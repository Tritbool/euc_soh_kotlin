package io.github.eucsoh.android.data.model

import android.net.Uri

/**
 * Information about a CSV file including validation status.
 */
data class CsvFileInfo(
    val uri: Uri,
    val fileName: String,
    val sizeBytes: Long,
    val isValid: Boolean,
    val validationMessage: String,
    val nPoints: Int?,
    val hasTemperature: Boolean,
    val reqMedian: Double?,
    val wheelKm: Double?,
    val isExcluded: Boolean = false
)
