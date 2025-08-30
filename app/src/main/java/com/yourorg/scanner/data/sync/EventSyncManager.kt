package com.yourorg.scanner.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.yourorg.scanner.model.EventAttendee
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventSyncManager @Inject constructor(
    private val context: Context
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    companion object {
        private const val TAG = "EventSyncManager" 
        private const val ATTENDEES_COLLECTION = "attendees"
        private const val OFFLINE_QUEUE_COLLECTION = "offline_queue"
        private const val SYNC_WORK_NAME = "event_sync_work"
    }
    
    private var currentEventListener: ListenerRegistration? = null
    private var isOnline = false
    
    init {
        setupNetworkMonitoring()
        setupPeriodicSync()
    }
    
    /**
     * Monitor network connectivity and trigger sync when back online
     */
    private fun setupNetworkMonitoring() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Network available - triggering sync")
                isOnline = true
                triggerOfflineSync()
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Network lost - going offline mode")
                isOnline = false
            }
        })
    }
    
    /**
     * Setup periodic background sync using WorkManager
     */
    private fun setupPeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
            
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    
    /**
     * Add attendee with duplicate prevention and offline support
     */
    suspend fun addAttendeeWithSync(eventId: String, attendee: EventAttendee): Boolean {
        return try {
            if (isOnline) {
                // Try to add directly to Firestore with duplicate check
                addAttendeeToFirestore(eventId, attendee)
            } else {
                // Queue for offline sync
                queueAttendeeForOfflineSync(eventId, attendee)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding attendee", e)
            // If online attempt fails, queue for retry
            queueAttendeeForOfflineSync(eventId, attendee)
            false
        }
    }
    
    /**
     * Add attendee directly to Firestore with duplicate prevention
     */
    private suspend fun addAttendeeToFirestore(eventId: String, attendee: EventAttendee): Boolean {
        return try {
            // Check for existing attendee with same student ID and event ID
            val existing = firestore.collection(ATTENDEES_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("studentId", attendee.studentId)
                .limit(1)
                .get()
                .await()
            
            if (existing.isEmpty) {
                // No duplicate found, add the attendee
                firestore.collection(ATTENDEES_COLLECTION)
                    .document(attendee.id)
                    .set(attendee)
                    .await()
                
                Log.d(TAG, "Attendee added to Firestore: ${attendee.studentId}")
                true
            } else {
                Log.w(TAG, "Duplicate attendee prevented: ${attendee.studentId} for event $eventId")
                // Still return true as this is expected behavior
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adding attendee to Firestore", e)
            throw e
        }
    }
    
    /**
     * Queue attendee for offline sync
     */
    private suspend fun queueAttendeeForOfflineSync(eventId: String, attendee: EventAttendee) {
        try {
            val queueItem = hashMapOf(
                "attendee" to attendee,
                "eventId" to eventId,
                "queuedAt" to System.currentTimeMillis(),
                "deviceId" to attendee.deviceId,
                "attempts" to 0
            )
            
            firestore.collection(OFFLINE_QUEUE_COLLECTION)
                .document("${attendee.deviceId}_${attendee.id}")
                .set(queueItem)
                .await()
                
            Log.d(TAG, "Attendee queued for offline sync: ${attendee.studentId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error queuing attendee for offline sync", e)
            // As a last resort, store in local database or SharedPreferences
            // This would require additional implementation
        }
    }
    
    /**
     * Process offline sync queue when coming back online
     */
    private fun triggerOfflineSync() {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
            
        WorkManager.getInstance(context).enqueue(syncRequest)
    }
    
    /**
     * Get real-time attendees for an event with duplicate prevention
     */
    fun getEventAttendeesLive(eventId: String): Flow<List<EventAttendee>> = callbackFlow {
        currentEventListener?.remove()
        
        currentEventListener = firestore.collection(ATTENDEES_COLLECTION)
            .whereEqualTo("eventId", eventId)
            .orderBy("scannedAt")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to attendees", error)
                    return@addSnapshotListener
                }
                
                val attendees = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(EventAttendee::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing attendee document ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                
                // Remove duplicates based on studentId (latest scan wins)
                val uniqueAttendees = attendees
                    .groupBy { it.studentId }
                    .mapValues { (_, duplicates) -> 
                        duplicates.maxByOrNull { it.scannedAt } 
                    }
                    .values
                    .filterNotNull()
                    .sortedBy { it.scannedAt }
                
                trySend(uniqueAttendees)
                Log.d(TAG, "Event attendees updated: ${uniqueAttendees.size} unique attendees")
            }
            
        awaitClose { 
            currentEventListener?.remove()
        }
    }
    
    /**
     * Process offline sync queue
     */
    suspend fun processOfflineQueue(): Boolean {
        return try {
            val queueSnapshot = firestore.collection(OFFLINE_QUEUE_COLLECTION)
                .get()
                .await()
                
            var successCount = 0
            var totalCount = queueSnapshot.size()
            
            for (doc in queueSnapshot.documents) {
                try {
                    val queueItem = doc.data ?: continue
                    val attendee = queueItem["attendee"] as? EventAttendee ?: continue
                    val eventId = queueItem["eventId"] as? String ?: continue
                    
                    // Try to add to Firestore with duplicate check
                    val success = addAttendeeToFirestore(eventId, attendee)
                    
                    if (success) {
                        // Remove from queue after successful sync
                        doc.reference.delete().await()
                        successCount++
                        Log.d(TAG, "Synced queued attendee: ${attendee.studentId}")
                    } else {
                        // Increment attempt counter
                        val attempts = (queueItem["attempts"] as? Long ?: 0) + 1
                        if (attempts > 3) {
                            // Remove after 3 failed attempts
                            doc.reference.delete().await()
                            Log.w(TAG, "Removed failed queue item after 3 attempts: ${attendee.studentId}")
                        } else {
                            doc.reference.update("attempts", attempts).await()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing queue item ${doc.id}", e)
                }
            }
            
            Log.d(TAG, "Offline sync completed: $successCount/$totalCount items synced")
            successCount == totalCount
        } catch (e: Exception) {
            Log.e(TAG, "Error processing offline queue", e)
            false
        }
    }
    
    /**
     * Clean up old duplicate entries (run periodically)
     */
    suspend fun cleanupDuplicates(eventId: String): Int {
        return try {
            val attendees = firestore.collection(ATTENDEES_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.toObject(EventAttendee::class.java)?.copy(id = doc.id)
                }
            
            // Group by studentId and find duplicates
            val duplicateGroups = attendees.groupBy { it.studentId }
                .filter { it.value.size > 1 }
            
            var deletedCount = 0
            
            for ((studentId, duplicates) in duplicateGroups) {
                // Keep the latest scan, delete the rest
                val latest = duplicates.maxByOrNull { it.scannedAt }
                val toDelete = duplicates.filter { it.id != latest?.id }
                
                for (duplicate in toDelete) {
                    firestore.collection(ATTENDEES_COLLECTION)
                        .document(duplicate.id)
                        .delete()
                        .await()
                    deletedCount++
                }
                
                Log.d(TAG, "Cleaned up ${toDelete.size} duplicates for student $studentId")
            }
            
            Log.d(TAG, "Duplicate cleanup completed: $deletedCount entries removed")
            deletedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up duplicates", e)
            0
        }
    }
    
    fun stopListening() {
        currentEventListener?.remove()
        currentEventListener = null
    }
}

/**
 * WorkManager worker for background synchronization
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            val syncManager = EventSyncManager(applicationContext)
            val success = syncManager.processOfflineQueue()
            
            if (success) {
                Log.d("SyncWorker", "Background sync completed successfully")
                Result.success()
            } else {
                Log.w("SyncWorker", "Background sync had failures, will retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("SyncWorker", "Background sync failed", e)
            Result.failure()
        }
    }
}