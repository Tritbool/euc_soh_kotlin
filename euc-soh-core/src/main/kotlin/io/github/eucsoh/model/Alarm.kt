package io.github.eucsoh.model

/**
 * Represents a detected alarm/anomaly in a log file.
 */
data class Alarm(
    val file: String,
    val wheelKm: Double?,
    val datetimeFirst: String?,
    val reasons: String
)
