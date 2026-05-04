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
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.BuildConfig
import io.github.eucsoh.android.data.model.WheelDataSource
import androidx.core.net.toUri

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

        // Collect SHA256 hashes for each file written to the ZIP
        val fileEntries = mutableListOf<ArchiveFileEntry>()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->

            // 1. soh.pdf
            val pdfEntryName = "soh-${wheelName}-${macAddress}.pdf"
            fileEntries += zos.addFileWithHash(pdfFile, pdfEntryName)
            // soh.csv si fourni
            csvFile?.let {
                val csvEntryName = "soh_stats-${wheelName}-${macAddress}.csv"
                fileEntries += zos.addFileWithHash(it, csvEntryName)
            }


            // 2. CSV files groupés par source
            fileReports
                .filter { it.accepted && it.source != DARKNESS_BOT}
                .forEach { report ->
                    val ext = if(report.path.endsWith(".csv")) "" else ".csv"
                    val entryPath = when {
                        report.source == EUC_WORLD -> "EUC World/${File(report.path).name}$ext"
                        report.source == WHEELLOG  -> "WheelLog/$macFolder/${File(report.path).name}$ext"
                        else                         -> "Other/${File(report.path).name}$ext"
                    }
                    try {
                        val inputStream = when {
                            report.path.startsWith("content://") -> {
                                try {
                                    context.contentResolver.openInputStream(report.path.toUri())
                                } catch (e: SecurityException) {
                                    Log.w(TAG, "No permission for ${report.path}: ${e.message}")
                                    null
                                }
                            }
                            report.path.startsWith("file://") ->
                                java.io.File(report.path.toUri().path!!).inputStream()
                            else ->
                                java.io.File(report.path).takeIf { it.canRead() }?.inputStream()
                        }


                        inputStream?.use { input ->
                            val digest = MessageDigest.getInstance("SHA-256")
                            zos.putNextEntry(ZipEntry(entryPath))
                            val digestOut = DigestOutputStream(zos as OutputStream, digest)
                            input.copyTo(digestOut)
                            digestOut.flush()
                            zos.closeEntry()
                            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
                            fileEntries += ArchiveFileEntry(name = entryPath, sha256 = sha256)
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
                        fileEntries += zos.addFileWithHash(dbbFile, dbbFile.name)
                        Log.d(TAG, "Added .dbb to archive: ${dbbFile.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "DarknessBot repack failed, skipping: ${e.message}")
                }
            }

            // 4. Build manifest, compute HMAC, write manifest.json as last entry
            val manifest = ArchiveManifest(
                appVersionCode = BuildConfig.VERSION_CODE,
                wheelMac = macAddress,
                files = fileEntries
            ).let { m -> m.copy(hmac = m.computeHmac(ArchiveHmacKey.SECRET)) }

            val manifestBytes = manifest.toJson().toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()
            Log.d(TAG, "Manifest written: ${fileEntries.size} file entries, hmac=${manifest.hmac.take(16)}...")

        }

        zipFile
    }

    /**
     * Adds a file to the ZIP and returns its SHA256 hash for the manifest.
     */
    private fun ZipOutputStream.addFileWithHash(file: File, entryName: String): ArchiveFileEntry {
        val digest = MessageDigest.getInstance("SHA-256")
        putNextEntry(ZipEntry(entryName))
        val digestOut = DigestOutputStream(this as OutputStream, digest)
        file.inputStream().use { it.copyTo(digestOut) }
        digestOut.flush()
        closeEntry()
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        return ArchiveFileEntry(name = entryName, sha256 = sha256)
    }
}
