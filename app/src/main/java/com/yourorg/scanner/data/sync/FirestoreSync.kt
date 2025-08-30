package com.yourorg.scanner.data.sync

import com.yourorg.scanner.model.ScanRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import android.util.Log

// Firebase imports
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase sync handler with Firestore integration
 */
class FirestoreSync {
    
    companion object {
        private const val TAG = "FirestoreSync"
        private var isFirebaseEnabled = false
    }
    
    // Firebase instance
    private val db: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance().also { 
            isFirebaseEnabled = true
            Log.d(TAG, "Firebase initialized successfully")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Firebase not configured, running in offline mode: ${e.message}")
        null
    }
    
    /**
     * Listen to scan updates from Firestore
     * Returns empty flow when Firebase is not configured
     */
    fun listenScans(listId: String): Flow<List<ScanRecord>> {
        if (db == null) {
            Log.d(TAG, "listenScans: Firebase not available, returning empty flow")
            return flowOf(emptyList())
        }
        
        return callbackFlow<List<ScanRecord>> {
            val query: Query = db.collection("lists").document(listId)
                .collection("scans").orderBy("timestamp", Query.Direction.DESCENDING)
            val reg: ListenerRegistration = query.addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Error listening to scans", err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val items = snap.documents.mapNotNull { it.toObject(ScanRecord::class.java) }
                trySend(items)
            }
            awaitClose { reg.remove() }
        }
    }

    /**
     * Add a scan record to Firestore
     * No-op when Firebase is not configured
     */
    suspend fun addScan(listId: String, scan: ScanRecord) {
        if (db == null) {
            Log.d(TAG, "addScan: Firebase not available")
            return
        }
        
        try {
            db.collection("lists").document(listId)
                .collection("scans").document(scan.id).set(scan).await()
            Log.d(TAG, "Scan added successfully: ${scan.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding scan", e)
        }
    }

    /**
     * Upload multiple scan records as a batch
     * No-op when Firebase is not configured
     */
    suspend fun uploadBatch(listId: String, scans: List<ScanRecord>) {
        if (db == null) {
            Log.d(TAG, "uploadBatch: Firebase not available")
            return
        }
        
        try {
            val batch = db.batch()
            scans.forEach { s ->
                val ref = db.collection("lists").document(listId)
                    .collection("scans").document(s.id)
                batch.set(ref, s)
            }
            batch.commit().await()
            Log.d(TAG, "Batch uploaded successfully: ${scans.size} scans")
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading batch", e)
        }
    }
    
    /**
     * Check if Firebase is configured and available
     */
    fun isFirebaseAvailable(): Boolean {
        return isFirebaseEnabled
    }
    
    /**
     * Save an error record for unrecognized student IDs
     */
    suspend fun saveErrorRecord(
        scannedId: String,
        email: String,
        eventId: String,
        eventName: String,
        eventDate: String
    ) {
        if (db == null) {
            Log.w(TAG, "Firebase not available, cannot save error record")
            return
        }
        
        try {
            val errorRecord = hashMapOf(
                "scannedId" to scannedId,
                "studentEmail" to email,
                "eventId" to eventId,
                "eventName" to eventName,
                "eventDate" to eventDate,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "resolved" to false
            )
            
            db.collection("error_records")
                .add(errorRecord)
                .await()
            
            Log.d(TAG, "Error record saved for $scannedId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save error record", e)
            throw e
        }
    }
    
    /**
     * Notify admin portal that an event has been completed
     */
    suspend fun notifyEventCompleted(
        eventId: String,
        eventName: String,
        eventNumber: Int,
        completedAt: Long,
        totalScans: Int
    ) {
        if (db == null) {
            Log.w(TAG, "Firebase not available, cannot notify event completion")
            return
        }
        
        try {
            val notification = hashMapOf(
                "type" to "EVENT_COMPLETED",
                "eventId" to eventId,
                "eventName" to eventName,
                "eventNumber" to eventNumber,
                "completedAt" to com.google.firebase.Timestamp(completedAt / 1000, 0),
                "totalScans" to totalScans,
                "timestamp" to com.google.firebase.Timestamp.now(),
                "read" to false,
                "exported" to false
            )
            
            // Add to notifications collection for admin portal
            db.collection("notifications")
                .add(notification)
                .await()
            
            // Also update the event status in the events collection
            db.collection("events")
                .document(eventId)
                .update(
                    "status", "COMPLETED",
                    "completedAt", com.google.firebase.Timestamp(completedAt / 1000, 0),
                    "isActive", false,
                    "isCompleted", true
                )
                .await()
            
            Log.d(TAG, "Event completion notified: $eventName with $totalScans scans")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify event completion", e)
            throw e
        }
    }
}
