package com.yourorg.scanner.export

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.yourorg.scanner.model.ScanRecord
import com.yourorg.scanner.model.Event
import com.yourorg.scanner.model.EventAttendee
import com.yourorg.scanner.data.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ExportManager handles exporting scan data to various formats
 * and sharing via email or other apps
 */
class ExportManager(private val context: Context) {

    companion object {
        private const val TAG = "ExportManager"
        private const val AUTHORITY = "com.yourorg.scanner.fileprovider"
    }

    private val exportsDir: File by lazy {
        File(context.getExternalFilesDir(null), "exports").apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val textDelimitedExportManager by lazy {
        TextDelimitedExportManager(context)
    }

    /**
     * Export scan records to CSV format
     */
    suspend fun exportToCsv(scans: List<ScanRecord>, listId: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "scans_${listId}_${timestamp}.csv"
                val file = File(exportsDir, fileName)
                
                FileWriter(file).use { writer ->
                    // Write CSV header
                    writer.append("ID,Code,Symbology,Timestamp,FormattedTime,DeviceID,UserID,ListID\n")
                    
                    // Write data rows
                    scans.forEach { scan ->
                        val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(Date(scan.timestamp))
                        
                        writer.append("\"${scan.id}\",")
                        writer.append("\"${escapeQuotes(scan.code)}\",")
                        writer.append("\"${scan.symbology ?: ""}\",")
                        writer.append("${scan.timestamp},")
                        writer.append("\"$formattedTime\",")
                        writer.append("\"${scan.deviceId}\",")
                        writer.append("\"${scan.userId ?: ""}\",")
                        writer.append("\"${scan.listId}\"\n")
                    }
                }
                
                Log.d(TAG, "CSV export successful: ${file.absolutePath}")
                file
            } catch (e: Exception) {
                Log.e(TAG, "CSV export failed", e)
                null
            }
        }
    }

    /**
     * Export scan records to XLSX format using Apache POI
     */
    suspend fun exportToXlsx(scans: List<ScanRecord>, listId: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                // TODO: Implement XLSX export when POI is properly configured
                // For now, fall back to CSV
                Log.w(TAG, "XLSX export not yet implemented, falling back to CSV")
                exportToCsv(scans, listId)
                
                /*
                // Apache POI XLSX implementation (uncomment when dependencies are resolved)
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Scan Records")
                
                // Create header row
                val headerRow = sheet.createRow(0)
                val headers = arrayOf("ID", "Code", "Symbology", "Timestamp", "Formatted Time", "Device ID", "User ID", "List ID")
                headers.forEachIndexed { index, header ->
                    headerRow.createCell(index).setCellValue(header)
                }
                
                // Create data rows
                scans.forEachIndexed { rowIndex, scan ->
                    val row = sheet.createRow(rowIndex + 1)
                    val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(Date(scan.timestamp))
                    
                    row.createCell(0).setCellValue(scan.id)
                    row.createCell(1).setCellValue(scan.code)
                    row.createCell(2).setCellValue(scan.symbology ?: "")
                    row.createCell(3).setCellValue(scan.timestamp.toDouble())
                    row.createCell(4).setCellValue(formattedTime)
                    row.createCell(5).setCellValue(scan.deviceId)
                    row.createCell(6).setCellValue(scan.userId ?: "")
                    row.createCell(7).setCellValue(scan.listId)
                }
                
                // Auto-size columns
                for (i in 0 until headers.size) {
                    sheet.autoSizeColumn(i)
                }
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "scans_${listId}_${timestamp}.xlsx"
                val file = File(exportsDir, fileName)
                
                FileOutputStream(file).use { outputStream ->
                    workbook.write(outputStream)
                }
                workbook.close()
                
                Log.d(TAG, "XLSX export successful: ${file.absolutePath}")
                file
                */
                
            } catch (e: Exception) {
                Log.e(TAG, "XLSX export failed", e)
                null
            }
        }
    }

    /**
     * Share export file via email or other apps
     */
    suspend fun shareFile(file: File, subject: String, body: String) {
        withContext(Dispatchers.Main) {
            try {
                val uri = FileProvider.getUriForFile(context, AUTHORITY, file)
                
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = when (file.extension.lowercase()) {
                        "csv" -> "text/csv"
                        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        else -> "application/octet-stream"
                    }
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(shareIntent, "Share Scan Report")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                
                Log.d(TAG, "Share intent launched for file: ${file.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share file", e)
            }
        }
    }

    /**
     * Export event attendees to text delimited format (matches Python script)
     */
    suspend fun exportEventToTextDelimited(event: Event, attendees: List<EventAttendee>): File? {
        return withContext(Dispatchers.IO) {
            textDelimitedExportManager.exportEventAttendees(event, attendees)
        }
    }

    /**
     * Export and email report
     */
    suspend fun emailReport(scans: List<ScanRecord>, listId: String, format: ExportFormat = ExportFormat.CSV) {
        val file = when (format) {
            ExportFormat.CSV -> exportToCsv(scans, listId)
            ExportFormat.XLSX -> exportToXlsx(scans, listId)
            ExportFormat.TEXT_DELIMITED -> {
                // For text delimited, we need event context
                Log.w(TAG, "Text delimited export requires event context, use emailEventReport instead")
                null
            }
        }
        
        file?.let { reportFile ->
            val timestamp = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US).format(Date())
            val subject = "Scan Report - $listId"
            val body = buildString {
                appendLine("Scan Report Generated: $timestamp")
                appendLine("List ID: $listId")
                appendLine("Total Scans: ${scans.size}")
                appendLine()
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Generated by Scanner Pro")
                appendLine()
                if (scans.isNotEmpty()) {
                    appendLine("Latest Scans:")
                    scans.take(5).forEach { scan ->
                        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(scan.timestamp))
                        appendLine("• $time - ${scan.code} (${scan.symbology})")
                    }
                    if (scans.size > 5) {
                        appendLine("... and ${scans.size - 5} more (see attachment)")
                    }
                }
            }
            
            shareFile(reportFile, subject, body)
        }
    }

    /**
     * Export and email event attendees in text delimited format
     */
    suspend fun emailEventReport(event: Event, attendees: List<EventAttendee>) {
        val exportFile = exportEventToTextDelimited(event, attendees)
        val errorFile = textDelimitedExportManager.exportErrorFile(event, attendees)
        
        exportFile?.let { textFile ->
            val timestamp = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.US).format(Date())
            val subject = "Event Attendee Export - ${event.name}"
            val summary = textDelimitedExportManager.getExportSummary(event, attendees)
            
            val body = buildString {
                appendLine("Event Attendee Report")
                appendLine("Generated: $timestamp")
                appendLine()
                appendLine("Event: #${event.eventNumber} - ${event.name}")
                if (event.description.isNotEmpty()) {
                    appendLine("Description: ${event.description}")
                }
                appendLine("Date: ${SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(event.date))}")
                appendLine()
                appendLine("Export Summary:")
                appendLine("• Valid attendees: ${summary.validCount}")
                appendLine("• Invalid student IDs: ${summary.invalidCount}")
                appendLine("• Total scanned: ${attendees.size}")
                appendLine()
                appendLine("File: ${textFile.name}")
                appendLine("Format: TEXT_DELIMITED (matches Python script)")
                appendLine()
                if (summary.invalidCount > 0) {
                    appendLine("Note: ${summary.invalidCount} invalid student IDs found.")
                    appendLine("Error file will be attached separately if applicable.")
                }
                appendLine("Generated by Scanner Pro")
            }
            
            shareFile(textFile, subject, body)
            
            // Also share error file if it exists
            errorFile?.let { errFile ->
                shareFile(errFile, "Error Report - ${event.name}", 
                    "Student IDs that could not be processed (not 9 characters)")
            }
        }
    }

    /**
     * Get list of recent export files
     */
    fun getRecentExports(): List<File> {
        return try {
            exportsDir.listFiles()
                ?.filter { it.isFile && (it.extension == "csv" || it.extension == "xlsx") }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list recent exports", e)
            emptyList()
        }
    }

    /**
     * Clean up old export files (keep last 10)
     */
    suspend fun cleanupOldExports(): Unit {
        withContext(Dispatchers.IO) {
            try {
                val files = getRecentExports()
                if (files.size > 10) {
                    files.drop(10).forEach { file ->
                        if (file.delete()) {
                            Log.d(TAG, "Deleted old export: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cleanup old exports", e)
            }
            Unit
        }
    }

    private fun escapeQuotes(text: String): String {
        return text.replace("\"", "\"\"")
    }

    enum class ExportFormat {
        CSV, XLSX, TEXT_DELIMITED
    }
}

/**
 * Extension functions for easier usage
 */
suspend fun ExportManager.exportAndShareCsv(scans: List<ScanRecord>, listId: String) {
    val file = exportToCsv(scans, listId)
    file?.let { csvFile ->
        shareFile(
            file = csvFile,
            subject = "Scan Data Export - CSV",
            body = "CSV export with ${scans.size} scan records attached."
        )
    }
}

suspend fun ExportManager.exportAndShareXlsx(scans: List<ScanRecord>, listId: String) {
    val file = exportToXlsx(scans, listId)
    file?.let { xlsxFile ->
        shareFile(
            file = xlsxFile,
            subject = "Scan Data Export - Excel",
            body = "Excel export with ${scans.size} scan records attached."
        )
    }
}