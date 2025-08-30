package com.yourorg.scanner.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.text.SimpleDateFormat
import java.util.*

@Parcelize
data class Event(
    val id: String,
    val eventNumber: Int, // For Python script compatibility (Event ID)
    val name: String,
    val description: String = "",
    val date: Long,
    val location: String = "",
    val isActive: Boolean = true,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val createdBy: String = "",
    val customColumns: List<EventColumn> = emptyList(),
    val staticValues: Map<String, String> = emptyMap(),
    val exportFormat: ExportFormat = ExportFormat.TEXT_DELIMITED // Default to text format
) : Parcelable {
    
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US).format(Date(date))
    
    val shortDate: String
        get() = SimpleDateFormat("MMM dd", Locale.US).format(Date(date))
        
    val status: EventStatus
        get() = when {
            isCompleted -> EventStatus.COMPLETED
            isActive -> EventStatus.ACTIVE
            else -> EventStatus.INACTIVE
        }
        
    // Generate filename for text export (matches Python script format)
    val exportFilename: String
        get() {
            val dateFormat = SimpleDateFormat("MMddyy", Locale.US)
            val dateString = dateFormat.format(Date(System.currentTimeMillis()))
            return "Event_${eventNumber}_${dateString}.txt"
        }
    
    companion object {
        fun createNew(
            eventNumber: Int,
            name: String,
            description: String = "",
            date: Long = System.currentTimeMillis(),
            location: String = ""
        ) = Event(
            id = UUID.randomUUID().toString(),
            eventNumber = eventNumber,
            name = name,
            description = description,
            date = date,
            location = location
        )
    }
}

@Parcelize
data class EventColumn(
    val id: String,
    val name: String,
    val displayName: String,
    val dataType: ColumnDataType,
    val maxLength: Int = 50,
    val isRequired: Boolean = false,
    val defaultValue: String = "",
    val order: Int = 0
) : Parcelable {
    
    companion object {
        // Standard columns that are always included
        fun standardColumns() = listOf(
            EventColumn(
                id = "student_id",
                name = "student_id", 
                displayName = "Student ID",
                dataType = ColumnDataType.TEXT,
                maxLength = 20,
                isRequired = true,
                order = 0
            ),
            EventColumn(
                id = "first_name",
                name = "first_name",
                displayName = "First Name", 
                dataType = ColumnDataType.TEXT,
                maxLength = 30,
                isRequired = true,
                order = 1
            ),
            EventColumn(
                id = "last_name", 
                name = "last_name",
                displayName = "Last Name",
                dataType = ColumnDataType.TEXT,
                maxLength = 30,
                isRequired = true,
                order = 2
            ),
            EventColumn(
                id = "email",
                name = "email",
                displayName = "Email",
                dataType = ColumnDataType.TEXT,
                maxLength = 50,
                isRequired = false,
                order = 3
            ),
            EventColumn(
                id = "scan_timestamp",
                name = "scan_timestamp",
                displayName = "Scan Time",
                dataType = ColumnDataType.DATETIME,
                maxLength = 19,
                isRequired = true,
                order = 4
            )
        )
    }
}

@Parcelize
data class EventAttendee(
    val id: String,
    val eventId: String,
    val studentId: String,
    val student: Student?,
    val scannedAt: Long,
    val deviceId: String,
    val customFieldValues: Map<String, String> = emptyMap(),
    val uniqueEventValue: String = "", // Unique value per attendee
    val verified: Boolean = false
) : Parcelable {
    
    val formattedScanTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(scannedAt))
        
    companion object {
        fun create(
            eventId: String,
            studentId: String,
            student: Student?,
            deviceId: String,
            customValues: Map<String, String> = emptyMap()
        ) = EventAttendee(
            id = UUID.randomUUID().toString(),
            eventId = eventId,
            studentId = studentId,
            student = student,
            scannedAt = System.currentTimeMillis(),
            deviceId = deviceId,
            customFieldValues = customValues,
            uniqueEventValue = generateUniqueValue(),
            verified = student != null
        )
        
        private fun generateUniqueValue(): String {
            // Generate a unique 8-character alphanumeric value
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..8).map { chars.random() }.joinToString("")
        }
    }
}

enum class ColumnDataType {
    TEXT,
    NUMBER, 
    DATETIME,
    BOOLEAN,
    CUSTOM
}

enum class ExportFormat {
    CSV,
    FIXED_WIDTH,
    XLSX,
    TEXT_DELIMITED // For Python script compatibility
}

enum class EventStatus {
    ACTIVE,
    INACTIVE,
    COMPLETED
}