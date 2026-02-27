package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.model.Alarm
import io.github.eucsoh.model.ThresholdInfo
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.sqrt

/**
 * Gaussian-based alarm detection for SoH metrics.
 * Port of detect_alarms_gauss() from soh_core_en.py
 */
object GaussianAlarmDetector {

    private val METRICS_TO_CHECK = listOf(
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

    private val METRIC_DIRECTIONS = mapOf(
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
     * Computes Gaussian thresholds for multiple metrics.
     * Uses optimal fraction of best logs to define "healthy" baseline.
     */
    fun computeThresholds(
        df: DataFrame<*>,
        optimalFrac: Double = 0.5,
        nSigma: Double = 2.0,
        useBattMetric: Boolean = false
    ): Map<String, ThresholdInfo> {
        if ("Req_median" !in df.columnNames()) {
            return emptyMap()
        }

        // Sort by Req_median to select optimal logs
        val dfSorted = df.sortBy("Req_median")
        val nOpt = maxOf(3, (dfSorted.rowsCount() * optimalFrac).toInt())
        val dfOpt = dfSorted.take(nOpt)

        val thresholds = mutableMapOf<String, ThresholdInfo>()

        for (metric in METRICS_TO_CHECK) {
            if (metric !in df.columnNames()) continue

            val values = dfOpt[metric].values()
                .filterIsInstance<Number>()
                .map { it.toDouble() }
                .filter { !it.isNaN() }

            if (values.size < 3) continue

            val mean = values.average()
            val std = sqrt(values.map { (it - mean) * (it - mean) }.average())

            val direction = METRIC_DIRECTIONS[metric] ?: "higher_is_bad"
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
     * Detects alarms by comparing each log against computed thresholds.
     * 
     * @param checkAbsoluteLimit also check absolute Req_median limit
     * @param rPackNominal nominal pack resistance for absolute threshold
     */
    fun detectAlarms(
        df: DataFrame<*>,
        thresholds: Map<String, ThresholdInfo>,
        checkAbsoluteLimit: Boolean = true,
        rPackNominal: Double? = null
    ): List<Alarm> {
        val alarms = mutableListOf<Alarm>()

        // Per-log threshold checks
        for (rowIdx in 0 until df.rowsCount()) {
            val reasons = mutableListOf<String>()

            for ((metric, info) in thresholds) {
                if (metric !in df.columnNames()) continue

                val value = df[metric][rowIdx] as? Number ?: continue
                val v = value.toDouble()
                if (v.isNaN()) continue

                val bad = if (info.direction == "higher_is_bad") {
                    v > info.limit
                } else {
                    v < info.limit
                }

                if (bad) {
                    reasons.add(
                        "$metric (${info.direction}) limit=${"%.3f".format(info.limit)}, " +
                                "val=${"%.3f".format(v)}, µ=${"%.3f".format(info.mean)}, σ=${"%.3f".format(info.std)}"
                    )
                }
            }

            if (reasons.isNotEmpty()) {
                val file = df["file"][rowIdx]?.toString() ?: "unknown"
                val wheelKm = (df["wheel_km"][rowIdx] as? Number)?.toDouble()
                val datetime = df["datetime_first"][rowIdx]?.toString()

                alarms.add(
                    Alarm(
                        file = file,
                        wheelKm = wheelKm,
                        datetimeFirst = datetime,
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

            for (rowIdx in 0 until df.rowsCount()) {
                val req = (df["Req_median"][rowIdx] as? Number)?.toDouble() ?: continue
                val km = (df["wheel_km"][rowIdx] as? Number)?.toDouble() ?: continue

                if (req > absLimit && km >= Constants.ABS_KM_LIMIT) {
                    val file = df["file"][rowIdx]?.toString() ?: "unknown"
                    val datetime = df["datetime_first"][rowIdx]?.toString()

                    val factorStr = if (rPackNominal != null) {
                        "≈ ${"%.1f".format(absLimit / rPackNominal)}×R_pack_nom=${"%.3f".format(rPackNominal)} Ω"
                    } else {
                        ""
                    }

                    alarms.add(
                        Alarm(
                            file = file,
                            wheelKm = km,
                            datetimeFirst = datetime,
                            reasons = "Absolute high Req_median: ${"%.3f".format(req)} Ω " +
                                    "(> ${"%.3f".format(absLimit)} Ω $factorStr) at ${km.toInt()} km"
                        )
                    )
                }
            }
        }

        return alarms
    }
}
