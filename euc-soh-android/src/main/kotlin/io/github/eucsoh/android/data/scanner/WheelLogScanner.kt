package io.github.eucsoh.android.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
        private const val TAG = "WheelLogScanner"
        
        // Pattern: XX_YY_ZZ_AA_BB_CC (MAC with underscores)
        private val MAC_FOLDER_PATTERN = Regex(
            """^([0-9A-F]{2}_){5}[0-9A-F]{2}$""", 
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * Scans a specific WheelLog folder structure (File-based).
     * 
     * @param wheelLogDir The WheelLog directory to scan (absolute path)
     * @return Map of MAC address -> WheelIdentity
     */
    fun scanFolder(wheelLogDir: File): Map<String, WheelIdentity> {
        if (!wheelLogDir.exists() || !wheelLogDir.isDirectory) {
            Log.w(TAG, "WheelLog directory not valid: ${wheelLogDir.absolutePath}")
            return emptyMap()
        }

        val result = mutableMapOf<String, WheelIdentity>()

        wheelLogDir.listFiles()?.forEach { macFolder ->
            if (macFolder.isDirectory && MAC_FOLDER_PATTERN.matches(macFolder.name)) {
                // Convert folder name to MAC (18_7A_3E_9C_56_FC -> 18:7A:3E:9C:56:FC)
                val mac = macFolder.name.replace("_", ":").uppercase()
                Log.d(TAG, "Found MAC folder: ${macFolder.name} -> $mac")

                // Find all CSV files in this folder (exclude RAW_*.csv)
                val csvFiles = macFolder.walkTopDown()
                    .filter { it.isFile && it.extension.equals("csv", ignoreCase = true) }
                    .filter { !it.name.startsWith("RAW_", ignoreCase = true) }
                    .map { Uri.fromFile(it) }
                    .toSet()

                if (csvFiles.isNotEmpty()) {
                    Log.d(TAG, "Found ${csvFiles.size} CSV files for $mac")
                    result[mac] = WheelIdentity(
                        macAddress = mac,
                        displayName = mac,
                        csvFiles = csvFiles,
                        source = WheelDataSource.WHEELLOG
                    )
                }
            }
            else{
                Log.w(TAG, "WheelLog directory name does not match MAC pattern ${wheelLogDir.absolutePath}/${macFolder}")
            }
        }

        return result
    }

    /**
     * Scans a specific WheelLog folder structure (DocumentFile-based).
     * 
     * @param wheelLogDoc The WheelLog directory document
     * @return Map of MAC address -> WheelIdentity
     */
    fun scanDocument(wheelLogDoc: DocumentFile): Map<String, WheelIdentity> {
        if (!wheelLogDoc.exists() || !wheelLogDoc.isDirectory) {
            Log.w(TAG, "WheelLog document not valid: ${wheelLogDoc.uri}")
            return emptyMap()
        }

        val result = mutableMapOf<String, WheelIdentity>()

        wheelLogDoc.listFiles().forEach { macFolder ->
            val folderName = macFolder.name ?: return@forEach
            
            if (macFolder.isDirectory && MAC_FOLDER_PATTERN.matches(folderName)) {
                // Convert folder name to MAC (18_7A_3E_9C_56_FC -> 18:7A:3E:9C:56:FC)
                val mac = folderName.replace("_", ":").uppercase()
                Log.d(TAG, "Found MAC folder: $folderName -> $mac")

                // Find all CSV files recursively in this folder (exclude RAW_*.csv)
                val csvFiles = mutableListOf<Uri>()
                collectCsvFiles(macFolder, csvFiles)

                if (csvFiles.isNotEmpty()) {
                    Log.d(TAG, "Found ${csvFiles.size} CSV files for $mac")
                    result[mac] = WheelIdentity(
                        macAddress = mac,
                        displayName = mac,
                        csvFiles = csvFiles.toSet(),
                        source = WheelDataSource.WHEELLOG
                    )
                }
            }
        }

        return result
    }

    /**
     * Recursively collects CSV file URIs from a DocumentFile tree.
     * Excludes RAW_*.csv files (binary dumps).
     */
    private fun collectCsvFiles(doc: DocumentFile, collector: MutableList<Uri>) {
        if (doc.isFile) {
            val name = doc.name ?: return
            if (name.endsWith(".csv", ignoreCase = true) && 
                !name.startsWith("RAW_", ignoreCase = true)) {
                collector.add(doc.uri)
            }
        } else if (doc.isDirectory) {
            doc.listFiles().forEach { child ->
                collectCsvFiles(child, collector)
            }
        }
    }
}
