package io.github.eucsoh.android.data.scanner

import android.content.Context
import android.net.Uri
import io.github.eucsoh.android.data.model.WheelDataSource
import io.github.eucsoh.android.data.model.WheelIdentity
import java.io.File

/**
 * Scanner for WheelLog logs.
 * 
 * WheelLog stores logs in: WheelLog/MAC_ADDRESS/
 * Each subfolder name IS the MAC address (e.g., "18_7A_3E_9C_56_FC")
 * 
 * Structure:
 * WheelLog/
 *   ├── 18_7A_3E_9C_56_FC/
 *   │   ├── log1.csv
 *   │   └── log2.csv
 *   └── E9_A8_39_04_B4_8C/
 *       ├── log3.csv
 *       └── log4.csv
 */
class WheelLogScanner(private val context: Context) {

    companion object {
        // Pattern: XX_YY_ZZ_AA_BB_CC (MAC with underscores)
        private val MAC_FOLDER_PATTERN = Regex(
            """^([0-9A-F]{2}_){5}[0-9A-F]{2}$""", 
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * Scans a specific WheelLog folder structure.
     * 
     * @param wheelLogDir The WheelLog directory to scan (absolute path)
     * @return Map of MAC address -> WheelIdentity
     */
    fun scanFolder(wheelLogDir: File): Map<String, WheelIdentity> {
        if (!wheelLogDir.exists() || !wheelLogDir.isDirectory) {
            return emptyMap()
        }

        val result = mutableMapOf<String, WheelIdentity>()

        wheelLogDir.listFiles()?.forEach { macFolder ->
            if (macFolder.isDirectory && MAC_FOLDER_PATTERN.matches(macFolder.name)) {
                // Convert folder name to MAC (18_7A_3E_9C_56_FC -> 18:7A:3E:9C:56:FC)
                val mac = macFolder.name.replace("_", ":").uppercase()

                // Find all CSV files in this folder
                val csvFiles = macFolder.walkTopDown()
                    .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
                    .map { Uri.fromFile(it) }
                    .toList()

                if (csvFiles.isNotEmpty()) {
                    result[mac] = WheelIdentity(
                        macAddress = mac,
                        displayName = mac,  // Will be updated if found in EUC World
                        csvFiles = csvFiles,
                        source = WheelDataSource.WHEELLOG
                    )
                }
            }
        }

        return result
    }
}
