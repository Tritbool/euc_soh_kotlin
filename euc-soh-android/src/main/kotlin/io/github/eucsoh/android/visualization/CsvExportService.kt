package io.github.eucsoh.android.visualization

import android.content.Context
import io.github.eucsoh.SohAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CsvExportService(private val context: Context) {

    suspend fun exportToCsv(
        result: SohAnalyzer.AnalysisResult,
        wheelName: String,
        macAddress: String
    ): File = withContext(Dispatchers.IO) {

        val summary = buildSummary(result, wheelName)
        val logs = summary.logs
        if (logs.isEmpty()) throw IllegalArgumentException("No data to export")

        val headers = logs.first().keys.toList()
        val sb = StringBuilder()

        // Header
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })

        // Rows
        logs.forEach { row ->
            sb.appendLine(headers.joinToString(",") { col ->
                escapeCsv(formatValue(row[col]))
            })
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(context.getExternalFilesDir(null), "EUC_SoH").also { it.mkdirs() }
        val file = File(outputDir, "${wheelName}-${macAddress}_SoH_${timestamp}.csv")
        file.writeText(sb.toString(), Charsets.UTF_8)
        file
    }

    private fun escapeCsv(value: String): String {
        // RFC 4180 : entoure de guillemets si virgule, guillemet ou saut de ligne
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value.isNaN() || value.isInfinite()) "N/A"
        else "%.6f".format(value)
        is Float  -> if (value.isNaN() || value.isInfinite()) "N/A"
        else "%.6f".format(value)
        is Number -> value.toString()
        is Boolean -> if (value) "1" else "0"
        else -> value.toString()
    }

    private fun buildSummary(
        result: SohAnalyzer.AnalysisResult,
        wheelName: String
    ): SohAnalyzer.SummaryData {
        val analyzer = io.github.eucsoh.SohAnalyzer(
            csvSource = null, mosfetParams = null,
            logger = object : io.github.eucsoh.Logger {
                override fun d(tag: String, message: String) {}
                override fun e(tag: String, message: String, throwable: Throwable?) {}
            }
        )
        return analyzer.buildSummary(result, wheelName)
    }
}
