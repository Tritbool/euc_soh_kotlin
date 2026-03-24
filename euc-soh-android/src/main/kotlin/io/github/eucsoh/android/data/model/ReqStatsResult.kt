package io.github.eucsoh.android.data.model

import io.github.eucsoh.Constants.Metrics

/**
 * Statistical result for a single CSV file.
 * Matches the structure from SohAnalyzer.AnalysisResult summary logs.
 *
 * Used for chart generation in SohChartGenerator.
 *
 * The companion object [extractors] provides a bridge between [Metrics] (core,
 * csv_code-based) and the Kotlin field names of this data class.  Any [Metrics]
 * entry absent from the map (e.g. REQ_MEAN which has no dedicated field here)
 * is simply skipped at chart-generation time.
 */
data class ReqStatsResult(
    val fileName: String,
    val wheelKm: Double? = null,

    // Resistance metrics
    val reqMedian: Double? = null,
    val reqMedian25C: Double? = null,
    val req95p: Double? = null,

    // Battery metrics
    val rBattMedian: Double? = null,
    val rBattMedian25C: Double? = null,

    // Voltage sag metrics
    val sagMedian: Double? = null,
    val sag95p: Double? = null,
    val sagMax: Double? = null,
    val vMinStrong: Double? = null,

    // Current metrics
    val iMax: Double? = null,
    val iPhaseMax: Double? = null,
    val i95p: Double? = null,
    val iPhase95p: Double? = null,
    val iPhase2Int: Double? = null,

    val pwmMax: Double? = null,
    val pwm95p: Double? = null,


    // Mosfet metrics
    val rMosfetHot: Double? = null,

    // Temperature metrics
    val tempBoardMax: Double? = null,
    val tempMotorMax: Double? = null
) {
    companion object {
        /**
         * Maps each [Metrics] entry to the extractor function that reads its
         * value from a [ReqStatsResult].  Entries with no corresponding field
         * (e.g. [Metrics.REQ_MEAN]) are omitted — callers should use
         * `extractors[metric]` and skip nulls.
         */
        val extractors: Map<Metrics, (ReqStatsResult) -> Double?> = mapOf(
            Metrics.I_MAX           to { it.iMax },
            Metrics.I_95P           to { it.i95p },
            Metrics.I_PHASE_MAX     to { it.iPhaseMax },
            Metrics.I_PHASE_95P     to { it.iPhase95p },
            Metrics.I_PHASE2_INT    to { it.iPhase2Int },
            Metrics.R_BATT_MEDIAN   to { it.rBattMedian },
            Metrics.REQ_MEDIAN      to { it.reqMedian },
            Metrics.REQ_MEDIAN_25C  to { it.reqMedian25C },
            Metrics.REQ_95P         to { it.req95p },
            Metrics.V_MIN_STRONG    to { it.vMinStrong },
            Metrics.R_BATT_MEDIAN_25C to { it.rBattMedian25C },
            Metrics.R_MOSFET_HOT   to { it.rMosfetHot },
            Metrics.SAG_95P         to { it.sag95p },
            Metrics.SAG_MAX         to { it.sagMax },
            Metrics.TEMP_BOARD_MAX  to { it.tempBoardMax },
            Metrics.TEMP_MOTOR_MAX  to { it.tempMotorMax },
            Metrics.PWM_95P         to { it.pwm95p },
            Metrics.PWM_MAX         to { it.pwmMax }
            // Metrics.REQ_MEAN intentionally absent — no field in ReqStatsResult
        )
    }
}
