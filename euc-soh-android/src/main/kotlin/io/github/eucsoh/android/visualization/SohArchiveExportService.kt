/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.android.visualization

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.eucsoh.Constants.DARKNESS_BOT
import io.github.eucsoh.Constants.EUC_WORLD
import io.github.eucsoh.Constants.WHEELLOG
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

    companion object {
        private val TAG = "SohArchiveExportService"

    }
    private val repackService = DarknessBotRepackService(context)

    suspend fun exportArchive(
        wheelName: String,
        macAddress: String,
        fileReports: List<SohAnalyzer.FileReport>,
        pdfFile: File,
        csvFile: File?,
        darknessBotEnabled: Boolean = false
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
            zos.addFile(pdfFile, "soh-${wheelName}-${macAddress}.pdf")
            // soh.csv si fourni
            csvFile?.let { zos.addFile(it, "soh_stats-${wheelName}-${macAddress}.csv") }


            // 2. CSV files groupés par source
            fileReports
                .filter { it.accepted && it.source != DARKNESS_BOT}
                .forEach { report ->
                    val entryPath = when {
                        report.source == EUC_WORLD -> "EUC World/${report.fileName}"
                        report.source == WHEELLOG  -> "WheelLog/$macFolder/${report.fileName}"
                        else                         -> "Other/${report.fileName}"
                    }
                    try {
                        // Normalise le path en URI lisible par ContentResolver
                        val uri = when {
                            report.path.startsWith("content://") ->
                                Uri.parse(report.path)
                            report.path.startsWith("file://") ->
                                Uri.parse(report.path)
                            else ->
                                // Chemin brut File → on lit directement avec java.io.File
                                null
                        }

                        val inputStream = when {
                            report.path.startsWith("content://") -> {
                                try {
                                    context.contentResolver.openInputStream(Uri.parse(report.path))
                                } catch (e: SecurityException) {
                                    Log.w(TAG, "No permission for ${report.path}: ${e.message}")
                                    null
                                }
                            }
                            report.path.startsWith("file://") ->
                                java.io.File(Uri.parse(report.path).path!!).inputStream()
                            else ->
                                java.io.File(report.path).takeIf { it.canRead() }?.inputStream()
                        }


                        inputStream?.use { input ->
                            zos.putNextEntry(ZipEntry(entryPath))
                            input.copyTo(zos)
                            zos.closeEntry()
                        }
                    } catch (e: Exception) {
                        Log.w("SohArchiveExport", "Skipping ${report.fileName}: ${e.message}")
                    }
                }

            // 3. .dbb repack (only when DarknessBot data was used)
            if (darknessBotEnabled) {
                try {
                    val dbbTempDir = File(context.cacheDir, "dbb_repack_tmp").also { it.mkdirs() }
                    val dbbFile = repackService.repack(dbbTempDir, macAddress)
                    if (dbbFile != null) {
                        zos.addFile(dbbFile, dbbFile.name)
                        Log.d(TAG, "Added .dbb to archive: ${dbbFile.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DarknessBot repack failed, skipping: ${e.message}")
                }
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
