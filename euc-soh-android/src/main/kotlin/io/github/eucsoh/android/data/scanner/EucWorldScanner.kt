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
import io.github.eucsoh.android.data.model.WheelDataSource
import io.github.eucsoh.android.data.model.WheelIdentity
import java.io.File
import java.io.InputStream

/**
 * Scanner for EUC World logs.
 * 
 * EUC World stores logs in subdirectories under "EUC World".
 * This scanner recursively walks ALL subdirectories to find CSV files.
 * 
 * Each CSV has a column 'extra' containing ONE key=value pair per row.
 * We need to scan all rows to build the metadata dictionary:
 * - euc.btAddress (MAC - required)
 * - euc.btName (display name)
 * - euc.make (manufacturer)
 * - euc.model
 * - euc.serial
 */
class EucWorldScanner(private val context: Context) {

    companion object {
        private const val TAG = "EucWorldScanner"
    }

    /**
     * Scans a specific EUC World directory (File-based).
     * Walks through ALL subdirectories and collects CSV files.
     * 
     * @param eucWorldDir The "EUC World" directory to scan (absolute path)
     * @return Map of MAC address -> WheelIdentity
     */
    fun scanFolder(eucWorldDir: File): Map<String, WheelIdentity> {
        if (!eucWorldDir.exists() || !eucWorldDir.isDirectory) {
            Log.w(TAG, "EUC World directory not valid: ${eucWorldDir.absolutePath}")
            return emptyMap()
        }

        val result = mutableMapOf<String, WheelIdentity>()

        // Walk through ALL subdirectories and find CSV files
        val csvFiles = eucWorldDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }

