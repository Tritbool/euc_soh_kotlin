package io.github.eucsoh.android.data.scanner

import android.content.Context
import android.net.Uri
import io.github.eucsoh.android.data.model.WheelDataSource
import io.github.eucsoh.android.data.model.WheelIdentity
import java.io.File

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

    /**
     * Scans a specific EUC World directory.
     * Walks through ALL subdirectories and collects CSV files.
     * 
     * @param eucWorldDir The "EUC World" directory to scan (absolute path)
     * @return Map of MAC address -> WheelIdentity
     */
    fun scanFolder(eucWorldDir: File): Map<String, WheelIdentity> {
        if (!eucWorldDir.exists() || !eucWorldDir.isDirectory) {
            return emptyMap()
        }

        val result = mutableMapOf<String, WheelIdentity>()

        // Walk through ALL subdirectories and find CSV files
        val csvFiles = eucWorldDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }

        for (file in csvFiles) {
            try {
                val info = extractWheelInfoFromCsv(file) ?: continue
                val mac = info.macAddress

                // Merge with existing entry (aggregate CSV files)
                result.merge(mac, info) { existing, new ->
                    existing.copy(
                        csvFiles = (existing.csvFiles + new.csvFiles).distinct(),
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
                // Skip corrupted files
                continue
            }
        }

        return result
    }

    /**
     * Extracts wheel metadata from EUC World CSV.
     * 
     * The 'extra' column contains ONE key=value per row:
     * - Row 1: euc.batteryCircuitResistance=0.3
     * - Row 2: euc.battery1Imbalance=0.009
     * - ...
     * - Row N: euc.btAddress=E9:A8:39:04:B4:8C
     */
    private fun extractWheelInfoFromCsv(csvFile: File): WheelIdentity? {
        val metadata = mutableMapOf<String, String>()
        var foundMac = false
        var foundBtName = false
        var foundMake = false
        var foundModel = false
        var foundSerial = false

        csvFile.bufferedReader().use { reader ->
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
                                "euc.btAddress" -> {
                                    metadata[key] = value
                                    foundMac = true
                                }
                                "euc.btName" -> {
                                    metadata[key] = value
                                    foundBtName = true
                                }
                                "euc.make" -> {
                                    metadata[key] = value
                                    foundMake = true
                                }
                                "euc.model" -> {
                                    metadata[key] = value
                                    foundModel = true
                                }
                                "euc.serial" -> {
                                    metadata[key] = value
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
            csvFiles = listOf(Uri.fromFile(csvFile)),
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
