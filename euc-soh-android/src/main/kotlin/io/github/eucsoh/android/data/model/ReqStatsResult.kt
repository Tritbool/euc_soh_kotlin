package io.github.eucsoh.android.data.model

/**
 * Statistical result for a single CSV file.
 * Matches the structure from SohAnalyzer.AnalysisResult summary logs.
 * 
 * Used for chart generation in SohChartGenerator.
 */
data class ReqStatsResult(
    val fileName: String,
    val wheelKm: Double? = null,
    
    // Resistance metrics
    val reqMedian: Double? = null,
    val req95p: Double? = null,
    
    // Voltage sag metrics
    val sagMedian: Double? = null,
    val sag95p: Double? = null,
    val sagMax: Double? = null,
    val vMinStrong: Double? = null,
    
    // Current metrics
    val iMax: Double? = null,
    val i95p: Double? = null,
    
    // Temperature metrics
    val tempBoardMax: Double? = null,
    val tempMotorMax: Double? = null
)
