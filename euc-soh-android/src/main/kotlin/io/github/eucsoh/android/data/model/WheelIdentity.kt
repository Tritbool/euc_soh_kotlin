package io.github.eucsoh.android.data.model

import android.net.Uri

/**
 * Represents an identified EUC wheel with its associated log files.
 */
data class WheelIdentity(
    val macAddress: String,              // "E9:A8:39:04:B4:8C"
    val displayName: String,             // "P6-50009559" or MAC if no name
    val csvFiles: List<Uri>,             // List of CSV files for this wheel
    val manufacturer: String? = null,     // "Inmotion"
    val model: String? = null,           // "P6"
    val serialNumber: String? = null,    // "A14219A150009559"
    val source: WheelDataSource = WheelDataSource.UNKNOWN
)

/**
 * Source from which wheel data was detected.
 */
enum class WheelDataSource {
    WHEELLOG,      // From Download/WheelLog/MAC_ADDRESS/ folders
    EUC_WORLD,     // From Download/EUC World/EUC Data Logs/
    MANUAL,        // User-selected folder
    UNKNOWN
}
