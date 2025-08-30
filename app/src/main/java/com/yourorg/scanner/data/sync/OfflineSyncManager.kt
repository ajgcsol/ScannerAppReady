package com.yourorg.scanner.data.sync

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import com.yourorg.scanner.data.room.AppDb
import com.yourorg.scanner.data.room.StudentEntity
import com.yourorg.scanner.model.Student
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncManager @Inject constructor(
    private val context: Context,
    private val database: AppDb,
    private val connectivityMonitor: ConnectivityMonitor
) {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        private const val TAG = "OfflineSyncManager"
        private const val SYNC_WORK_NAME = "offline_sync_work"
        private const val STUDENT_SYNC_WORK_NAME = "student_sync_work"
        private const val SCAN_SYNC_WORK_NAME = "scan_sync_work"
        private const val STUDENTS_COLLECTION = "students"
        private const val SCANS_COLLECTION = "scans"
    }
    
    init {
        // Monitor connectivity changes and trigger sync when back online
        scope.launch {
            connectivityMonitor.wasOffline.collect { wasOffline ->
                if (wasOffline && connectivityMonitor.isConnected.value) {
                    Log.d(TAG, "Device back online, starting sync")
                    startSync()
                    connectivityMonitor.markSyncCompleted()
                }
            }
        }
    }
    
    /**
     * Start comprehensive sync when network is available
     */
    fun startSync() {
        if (!connectivityMonitor.isCurrentlyConnected()) {
            Log.d(TAG, "Not connected, skipping sync")
            return
        }
        
        Log.d(TAG, "Starting manual offline sync")
        
        // Use direct coroutine instead of WorkManager to avoid callback conflicts
        scope.launch {
            try {
                Log.d(TAG, "Manual sync started")
                
                // Sync students from Firebase
                val studentsSuccess = syncStudentsFromFirebase()
                
                // Sync scans to Firebase  
                val scansSuccess = syncScansToFirebase()
                
                if (studentsSuccess && scansSuccess) {
                    Log.d(TAG, "Manual sync completed successfully")
                } else {
                    Log.w(TAG, "Manual sync completed with some failures")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Manual sync failed", e)
            }
        }
    }
    
    /**
     * Sync students from Firebase to local database
     */
    suspend fun syncStudentsFromFirebase(): Boolean {
        return try {
            Log.d(TAG, "Starting student sync from Firebase")
            
            val snapshot = firestore.collection(STUDENTS_COLLECTION)
                .get()
                .await()
                
            val students = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                StudentEntity(
                    studentId = data["studentId"] as? String ?: doc.id,
                    firstName = data["firstName"] as? String ?: "",
                    lastName = data["lastName"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    program = data["program"] as? String ?: "",
                    year = data["year"] as? String ?: "",
                    active = data["active"] as? Boolean ?: true,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
            
            // Clear old data and insert fresh data
            database.studentDao().deleteAllStudents()
            database.studentDao().insertStudents(students)
            
            Log.d(TAG, "Successfully synced ${students.size} students from Firebase")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync students from Firebase", e)
            false
        }
    }
    
    /**
     * Sync unsynced scans to Firebase
     */
    suspend fun syncScansToFirebase(): Boolean {
        return try {
            Log.d(TAG, "Starting scan sync to Firebase")
            
            val unsyncedScans = database.scanDao().getUnsyncedScans()
            
            if (unsyncedScans.isEmpty()) {
                Log.d(TAG, "No unsynced scans to upload")
                return true
            }
            
            Log.d(TAG, "Syncing ${unsyncedScans.size} scans to Firebase")
            val syncedScanIds = mutableListOf<String>()
            
            for (scan in unsyncedScans) {
                try {
                    val scanData = hashMapOf<String, Any>(
                        "code" to scan.code,
                        "studentId" to scan.code,
                        "timestamp" to scan.timestamp,
                        "deviceId" to scan.deviceId,
                        "verified" to scan.verified,
                        "symbology" to (scan.symbology ?: "QR_CODE"),
                        "listId" to scan.listId,
                        "userId" to (scan.userId ?: "scanner-user"),
                        "synced" to true
                    )
                    
                    // Add student info if available
                    if (scan.firstName.isNotEmpty()) {
                        scanData["firstName"] = scan.firstName
                        scanData["lastName"] = scan.lastName
                        scanData["email"] = scan.email
                        scanData["program"] = scan.program
                        scanData["year"] = scan.year
                    }
                    
                    // Add event info if available
                    scan.eventId?.let { eventId ->
                        scanData["eventId"] = eventId
                    }
                    
                    firestore.collection(SCANS_COLLECTION)
                        .document(scan.id)
                        .set(scanData)
                        .await()
                        
                    syncedScanIds.add(scan.id)
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to sync scan ${scan.id}", e)
                }
            }
            
            // Mark successfully synced scans
            if (syncedScanIds.isNotEmpty()) {
                database.scanDao().markSynced(syncedScanIds)
                Log.d(TAG, "Marked ${syncedScanIds.size} scans as synced")
            }
            
            Log.d(TAG, "Scan sync completed: ${syncedScanIds.size}/${unsyncedScans.size} successful")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync scans to Firebase", e)
            false
        }
    }
    
    /**
     * Initial data sync (download students if local DB is empty)
     */
    suspend fun performInitialSyncIfNeeded(): Boolean {
        val studentCount = database.studentDao().getStudentCount()
        
        if (studentCount == 0 && connectivityMonitor.isCurrentlyConnected()) {
            Log.d(TAG, "No local students found, performing initial sync")
            return syncStudentsFromFirebase()
        }
        
        Log.d(TAG, "Local students available ($studentCount), skipping initial sync")
        return true
    }
    
    /**
     * Schedule periodic sync (disabled to avoid callback conflicts)
     * Sync is triggered automatically when network becomes available
     */
    fun schedulePeriodicSync() {
        Log.d(TAG, "Periodic sync disabled - using connectivity-based sync instead")
        // Sync is handled automatically by connectivity monitoring
    }
    
    /**
     * Get sync status info
     */
    suspend fun getSyncStatus(): SyncStatus {
        val unsyncedScanCount = database.scanDao().getUnsyncedScanCount()
        val lastSyncTime = database.studentDao().getLastSyncTime()
        val isConnected = connectivityMonitor.isCurrentlyConnected()
        
        return SyncStatus(
            isConnected = isConnected,
            unsyncedScanCount = unsyncedScanCount,
            lastSyncTime = lastSyncTime,
            needsSync = unsyncedScanCount > 0
        )
    }
}

data class SyncStatus(
    val isConnected: Boolean,
    val unsyncedScanCount: Int,
    val lastSyncTime: Long?,
    val needsSync: Boolean
)

/**
 * WorkManager worker for background offline sync
 */
class OfflineSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "OfflineSyncWorker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "SyncWorker started")
            
            val database = androidx.room.Room.databaseBuilder(
                applicationContext,
                AppDb::class.java,
                "app_database"
            ).build()
            val connectivityMonitor = ConnectivityMonitor(applicationContext)
            val syncManager = OfflineSyncManager(applicationContext, database, connectivityMonitor)
            
            // Sync students from Firebase
            val studentsSuccess = syncManager.syncStudentsFromFirebase()
            
            // Sync scans to Firebase  
            val scansSuccess = syncManager.syncScansToFirebase()
            
            if (studentsSuccess && scansSuccess) {
                Log.d(TAG, "SyncWorker completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "SyncWorker completed with some failures")
                Result.retry()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed", e)
            Result.failure()
        }
    }
}