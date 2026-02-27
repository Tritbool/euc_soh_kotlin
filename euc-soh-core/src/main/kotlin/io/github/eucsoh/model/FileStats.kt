package io.github.eucsoh.model

/**
 * Statistics computed from a single CSV log file.
 * Equivalent to one row in df_stats from Python.
 */
data class FileStats(
    val file: String,
    val source: String,
    val datetimeFirst: String?,
    val wheelKm: Double?,
    val wheelKmSource: String?,
    val vIdle: Double,
    val ns: Int?,
    val socRefOk: Boolean,
    val socRefVFull: Double?,
    val nPoints: Int,
    val reqMean: Double,
    val reqMedian: Double,
    val reqMedian25C: Double,
    val req95p: Double,
    val sag95p: Double,
    val sagMax: Double,
    val vMinStrong: Double,
    val iMax: Double,
    val i95p: Double,
    val tempBoardMax: Double?,
    val tempMotorMax: Double?,
    // Phase current metrics (MOSFET stress)
    val iPhase2Int: Double?,
    val iPhaseMax: Double?,
    val iPhase95p: Double?,
    // Decomposed resistance
    val rMosfetHot: Double?,
    val rBattMedian: Double?,
    val rBattMedian25C: Double?
)
