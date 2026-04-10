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

package io.github.eucsoh.android.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.github.eucsoh.Constants
import io.github.eucsoh.android.data.model.WheelDataSource
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.parser.DarknessBotParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Scanner for DarknessBot .dbb archive files.
 *
 * A .dbb file is a ZIP archive containing per-wheel daily CSV files named:
 *   {MAC12HEX}_{DD.MM.YYYY}.csv   e.g. 187A3E9C56FC_27.03.2026.csv
 * plus a DemoDeviceID_*.csv that is ignored.
 *
 * Responsibilities (Android layer only — no parsing logic here):
 * 1. Find .dbb files in the given directory tree.
 * 2. For each .dbb, check the cache: skip if already extracted and archive is unchanged.
 * 3. Open each CSV entry as a ZipInputStream, hand the stream to [DarknessBotParser].
 * 4. Write the resulting trip-CSVs to cacheDir/dbb/{archive_stem}/{mac}/
 * 5. Return a Map<MAC, WheelIdentity> pointing to the cached trip-CSV URIs.
 *
 * Cache layout:
 *   cacheDir/
 *     dbb/
 *       07.04.2026/              ← archive stem (filename without extension)
 *         .last_modified          ← last-modified epoch of the source .dbb
 *         18_7A_3E_9C_56_FC/
 *           187A3E9C56FC_27.03.2026_trip1.csv
 *           187A3E9C56FC_27.03.2026_trip2.csv
 *         ...
 */
class DarknessBotScanner(private val context: Context) {

    companion object {
        private const val TAG = "DarknessBotScanner"
        private const val CACHE_ROOT = "dbb"
        private const val LAST_MODIFIED_FILE = ".last_modified"
        /** Sub-directory inside each archiveStem cache dir holding the untouched original CSVs. */
        const val ORIG_CSV_DIR = "orig_csv"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File-based scan (legacy / direct filesystem access)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun scanFromFile(rootDir: File, max_depth:Int = 10): Map<String, WheelIdentity> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Found .dbb: $rootDir")
            val result = mutableMapOf<String, WheelIdentity>()
            rootDir.walkTopDown()
                .maxDepth(max_depth)
                .filter { it.isFile && it.extension.equals("dbb", ignoreCase = true) }
                .forEach { dbbFile ->
                    Log.d(TAG, "Found .dbb: ${dbbFile.absolutePath}")
                    try {
                        val wheels = processDbbFile(
                            archiveStem = dbbFile.nameWithoutExtension,
                            lastModified = dbbFile.lastModified(),
                            openStream = { dbbFile.inputStream() }
                        )
                        mergeInto(result, wheels)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process ${dbbFile.name}", e)
                    }
                }
            result
        }

