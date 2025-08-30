package com.yourorg.scanner.export

import android.content.Context
import android.util.Log
import com.yourorg.scanner.model.Event
import com.yourorg.scanner.model.EventAttendee
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Text Delimited Export Manager - matches Python script format
 * Format: EventNumber StudentID(9chars) 1
 * Filename: Event_[eventNumber]_[MMDDYY].txt
 */
class TextDelimitedExportManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TextDelimitedExport"
        private const val STUDENT_ID_LENGTH = 9
        private const val ATTENDANCE_FLAG = "1"
    }
    
    /**
     * Export event attendees in text delimited format matching Python script
     */
    fun exportEventAttendees(
        event: Event,
        attendees: List<EventAttendee>
    ): File? {
        return try {
            Log.d(TAG, "Exporting ${attendees.size} attendees for event ${event.eventNumber}")
            
            // Filter and clean student IDs (match Python script logic)
            val validAttendees = attendees.filter { attendee ->
                val studentId = cleanStudentId(attendee.studentId)
                studentId.length == STUDENT_ID_LENGTH
            }
            
            Log.d(TAG, "Found ${validAttendees.size} valid attendees with 9-char student IDs")
            
            if (validAttendees.isEmpty()) {
                Log.w(TAG, "No valid attendees found for export")
                return null
            }
            
            // Create export file
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val fileName = event.exportFilename
            val exportFile = File(exportDir, fileName)
            
            // Write data in fixed-width format matching sample: "889 AB61247031 1"
            FileWriter(exportFile).use { writer ->
                val sortedAttendees = validAttendees
                    .distinctBy { it.studentId } // Remove duplicates
                    .sortedBy { it.studentId }   // Sort by student ID
                
                sortedAttendees.forEach { attendee ->
                    val cleanedId = cleanStudentId(attendee.studentId)
                    // Format: EventNumber(3 digits) + space + StudentID(9 chars) + space + "1"
                    val line = "${event.eventNumber.toString().padStart(3, '0')} $cleanedId $ATTENDANCE_FLAG\n"
                    writer.write(line)
                }
            }
            
            Log.d(TAG, "Export completed: ${exportFile.absolutePath}")
            exportFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting text delimited file", e)
            null
        }
    }
    
    /**
     * Export error file for invalid student IDs (like Python script)
     */
    fun exportErrorFile(
        event: Event,
        attendees: List<EventAttendee>
    ): File? {
        return try {
            // Find invalid student IDs (not 9 characters)
            val invalidAttendees = attendees.filter { attendee ->
                val studentId = cleanStudentId(attendee.studentId)
                studentId.length != STUDENT_ID_LENGTH
            }
            
            if (invalidAttendees.isEmpty()) {
                return null
            }
            
            Log.d(TAG, "Found ${invalidAttendees.size} invalid student IDs for error file")
            
            // Create error file
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            
            val dateFormat = SimpleDateFormat("MMddyy", Locale.US)
            val dateString = dateFormat.format(Date())
            val fileName = "Errors_${dateString}.txt"
            val errorFile = File(exportDir, fileName)
            
            FileWriter(errorFile).use { writer ->
                invalidAttendees.forEach { attendee ->
                    writer.write("${attendee.studentId}\n")
                }
            }
            
            Log.d(TAG, "Error file created: ${errorFile.absolutePath}")
            errorFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating error file", e)
            null
        }
    }
    
    /**
     * Clean student ID - remove spaces (matches Python script logic)
     */
    private fun cleanStudentId(studentId: String): String {
        return studentId.replace(" ", "")
    }
    
    /**
     * Validate student ID length
     */
    fun isValidStudentId(studentId: String): Boolean {
        return cleanStudentId(studentId).length == STUDENT_ID_LENGTH
    }
    
    /**
     * Get export summary for display
     */
    data class ExportSummary(
        val validCount: Int,
        val invalidCount: Int,
        val exportFile: File?,
        val errorFile: File?
    )
    
    fun getExportSummary(
        event: Event,
        attendees: List<EventAttendee>
    ): ExportSummary {
        val validAttendees = attendees.filter { isValidStudentId(it.studentId) }
        val invalidAttendees = attendees.filter { !isValidStudentId(it.studentId) }
        
        return ExportSummary(
            validCount = validAttendees.size,
            invalidCount = invalidAttendees.size,
            exportFile = if (validAttendees.isNotEmpty()) exportEventAttendees(event, attendees) else null,
            errorFile = if (invalidAttendees.isNotEmpty()) exportErrorFile(event, attendees) else null
        )
    }
}