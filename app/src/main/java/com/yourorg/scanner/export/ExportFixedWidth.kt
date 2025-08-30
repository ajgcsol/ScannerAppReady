package com.yourorg.scanner.export

import android.content.Context
import java.io.File
import kotlin.math.max

data class ColumnSpec(val width: Int, val align: Align = Align.LEFT, val padChar: Char = ' ')
enum class Align { LEFT, RIGHT }

object ExportFixedWidth {
    fun write(context: Context, specs: List<ColumnSpec>, header: List<String>? = null, rows: List<List<String>>): File {
        require(rows.all { it.size == specs.size })
        header?.let { require(it.size == specs.size) }
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(dir, "scan_report_${System.currentTimeMillis()}.txt")
        fun fmt(cols: List<String>): String = buildString {
            for (i in cols.indices) {
                val spec = specs[i]
                val raw = cols[i]
                val truncated = if (raw.length > spec.width) raw.substring(0, spec.width) else raw
                val padCount = max(0, spec.width - truncated.length)
                val pad = spec.padChar.toString().repeat(padCount)
                append(if (spec.align == Align.LEFT) truncated + pad else pad + truncated)
            }
        }
        file.bufferedWriter().use { out ->
            header?.let { out.appendLine(fmt(it)) }
            rows.forEach { out.appendLine(fmt(it)) }
        }
        return file
    }
}