    // ─────────────────────────────────────────────────────────────────────────
    // DocumentFile-based scan (SAF / modern Android)
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun scanFromUri(rootDoc: DocumentFile): Map<String, WheelIdentity> =
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, WheelIdentity>()
            collectDbbDocuments(rootDoc).forEach { doc ->
                Log.d(TAG, "Found .dbb document: ${doc.uri}")
                try {
                    val archiveStem = doc.name?.removeSuffix(".dbb")
                        ?.removeSuffix(".DBB") ?: return@forEach
                    val wheels = processDbbFile(
                        archiveStem = archiveStem,
                        lastModified = doc.lastModified(),
                        openStream = {
                            context.contentResolver.openInputStream(doc.uri)
                                ?: error("Cannot open ${doc.uri}")
                        }
                    )
                    mergeInto(result, wheels)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process ${doc.name}", e)
                }
            }
            result
        }

    // ─────────────────────────────────────────────────────────────────────────
    // Core processing: one .dbb archive → Map<MAC, WheelIdentity>
    // ─────────────────────────────────────────────────────────────────────────

    private fun processDbbFile(
        archiveStem: String,
        lastModified: Long,
        openStream: () -> InputStream
    ): Map<String, WheelIdentity> {

        val cacheDir = File(context.cacheDir, "$CACHE_ROOT/$archiveStem")
        val lastModifiedFile = File(cacheDir, LAST_MODIFIED_FILE)
        Log.d(TAG, "Cache dir: $cacheDir")

        // Cache hit: archive unchanged
        if (cacheDir.exists() && lastModifiedFile.exists()) {
            val cachedModified = lastModifiedFile.readText().trim().toLongOrNull()
            if (cachedModified == lastModified) {
                Log.d(TAG, "Cache hit for $archiveStem")
                return readWheelIdentitiesFromCache(cacheDir)
            }
        }

        // Cache miss or stale: (re)extract
        Log.d(TAG, "Cache miss for $archiveStem — extracting")
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()

        val wheelTrips = mutableMapOf<String, MutableList<Uri>>() // mac → trip URIs
        val origCsvDir = File(cacheDir, ORIG_CSV_DIR)
        origCsvDir.mkdirs()

        ZipInputStream(openStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = File(entry.name).name // strip any path prefix
                    Log.d(TAG, "ZIP entry: $entryName")

                    // Buffer the full entry so we can (1) save the original and (2) parse it
                    val entryBytes = zip.readBytes()

                    // Save original CSV verbatim — used later by DarknessBotRepackService
                    val origFile = File(origCsvDir, entryName)
                    origFile.writeBytes(entryBytes)
                    Log.d(TAG, "Saved original CSV: ${origFile.absolutePath}")

                    val result = DarknessBotParser.parse(
                        fileName = entryName,
                        inputStream = entryBytes.inputStream()
                    )

                    if (result != null) {
                        val macDir = File(cacheDir, result.macAddress).apply { mkdirs() }
                        result.trips.forEach { trip ->
                            val tripFileName =
                                "${Constants.DARKNESS_BOT}_${result.macAddress}_${trip.date}_trip${trip.tripIndex}.csv"
                            val tripFile = File(macDir, tripFileName)
                            tripFile.writeText(trip.csvContent, Charsets.UTF_8)
                            Log.d(TAG, "Wrote trip: ${tripFile.absolutePath}")
                            wheelTrips
                                .getOrPut(result.macAddress) { mutableListOf() }
                                .add(Uri.fromFile(tripFile))
                        }
                    }
                }
                entry = zip.nextEntry
            }
            zip.closeEntry()
        }


        // Stamp cache with archive last-modified time
        lastModifiedFile.writeText(lastModified.toString())

        return buildWheelIdentities(wheelTrips)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cache reading (on cache hit)
    // ─────────────────────────────────────────────────────────────────────────

    private fun readWheelIdentitiesFromCache(cacheDir: File): Map<String, WheelIdentity> {
        val wheelTrips = mutableMapOf<String, MutableList<Uri>>()
        cacheDir.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { macDir ->
                val mac = macDir.name
                macDir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
                    .forEach { tripFile ->
                        wheelTrips.getOrPut(mac) { mutableListOf() }
                            .add(Uri.fromFile(tripFile))
                    }
            }
        return buildWheelIdentities(wheelTrips)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build WheelIdentity objects from mac → trip URI lists.
     * MAC is in WheelLog underscore format ("18_7A_3E_9C_56_FC").
     * WheelIdentity.macAddress uses colon format ("18:7A:3E:9C:56:FC") like the rest of the app.
     */
    private fun buildWheelIdentities(
        wheelTrips: Map<String, List<Uri>>
    ): Map<String, WheelIdentity> {
        return wheelTrips
            .filter { (_, uris) -> uris.isNotEmpty() }
            .mapValues { (macUnderscore, uris) ->
                val macColon = macUnderscore.replace("_", ":").uppercase()
                WheelIdentity(
                    macAddress = macColon,
                    displayName = macColon,
                    csvFiles = uris.toSet(),
                    source = WheelDataSource.DARKNESS_BOT
                )
            }
            // Re-key by colon MAC for consistency with the rest of the scanner infrastructure
            .entries.associate { (_, identity) -> identity.macAddress to identity }
    }

    private fun mergeInto(
        target: MutableMap<String, WheelIdentity>,
        source: Map<String, WheelIdentity>
    ) {
        source.forEach { (mac, identity) ->
            target.merge(mac, identity) { existing, new ->
                existing.copy(csvFiles = existing.csvFiles + new.csvFiles)
            }
        }
    }

    private fun collectDbbDocuments(
        doc: DocumentFile,
        collector: MutableList<DocumentFile> = mutableListOf()
    ): List<DocumentFile> {
        if (doc.isFile) {
            val name = doc.name ?: return collector
            if (name.endsWith(".dbb", ignoreCase = true)) collector.add(doc)
        } else if (doc.isDirectory) {
            doc.listFiles().forEach { collectDbbDocuments(it, collector) }
        }
        return collector
    }
}
