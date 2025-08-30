package com.yourorg.scanner.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.yourorg.scanner.data.room.AppDb
import com.yourorg.scanner.data.room.StudentEntity
import com.yourorg.scanner.data.room.ScanEntity
import com.yourorg.scanner.data.sync.ConnectivityMonitor
import com.yourorg.scanner.data.sync.OfflineSyncManager
import com.yourorg.scanner.model.Student
import com.yourorg.scanner.model.ScanRecord
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class StudentRepository @Inject constructor(
    private val context: Context,
    private val database: AppDb,
    private val connectivityMonitor: ConnectivityMonitor,
    private val syncManager: OfflineSyncManager
) {
    
    private val firestore = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "StudentRepository"
        private const val STUDENTS_COLLECTION = "students"
        private const val SCANS_COLLECTION = "scans"
    }
    
    init {
        // Perform initial sync if needed
        CoroutineScope(Dispatchers.IO).launch {
            syncManager.performInitialSyncIfNeeded()
        }
    }
    
    /**
     * Look up student by student ID - uses local DB first, then Firebase as fallback
     */
    suspend fun findStudentById(studentId: String): Student? {
        return try {
            Log.d(TAG, "Looking up student with ID: $studentId")
            
            // Try local database first
            val localStudent = database.studentDao().getStudentById(studentId)
            if (localStudent != null) {
                Log.d(TAG, "Found student locally: ${localStudent.firstName} ${localStudent.lastName}")
                return localStudent.toStudent()
            }
            
            // If not found locally and we have connectivity, try Firebase in background
            if (connectivityMonitor.isCurrentlyConnected()) {
                Log.d(TAG, "Student not found locally, will try Firebase in background")
                
                // Launch Firebase lookup in background - don't block UI
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val document = firestore.collection(STUDENTS_COLLECTION)
                            .document(studentId)
                            .get()
                            .await()
                        
                        if (document.exists()) {
                            val data = document.data ?: return@launch
                            val student = Student(
                                studentId = data["studentId"] as? String ?: studentId,
                                firstName = data["firstName"] as? String ?: "",
                                lastName = data["lastName"] as? String ?: "",
                                email = data["email"] as? String ?: "",
                                program = data["program"] as? String ?: "",
                                year = data["year"] as? String ?: "",
                                active = data["active"] as? Boolean ?: true
                            )
                            
                            // Cache the student locally for future offline access
                            database.studentDao().insertStudent(StudentEntity.fromStudent(student))
                            
                            Log.d(TAG, "Found student on Firebase and cached locally: ${student.firstName} ${student.lastName}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Background Firebase lookup failed for $studentId", e)
                    }
                }
            }
            
            Log.d(TAG, "Student not found with ID: $studentId")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up student: $studentId", e)
            null
        }
    }
    
    /**
     * Record a scan event with verification status - stores locally first, syncs when online
     */
    suspend fun recordScan(studentId: String, student: Student?, deviceId: String, eventId: String? = null, listId: String = "default-list"): Boolean {
        return try {
            val scanId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            val isOnline = connectivityMonitor.isCurrentlyConnected()
            
            val localScan = ScanEntity(
                id = scanId,
                code = studentId,
                symbology = "QR_CODE",
                timestamp = timestamp,
                deviceId = deviceId,
                userId = "scanner-user",
                listId = listId,
                synced = false, // Will be synced later
                verified = student != null,
                firstName = student?.firstName ?: "",
                lastName = student?.lastName ?: "",
                email = student?.email ?: "",
                program = student?.program ?: "",
                year = student?.year ?: "",
                eventId = eventId
            )
            
            // Always save locally first
            database.scanDao().insertScan(localScan)
            
            // If online, sync in background (non-blocking)
            if (isOnline) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val scanRecord = hashMapOf(
                            "code" to studentId,
                            "studentId" to studentId,
                            "timestamp" to timestamp,
                            "deviceId" to deviceId,
                            "verified" to (student != null),
                            "symbology" to "QR_CODE",
                            "listId" to listId,
                            "userId" to "scanner-user",
                            "synced" to true
                        )
                        
                        // Add student info if found
                        student?.let {
                            scanRecord["firstName"] = it.firstName
                            scanRecord["lastName"] = it.lastName
                            scanRecord["email"] = it.email
                            scanRecord["program"] = it.program
                            scanRecord["year"] = it.year
                        }
                        
                        // Add event info if available
                        eventId?.let {
                            scanRecord["eventId"] = it
                        }
                        
                        firestore.collection(SCANS_COLLECTION)
                            .document(scanId)
                            .set(scanRecord)
                            .await()
                        
                        // Mark as synced in local DB
                        database.scanDao().markScanAsSynced(scanId)
                        
                        Log.d(TAG, "Scan synced to Firebase for student ID: $studentId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to sync scan to Firebase, will sync later: $studentId", e)
                    }
                }
            }
            
            Log.d(TAG, "Scan recorded locally for student ID: $studentId")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error recording scan for student: $studentId", e)
            false
        }
    }
    
    /**
     * Get all students - uses local DB, falls back to Firebase if empty
     */
    fun getAllStudents(): Flow<List<Student>> {
        return flow {
            val entities = database.studentDao().getAllStudents()
            if (entities.isEmpty() && connectivityMonitor.isCurrentlyConnected()) {
                // Try to sync from Firebase if local DB is empty
                try {
                    syncManager.syncStudentsFromFirebase()
                    val refreshedEntities = database.studentDao().getAllStudents()
                    emit(refreshedEntities.map { it.toStudent() })
                } catch (e: Exception) {
                    Log.e(TAG, "Error syncing students from Firebase", e)
                    emit(emptyList())
                }
            } else {
                emit(entities.map { it.toStudent() })
            }
        }
    }
    
    /**
     * Get all students from local database (for performance)
     */
    private suspend fun getAllStudentsFromLocal(): List<Student> {
        return database.studentDao().getAllStudents().map { it.toStudent() }
    }
    
    /**
     * Search students by name or ID - uses local database with optimized Room query
     */
    suspend fun searchStudents(query: String): List<Student> {
        return try {
            Log.d(TAG, "Searching for students with query: '$query'")
            
            if (query.length < 2) {
                return emptyList()
            }
            
            // Use Room's optimized search query
            val matchedStudents = database.studentDao().searchStudents(query.trim())
            
            if (matchedStudents.isEmpty()) {
                // If no local results and we're online, try to sync and search again
                if (connectivityMonitor.isCurrentlyConnected()) {
                    Log.d(TAG, "No local results, attempting sync from Firebase")
                    syncManager.syncStudentsFromFirebase()
                    // Search again after sync
                    return database.studentDao().searchStudents(query.trim()).map { it.toStudent() }
                }
                Log.w(TAG, "No students found locally and device is offline")
                return emptyList()
            }
            
            Log.d(TAG, "Found ${matchedStudents.size} matching students for query '$query'")
            matchedStudents.map { it.toStudent() }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching students: ${e.message}", e)
            emptyList()
        }
    }
    
    /**
     * Get local scans for the current session
     */
    suspend fun getLocalScans(): List<ScanRecord> {
        return database.scanDao().getAllScans().map { it.toScanRecord() }
    }
    
    /**
     * Get local scans for a specific event
     */
    suspend fun getLocalScansForEvent(eventId: String): List<ScanRecord> {
        return database.scanDao().getScansForEvent(eventId).map { it.toScanRecord() }
    }
    
    /**
     * Force sync with Firebase
     */
    suspend fun forceSync() {
        if (connectivityMonitor.isCurrentlyConnected()) {
            Log.d(TAG, "Forcing sync with Firebase")
            syncManager.syncStudentsFromFirebase()
            syncManager.syncScansToFirebase()
        } else {
            Log.w(TAG, "Cannot sync - device is offline")
        }
    }
    
    /**
     * Get sync status
     */
    suspend fun getSyncStatus() = syncManager.getSyncStatus()
    
    /**
     * Update analytics after successful scan
     */
    suspend fun updateScanAnalytics(verified: Boolean) {
        try {
            val today = com.google.firebase.Timestamp.now()
            val analyticsRef = firestore.collection("analytics").document("daily")
            
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(analyticsRef)
                val currentData = snapshot.data ?: hashMapOf()
                
                val totalScans = (currentData["totalScans"] as? Long ?: 0) + 1
                val verifiedScans = if (verified) {
                    (currentData["verifiedScans"] as? Long ?: 0) + 1
                } else {
                    currentData["verifiedScans"] as? Long ?: 0
                }
                
                val updatedData = hashMapOf(
                    "totalScans" to totalScans,
                    "verifiedScans" to verifiedScans,
                    "lastUpdated" to today
                )
                
                transaction.set(analyticsRef, updatedData, com.google.firebase.firestore.SetOptions.merge())
            }.await()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating scan analytics", e)
        }
    }
}