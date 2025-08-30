package com.yourorg.scanner.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.yourorg.scanner.model.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.yourorg.scanner.data.sync.EventSyncManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor() {
    
    private val firestore = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "EventRepository"
        private const val EVENTS_COLLECTION = "events"
        private const val ATTENDEES_COLLECTION = "attendees"
    }
    
    /**
     * Create a new event
     */
    suspend fun createEvent(event: Event): Boolean {
        return try {
            firestore.collection(EVENTS_COLLECTION)
                .document(event.id)
                .set(event)
                .await()
            
            Log.d(TAG, "Event created: ${event.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event", e)
            false
        }
    }
    
    /**
     * Get all events (active and inactive)
     */
    fun getAllEvents(): Flow<List<Event>> = callbackFlow {
        val listener = firestore.collection(EVENTS_COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to events", error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val data = doc.data
                        Log.d(TAG, "Event document ${doc.id}: $data")
                        
                        // Map the document data to Event object
                        val event = Event(
                            id = doc.id,
                            eventNumber = (data?.get("eventNumber") as? Long)?.toInt() ?: 0,
                            name = data?.get("name") as? String ?: "",
                            description = data?.get("description") as? String ?: "",
                            date = (data?.get("date") as? com.google.firebase.Timestamp)?.toDate()?.time 
                                ?: System.currentTimeMillis(),
                            location = data?.get("location") as? String ?: "",
                            isActive = data?.get("isActive") as? Boolean ?: true,
                            isCompleted = data?.get("isCompleted") as? Boolean ?: false,
                            completedAt = (data?.get("completedAt") as? com.google.firebase.Timestamp)?.toDate()?.time,
                            createdAt = (data?.get("createdAt") as? com.google.firebase.Timestamp)?.toDate()?.time 
                                ?: System.currentTimeMillis()
                        )
                        event
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing event document ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                
                Log.d(TAG, "Loaded ${events.size} events from Firestore")
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get all active events
     */
    fun getActiveEvents(): Flow<List<Event>> = callbackFlow {
        val listener = firestore.collection(EVENTS_COLLECTION)
            .whereEqualTo("active", true)
            .orderBy("date")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to events", error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing event document ${doc.id}", e)
                        null
                    }
                } ?: emptyList()
                
                trySend(events)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get a specific event by ID
     */
    suspend fun getEvent(eventId: String): Event? {
        return try {
            val doc = firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .get()
                .await()
            
            doc.toObject(Event::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting event $eventId", e)
            null
        }
    }
    
    /**
     * Update an existing event
     */
    suspend fun updateEvent(event: Event): Boolean {
        return try {
            firestore.collection(EVENTS_COLLECTION)
                .document(event.id)
                .set(event)
                .await()
            
            Log.d(TAG, "Event updated: ${event.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating event", e)
            false
        }
    }
    
    /**
     * Record an attendee scan for an event with sync and duplicate prevention
     */
    suspend fun recordAttendee(
        eventId: String,
        studentId: String,
        student: Student?,
        deviceId: String,
        customValues: Map<String, String> = emptyMap()
    ): EventAttendee? {
        return try {
            val attendee = EventAttendee.create(
                eventId = eventId,
                studentId = studentId,
                student = student,
                deviceId = deviceId,
                customValues = customValues
            )
            
            // Save attendee directly to Firebase
            firestore.collection(ATTENDEES_COLLECTION)
                .document(attendee.id)
                .set(attendee)
                .await()
            
            Log.d(TAG, "Attendee recorded: ${student?.fullName ?: studentId} for event $eventId")
            attendee
        } catch (e: Exception) {
            Log.e(TAG, "Error recording attendee", e)
            null
        }
    }
    
    /**
     * Get attendee record for a specific student and event
     */
    private suspend fun getEventAttendee(eventId: String, studentId: String): EventAttendee? {
        return try {
            val snapshot = firestore.collection(ATTENDEES_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("studentId", studentId)
                .limit(1)
                .get()
                .await()
            
            snapshot.documents.firstOrNull()?.toObject(EventAttendee::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing attendee", e)
            null
        }
    }
    
    /**
     * Get all attendees for an event (real-time)
     */
    fun getEventAttendees(eventId: String): Flow<List<EventAttendee>> = callbackFlow {
        val listener = firestore.collection(ATTENDEES_COLLECTION)
            .whereEqualTo("eventId", eventId)
            .addSnapshotListener { snapshot, error ->
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
                
                trySend(attendees)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Update event with custom columns
     */
    suspend fun updateEventColumns(
        eventId: String, 
        customColumns: List<EventColumn>,
        staticValues: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            val updates = mapOf(
                "customColumns" to customColumns,
                "staticValues" to staticValues
            )
            
            firestore.collection(EVENTS_COLLECTION)
                .document(eventId)
                .update(updates)
                .await()
            
            Log.d(TAG, "Event columns updated for event $eventId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating event columns", e)
            false
        }
    }
    
    /**
     * Generate export data for an event with custom formatting
     */
    suspend fun generateEventExportData(
        eventId: String,
        format: ExportFormat = ExportFormat.CSV
    ): EventExportData? {
        return try {
            val event = getEvent(eventId) ?: return null
            val attendeesSnapshot = firestore.collection(ATTENDEES_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
            
            val attendees = attendeesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(EventAttendee::class.java)?.copy(id = doc.id)
            }
            
            EventExportData(
                event = event,
                attendees = attendees,
                format = format
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating export data", e)
            null
        }
    }
    
    /**
     * Get event statistics
     */
    suspend fun getEventStats(eventId: String): EventStats {
        return try {
            val attendeesSnapshot = firestore.collection(ATTENDEES_COLLECTION)
                .whereEqualTo("eventId", eventId)
                .get()
                .await()
            
            val attendees = attendeesSnapshot.documents.mapNotNull { doc ->
                doc.toObject(EventAttendee::class.java)
            }
            
            EventStats(
                totalAttendees = attendees.size,
                verifiedAttendees = attendees.count { it.verified },
                unverifiedAttendees = attendees.count { !it.verified },
                uniqueDevices = attendees.map { it.deviceId }.distinct().size
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting event stats", e)
            EventStats()
        }
    }
}

data class EventExportData(
    val event: Event,
    val attendees: List<EventAttendee>,
    val format: ExportFormat
)

data class EventStats(
    val totalAttendees: Int = 0,
    val verifiedAttendees: Int = 0,
    val unverifiedAttendees: Int = 0,
    val uniqueDevices: Int = 0
) {
    val verificationRate: Float
        get() = if (totalAttendees > 0) verifiedAttendees.toFloat() / totalAttendees else 0f
}