package com.yourorg.scanner.export

import android.content.Context
import android.util.Log
import com.yourorg.scanner.data.EventExportData
import com.yourorg.scanner.model.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages fixed-width text file exports for events
 */
class FixedWidthExportManager(private val context: Context) {
    
    companion object {
        private const val TAG = "FixedWidthExportManager"
    }
    
    private val exportsDir: File by lazy {
        File(context.getExternalFilesDir(null), "exports").apply {
            if (!exists()) mkdirs()
        }
    }
    
    /**
     * Export event data as fixed-width text file
     */
    suspend fun exportEventAsFixedWidth(exportData: EventExportData): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "event_${exportData.event.name.replace(" ", "_")}_${timestamp}.txt"
            val file = File(exportsDir, fileName)
            
            FileWriter(file).use { writer ->
                writeFixedWidthData(writer, exportData)
            }
            
            Log.d(TAG, "Fixed-width export successful: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Fixed-width export failed", e)
            null
        }
    }
    
    private fun writeFixedWidthData(writer: FileWriter, exportData: EventExportData) {
        val event = exportData.event
        val attendees = exportData.attendees
        
        // Combine standard columns with custom columns
        val allColumns = EventColumn.standardColumns() + event.customColumns
        val sortedColumns = allColumns.sortedBy { it.order }
        
        // Write header (optional - can be disabled)
        writeFixedWidthHeader(writer, sortedColumns)
        
        // Write data rows
        attendees.forEach { attendee ->
            val row = buildFixedWidthRow(attendee, sortedColumns, event.staticValues)
            writer.write(row + "\n")
        }
    }
    
    private fun writeFixedWidthHeader(writer: FileWriter, columns: List<EventColumn>) {
        val headerRow = columns.map { column ->
            truncateAndPad(column.displayName, column.maxLength)
        }.joinToString("")
        
        writer.write(headerRow + "\n")
        
        // Write separator line
        val separatorRow = columns.map { column ->
            "-".repeat(column.maxLength)
        }.joinToString("")
        writer.write(separatorRow + "\n")
    }
    
    private fun buildFixedWidthRow(
        attendee: EventAttendee, 
        columns: List<EventColumn>,
        staticValues: Map<String, String>
    ): String {
        return columns.map { column ->
            val value = getColumnValue(attendee, column, staticValues)
            formatColumnValue(value, column)
        }.joinToString("")
    }
    
    private fun getColumnValue(
        attendee: EventAttendee, 
        column: EventColumn,
        staticValues: Map<String, String>
    ): String {
        // Check if this is a static value
        staticValues[column.name]?.let { return it }
        
        // Check custom field values
        attendee.customFieldValues[column.name]?.let { return it }
        
        // Handle standard columns
        return when (column.name) {
            "student_id" -> attendee.studentId
            "first_name" -> attendee.student?.firstName ?: ""
            "last_name" -> attendee.student?.lastName ?: ""
            "email" -> attendee.student?.email ?: ""
            "scan_timestamp" -> attendee.formattedScanTime
            "unique_value" -> attendee.uniqueEventValue
            "program" -> attendee.student?.program ?: ""
            "year" -> attendee.student?.year ?: ""
            "device_id" -> attendee.deviceId
            "verified" -> if (attendee.verified) "Y" else "N"
            else -> column.defaultValue
        }
    }
    
    private fun formatColumnValue(value: String, column: EventColumn): String {
        val formattedValue = when (column.dataType) {
            ColumnDataType.TEXT -> value
            ColumnDataType.NUMBER -> {
                val numericValue = value.toDoubleOrNull() ?: 0.0
                if (numericValue % 1.0 == 0.0) {
                    numericValue.toInt().toString()
                } else {
                    String.format("%.2f", numericValue)
                }
            }
            ColumnDataType.DATETIME -> {
                // Ensure consistent datetime format
                try {
                    val timestamp = value.toLongOrNull()
                    if (timestamp != null) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
                    } else {
                        value
                    }
                } catch (e: Exception) {
                    value
                }
            }
            ColumnDataType.BOOLEAN -> {
                when (value.lowercase()) {
                    "true", "1", "yes", "y" -> "Y"
                    else -> "N"
                }
            }
            ColumnDataType.CUSTOM -> value
        }
        
        return truncateAndPad(formattedValue, column.maxLength)
    }
    
    private fun truncateAndPad(value: String, width: Int): String {
        return when {
            value.length > width -> value.substring(0, width)
            value.length < width -> value.padEnd(width)
            else -> value
        }
    }
    
    /**
     * Export event data as CSV with custom columns
     */
    suspend fun exportEventAsCSV(exportData: EventExportData): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "event_${exportData.event.name.replace(" ", "_")}_${timestamp}.csv"
            val file = File(exportsDir, fileName)
            
            FileWriter(file).use { writer ->
                writeCSVData(writer, exportData)
            }
            
            Log.d(TAG, "CSV export successful: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "CSV export failed", e)
            null
        }
    }
    
    private fun writeCSVData(writer: FileWriter, exportData: EventExportData) {
        val event = exportData.event
        val attendees = exportData.attendees
        
        // Combine standard columns with custom columns
        val allColumns = EventColumn.standardColumns() + event.customColumns
        val sortedColumns = allColumns.sortedBy { it.order }
        
        // Write CSV header
        val headerRow = sortedColumns.map { it.displayName }.joinToString(",")
        writer.write(headerRow + "\n")
        
        // Write data rows
        attendees.forEach { attendee ->
            val row = sortedColumns.map { column ->
                val value = getColumnValue(attendee, column, event.staticValues)
                "\"${escapeQuotes(value)}\""
            }.joinToString(",")
            writer.write(row + "\n")
        }
    }
    
    private fun escapeQuotes(text: String): String {
        return text.replace("\"", "\"\"")
    }
    
    /**
     * Generate export preview
     */
    fun generateExportPreview(exportData: EventExportData, maxRows: Int = 5): ExportPreview {
        val event = exportData.event
        val attendees = exportData.attendees.take(maxRows)
        
        val allColumns = EventColumn.standardColumns() + event.customColumns
        val sortedColumns = allColumns.sortedBy { it.order }
        
        val headers = sortedColumns.map { it.displayName }
        val rows = attendees.map { attendee ->
            sortedColumns.map { column ->
                getColumnValue(attendee, column, event.staticValues)
            }
        }
        
        return ExportPreview(
            headers = headers,
            sampleRows = rows,
            totalRows = exportData.attendees.size,
            columns = sortedColumns
        )
    }
}

data class ExportPreview(
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val totalRows: Int,
    val columns: List<EventColumn>
)