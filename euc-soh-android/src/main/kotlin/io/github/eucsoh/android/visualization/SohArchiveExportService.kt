package io.github.eucsoh.android.visualization

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.data.model.WheelDataSource

/**
 * Exports a ZIP archive with structure:
 *   Archive/
 *   ├── soh.pdf
 *   ├── EUC World/
 *   │   └── file.csv
 *   └── WheelLog/
 *       └── 11_22_33_44_55_66/
 *           └── file.csv
 */
class SohArchiveExportService(private val context: Context) {

    suspend fun exportArchive(
        wheelName: String,
        macAddress: String,
        fileReports: List<SohAnalyzer.FileReport>,
        pdfFile: File
    ): File = withContext(Dispatchers.IO) {

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputDir = File(
            context.getExternalFilesDir(null),
            "EUC_SoH_Archives"
        ).also { it.mkdirs() }

        val zipFile = File(outputDir, "${wheelName}_SoH_${timestamp}.zip")
        val macFolder = macAddress.replace(":", "_")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->

            // 1. soh.pdf
            zos.addFile(pdfFile, "soh.pdf")

            // 2. CSV files groupés par source
            fileReports
                .filter { it.accepted }
                .forEach { report ->
                    val entryPath = when {
                        report.source == "EUC World" ->
                            "EUC World/${report.fileName}"
                        report.source == "WheelLog" ->
                            "WheelLog/$macFolder/${report.fileName}"
                        else ->
                            "Other/${report.fileName}"
                    }
                    try {
                        val uri = Uri.parse(report.path)
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            zos.putNextEntry(ZipEntry(entryPath))
                            input.copyTo(zos)
                            zos.closeEntry()
                        }
                    } catch (_: Exception) { /* skip unreadable */ }
                }
        }

        zipFile
    }

    private fun ZipOutputStream.addFile(file: File, entryName: String) {
        putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(this) }
        closeEntry()
    }
}