        for (file in csvFiles) {
            try {
                val info = extractWheelInfoFromFile(file) ?: continue
                val mac = info.macAddress
                Log.d(TAG, "Extracted info from ${file.name}: MAC=$mac")

                // Merge with existing entry (aggregate CSV files)
                result.merge(mac, info) { existing, new ->
                    existing.copy(
                        csvFiles = existing.csvFiles + new.csvFiles,
                        manufacturer = new.manufacturer ?: existing.manufacturer,
                        model = new.model ?: existing.model,
                        serialNumber = new.serialNumber ?: existing.serialNumber,
                        displayName = if (new.displayName != new.macAddress) 
                            new.displayName 
                        else 
                            existing.displayName
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract info from ${file.name}", e)
                continue
            }
        }

        return result
    }

    /**
     * Scans a specific EUC World directory (DocumentFile-based).
     * Walks through ALL subdirectories and collects CSV files.
     * 
     * @param eucWorldDoc The "EUC World" directory document
     * @return Map of MAC address -> WheelIdentity
     */
    fun scanDocument(eucWorldDoc: DocumentFile): Map<String, WheelIdentity> {
        if (!eucWorldDoc.exists() || !eucWorldDoc.isDirectory) {
            Log.w(TAG, "EUC World document not valid: ${eucWorldDoc.uri}")
            return emptyMap()
        }

        val result = mutableMapOf<String, WheelIdentity>()
        val csvDocs = mutableListOf<DocumentFile>()

        // Collect all CSV files recursively
        collectCsvDocuments(eucWorldDoc, csvDocs)
        Log.d(TAG, "Found ${csvDocs.size} CSV files in EUC World")

        for (doc in csvDocs) {
            try {
                val info = extractWheelInfoFromDocument(doc) ?: continue
                val mac = info.macAddress
                Log.d(TAG, "Extracted info from ${doc.name}: MAC=$mac")

                // Merge with existing entry (aggregate CSV files)
                result.merge(mac, info) { existing, new ->
                    existing.copy(
                        csvFiles = existing.csvFiles + new.csvFiles,
                        manufacturer = new.manufacturer ?: existing.manufacturer,
                        model = new.model ?: existing.model,
                        serialNumber = new.serialNumber ?: existing.serialNumber,
                        displayName = if (new.displayName != new.macAddress) 
                            new.displayName 
                        else 
                            existing.displayName
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract info from ${doc.name}", e)
                continue
            }
        }

        return result
    }

    /**
     * Recursively collects CSV documents.
     */
    private fun collectCsvDocuments(doc: DocumentFile, collector: MutableList<DocumentFile>) {
        if (doc.isFile) {
            val name = doc.name ?: return
            if (name.endsWith(".csv", ignoreCase = true)) {
                collector.add(doc)
            }
        } else if (doc.isDirectory) {
            doc.listFiles().forEach { child ->
                collectCsvDocuments(child, collector)
            }
        }
    }

    /**
     * Extracts wheel metadata from EUC World CSV file.
     */
    private fun extractWheelInfoFromFile(csvFile: File): WheelIdentity? {
        return csvFile.inputStream().use { stream ->
            extractWheelInfoFromStream(stream, Uri.fromFile(csvFile))
        }
    }

    /**
     * Extracts wheel metadata from EUC World CSV document.
     */
    private fun extractWheelInfoFromDocument(csvDoc: DocumentFile): WheelIdentity? {
        val stream = context.contentResolver.openInputStream(csvDoc.uri) ?: return null
        return stream.use { 
            extractWheelInfoFromStream(it, csvDoc.uri)
        }
    }

    /**
     * Extracts wheel metadata from CSV input stream.
     * 
     * The 'extra' column contains ONE key=value per row:
     * - Row 1: euc.batteryCircuitResistance=0.3
     * - Row 2: euc.battery1Imbalance=0.009
     * - ...
     * - Row N: euc.btAddress=E9:A8:39:04:B4:8C
     */
    private fun extractWheelInfoFromStream(stream: InputStream, uri: Uri): WheelIdentity? {
        val metadata = mutableMapOf<String, String>()
        var foundMac = false
        var foundBtName = false
        var foundMake = false
        var foundModel = false
        var foundSerial = false

        stream.bufferedReader().use { reader ->
            val header = reader.readLine() ?: return null
            val columns = header.split(",")
            val extraIndex = columns.indexOfFirst { 
                it.trim().equals("extra", ignoreCase = true) 
            }

            if (extraIndex == -1) return null

            // Read lines until we have all essential info
            for (line in reader.lineSequence()) {
                val cells = parseCsvLine(line)
                if (cells.size > extraIndex) {
                    val extraCell = cells[extraIndex].trim()

                    if (extraCell.contains("=")) {
                        val parts = extraCell.split("=", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()

                            when (key) {
                                "euc.btAddress", "eucBluetoothAddress" -> {
                                    metadata["euc.btAddress"] = value
                                    foundMac = true
                                }
                                "euc.btName", "eucBluetoothName" -> {
                                    metadata["euc.btName"] = value
                                    foundBtName = true
                                }
                                "euc.make", "eucType" -> {
                                    metadata["euc.make"] = value
                                    foundMake = true
                                }
                                "euc.model", "eucModel" -> {
                                    metadata["euc.model"] = value
                                    foundModel = true
                                }
                                "euc.serial", "eucSerial" -> {
                                    metadata["euc.serial"] = value
                                    foundSerial = true
                                }
                            }

                            // Early exit when we have all info
                            if (foundMac && foundBtName && foundMake && foundModel && foundSerial) {
                                break
                            }
                        }
                    }
                }
            }
        }

        // Require at least MAC address
        val mac = metadata["euc.btAddress"] ?: return null

        return WheelIdentity(
            macAddress = mac.uppercase(),
            displayName = metadata["euc.btName"] ?:
                "${metadata["euc.make"] ?: ""} ${metadata["euc.model"] ?: ""}".trim()
                    .ifEmpty { mac },
            csvFiles = setOf(uri),
            manufacturer = metadata["euc.make"],
            model = metadata["euc.model"],
            serialNumber = metadata["euc.serial"],
            source = WheelDataSource.EUC_WORLD
        )
    }

    /**
     * Parse CSV line handling quotes and escaped commas.
     */
    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        var currentCell = StringBuilder()
        var inQuotes = false

        for (i in line.indices) {
            val char = line[i]
            when {
                char == '"' && (i == 0 || line[i - 1] != '\\') -> {
                    inQuotes = !inQuotes
                }
                char == ',' && !inQuotes -> {
                    cells.add(currentCell.toString())
                    currentCell.clear()
                }
                else -> currentCell.append(char)
            }
        }
        cells.add(currentCell.toString())

        return cells.map { it.trim() }
    }
}
