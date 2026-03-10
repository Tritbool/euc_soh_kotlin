package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.model.ThresholdInfo
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.abs
import kotlin.math.pow

/**
 * Gaussian-based alarm detection for SoH metrics.
 * Port of detect_alarms_gauss() from soh_core_en.py.
 */
object GaussianAlarmDetector {

    data class Alarm(
        val file: String,
        val wheelKm: Double?,
        val datetimeFirst: String?,
        val reasons: String
    )

    private val METRICS = listOf(
        "Req_median",
        "Req_95p",
        "sag_95p",
        "sag_max",
        "temp_board_max",
        "temp_motor_max",
        "v_min_strong",
        "R_batt_median",
        "R_mosfet_hot",
        "I_phase2_int",
        "i_phase_max",
        "i_phase_95p"
    )

    private val DIRECTION = mapOf(
        "Req_median" to "higher_is_bad",
        "Req_95p" to "higher_is_bad",
        "sag_95p" to "higher_is_bad",
        "sag_max" to "higher_is_bad",
        "temp_board_max" to "higher_is_bad",
        "temp_motor_max" to "higher_is_bad",
        "R_batt_median" to "higher_is_bad",
        "R_mosfet_hot" to "higher_is_bad",
        "v_min_strong" to "lower_is_bad",
        "I_phase2_int" to "higher_is_bad",
        "i_phase_max" to "higher_is_bad",
        "i_phase_95p" to "higher_is_bad"
    )

    /**
     * Computes thresholds for all available metrics.
     */
    fun computeThresholds(
        df: DataFrame<*>,
        optimalFrac: Double = 0.5,
        nSigma: Double = 2.0,
        useBattMetric: Boolean = false
    ): Map<String, ThresholdInfo> {
        val thresholds = mutableMapOf<String, ThresholdInfo>()

        // Sort by Req_median to pick optimal logs
        val dfSorted = df.sortBy("Req_median")
        val nOpt = maxOf(3, (dfSorted.rowsCount() * optimalFrac).toInt())
        val dfOpt = dfSorted.take(nOpt)

        for (metric in METRICS) {
            if (metric !in df.columnNames()) continue

            val vals = dfOpt[metric].values()
                .filterIsInstance<Number>()
                .map { it.toDouble() }
                .filter { !it.isNaN() }

            if (vals.size < 3) continue

            val mean = vals.average()
            // Bessel corrected std dev.
            val std = kotlin.math.sqrt(vals.sumOf { (it - mean).pow(2) } / (vals.size - 1))

            val direction = DIRECTION[metric] ?: "higher_is_bad"
            val limit = if (direction == "higher_is_bad") {
                mean + nSigma * std
            } else {
                mean - nSigma * std
            }

            thresholds[metric] = ThresholdInfo(
                mean = mean,
                std = std,
                limit = limit,
                direction = direction
            )
        }

        return thresholds
    }

    /**
     * Detects alarms based on thresholds.
     */
    fun detectAlarms(
        df: DataFrame<*>,
        thresholds: Map<String, ThresholdInfo>,
        checkAbsoluteLimit: Boolean = true,
        rPackNominal: Double? = null
    ): List<Alarm> {
        val alarms = mutableListOf<Alarm>()

        // Per-log Gaussian alarms
        for (i in 0 until df.rowsCount()) {
            val reasons = mutableListOf<String>()

            for ((metric, info) in thresholds) {
                if (metric !in df.columnNames()) continue

                val value = (df[metric][i] as? Number)?.toDouble() ?: continue
                if (value.isNaN()) continue

                val bad = if (info.direction == "higher_is_bad") {
                    value > info.limit
                } else {
                    value < info.limit
                }

                if (bad) {
                    reasons.add(
                        "$metric (${info.direction}) limit=${"%.3f".format(info.limit)}, " +
                                "val=${"%.3f".format(value)}, µ=${"%.3f".format(info.mean)}, σ=${"%.3f".format(info.std)}"
                    )
                }
            }

            if (reasons.isNotEmpty()) {
                alarms.add(
                    Alarm(
                        file = (df["file"][i] as? String) ?: "",
                        wheelKm = (df["wheel_km"][i] as? Number)?.toDouble(),
                        datetimeFirst = df["datetime_first"][i] as? String,
                        reasons = reasons.joinToString("; ")
                    )
                )
            }
        }

        // Absolute Req limit check
        if (checkAbsoluteLimit && "Req_median" in df.columnNames() && "wheel_km" in df.columnNames()) {
            val absLimit = if (rPackNominal != null) {
                rPackNominal * Constants.ABS_REQ_FACTOR
            } else {
                Constants.ABS_REQ_LIMIT
            }

            for (i in 0 until df.rowsCount()) {
                val req = (df["Req_median"][i] as? Number)?.toDouble() ?: continue
                val km = (df["wheel_km"][i] as? Number)?.toDouble() ?: continue

                if (req > absLimit && km >= Constants.ABS_KM_LIMIT) {
                    val factor = if (rPackNominal != null) req / rPackNominal else 0.0
                    alarms.add(
                        Alarm(
                            file = (df["file"][i] as? String) ?: "",
                            wheelKm = km,
                            datetimeFirst = df["datetime_first"][i] as? String,
                            reasons = "Absolute high Req_median: ${"%.3f".format(req)}Ω " +
                                    "(> ${"%.3f".format(absLimit)}Ω ≈ ${"%.1f".format(factor)}×R_pack_nom) " +
                                    "at ${"%.0f".format(km)} km"
                        )
                    )
                }
            }
        }

        return alarms
    }
}
