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
import android.util.Log
import io.github.eucsoh.android.data.scanner.DarknessBotScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Rebuilds a .dbb archive from the original CSV files saved in the cache by [DarknessBotScanner].
 *
 * Cache layout written by [DarknessBotScanner]:
 *   cacheDir/
 *     dbb/
 *       {archiveStem}/          ← e.g. "09.04.2026"
 *         orig_csv/
 *           187A3E9C56FC_27.03.2026.csv
 *           882584F038D3_31.03.2026.csv
 *           ...
 *
 * Output: a single  DD.MM.YYYY.dbb  (today's date) in [outputDir], containing
 * every original CSV found across ALL archiveStem directories.
 *
 * If no original CSV files are found in the cache, returns null.
 */
class DarknessBotRepackService(private val context: Context) {

    private val TAG = "DarknessBotRepackService"

    /**
     * @param outputDir   Directory where the .dbb file will be written.
     * @param macAddress  If non-null, only CSV files belonging to this MAC are included.
     *                    Expected format: colon-separated uppercase, e.g. "18:7A:3E:9C:56:FC".
     *                    Converted internally to 12-char hex prefix, e.g. "187A3E9C56FC".
     * @return            The produced .dbb [File], or null if no matching orig_csv files were found.
     */
    suspend fun repack(outputDir: File, macAddress: String? = null): File? = withContext(Dispatchers.IO) {
        // Prefix used to match CSV filenames: "187A3E9C56FC" (no separators, uppercase)
        val macPrefix: String? = macAddress?.replace(":", "")?.uppercase()

        val dbbCacheRoot = File(context.cacheDir, "dbb")
        if (!dbbCacheRoot.exists()) {
            Log.d(TAG, "No dbb cache root — nothing to repack")
            return@withContext null
        }

        // Collect original CSV files from every archiveStem/orig_csv/, filtered by MAC if provided
        val csvFiles: List<File> = dbbCacheRoot
            .listFiles { f -> f.isDirectory }
            .orEmpty()
            .flatMap { archiveDir ->
                val origCsvDir = File(archiveDir, DarknessBotScanner.ORIG_CSV_DIR)
                if (origCsvDir.exists()) {
                    origCsvDir.listFiles { f ->
                        f.isFile &&
                        f.extension.equals("csv", ignoreCase = true) &&
                        (macPrefix == null || f.nameWithoutExtension.startsWith(macPrefix, ignoreCase = true))
                    }.orEmpty().toList()
                } else {
                    emptyList()
                }
            }

        if (csvFiles.isEmpty()) {
            Log.d(TAG, "No original CSV files found in cache — nothing to repack")
            return@withContext null
        }

        Log.d(TAG, "Repacking ${csvFiles.size} original CSV file(s) into .dbb")

        outputDir.mkdirs()
        val today = SimpleDateFormat("dd.MM.yyyy", Locale.US).format(Date())
        val dbbFile = File(outputDir, "$today.dbb")

        ZipOutputStream(FileOutputStream(dbbFile)).use { zos ->
            // Track names already added to avoid duplicates when the same MAC+day
            // appears in multiple archiveStems (unlikely but defensive)
            val addedNames = mutableSetOf<String>()
            for (csv in csvFiles) {
                val entryName = csv.name
                if (entryName in addedNames) {
                    Log.w(TAG, "Duplicate entry skipped: $entryName")
                    continue
                }
                addedNames.add(entryName)
                zos.putNextEntry(ZipEntry(entryName))
                csv.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
                Log.d(TAG, "Added to .dbb: $entryName")
            }
        }

        Log.d(TAG, "Produced .dbb: ${dbbFile.absolutePath} (${dbbFile.length()} bytes)")
        dbbFile
    }
}
