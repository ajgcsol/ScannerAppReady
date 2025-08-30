package com.yourorg.scanner.export

import android.content.Context
import java.io.File

object ExportDelimited {
    fun write(context: Context, delimiter: String = ",", header: List<String>? = null, rows: List<List<String>>): File {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val ext = when (delimiter) { "	" -> "tsv"; "|" -> "psv"; else -> "csv" }
        val file = File(dir, "scan_report_${System.currentTimeMillis()}.$ext")
        file.bufferedWriter().use { out ->
            header?.let { out.append(it.joinToString(delimiter)).appendLine() }
            rows.forEach { r -> out.append(r.joinToString(delimiter)).appendLine() }
        }
        return file
    }
}
