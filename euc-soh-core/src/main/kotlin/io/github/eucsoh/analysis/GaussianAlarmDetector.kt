/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.Constants.HIGHER_IS_BAD
import io.github.eucsoh.Constants.LOWER_IS_BAD
import io.github.eucsoh.Constants.Metrics.*
import io.github.eucsoh.Constants.MetaColumns.*
import io.github.eucsoh.Logger
import io.github.eucsoh.NoOpLogger
import io.github.eucsoh.model.ThresholdInfo
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
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
    private val TAG:String="GaussianAlarmDetector"
    private val METRICS = listOf(
        REQ_MEDIAN,
        REQ_95P,
        SAG_95P,
        SAG_MAX,
        TEMP_BOARD_MAX,
        TEMP_MOTOR_MAX,
        V_MIN_STRONG,
        R_BATT_MEDIAN,
        R_MOSFET_HOT,
        I_PHASE2_INT,
        I_PHASE_MAX,
        I_PHASE_95P
    )

    /**
     * Computes thresholds for all available metrics.
     */
    fun computeThresholds(
        df: DataFrame<*>,
        optimalFrac: Double = 0.5,
        nSigma: Double = 2.0,
        useBattMetric: Boolean = false,
        logger: Logger = NoOpLogger
    ): Map<String, ThresholdInfo> {
        val thresholds = mutableMapOf<String, ThresholdInfo>()
        for (i in 0 until minOf(5, df.rowsCount())) {
            logger.d(TAG, "raw[$i] Req_median=${df[REQ_MEDIAN.csv_code][i]}")
        }
        // Sort by Req_median to pick optimal logs
        val dfSorted = df.sortBy(REQ_MEDIAN.csv_code)
        val nOpt = maxOf(3, (dfSorted.rowsCount() * optimalFrac).toInt())
        val dfOpt = dfSorted.take(nOpt)
        logger.d(TAG, "total=${df.rowsCount()}, nOpt=$nOpt")
        for (metricInfo in METRICS) {
            val metric = metricInfo.csv_code
            val direction = metricInfo.higher_is_bad
            if (metric !in df.columnNames()) continue

            val vals = dfOpt[metric].values()
                .filterIsInstance<Number>()
                .map { it.toDouble() }
                .filter { !it.isNaN() }

            if (vals.size < 3) continue

            val mean = vals.average()
            // Bessel corrected std dev.
            val std = kotlin.math.sqrt(vals.sumOf { (it - mean).pow(2) } / (vals.size - 1))

            val limit = if (direction) {
                mean + nSigma * std
            } else {
                mean - nSigma * std
            }
            logger.d(
                TAG,
                "$metric: vals=${vals.size}, mean=$mean, std=$std, limit=$limit"
            )
            thresholds[metric] = ThresholdInfo(
                mean = mean,
                std = std,
                limit = limit,
                direction = if(direction) HIGHER_IS_BAD else LOWER_IS_BAD
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

                val bad = if (info.direction == HIGHER_IS_BAD) {
                    value > info.limit
                } else {
                    value < info.limit
                }

                if (bad) {
                    reasons.add(
                        "$metric (${info.direction}) limit=${"%.3f".format(info.limit)}, " +
                                "val=${"%.3f".format(value)}, µ=${"%.3f".format(info.mean)}, σ=${
                                    "%.3f".format(
                                        info.std
                                    )
                                }"
                    )
                }
            }

            if (reasons.isNotEmpty()) {
                alarms.add(
                    Alarm(
                        file = (df[FILE.csv_code][i] as? String) ?: "",
                        wheelKm = (df[WHEEL_KM.csv_code][i] as? Number)?.toDouble(),
                        datetimeFirst = df[DATETIME_FIRST.csv_code][i] as? String,
                        reasons = reasons.joinToString("; ")
                    )
                )
            }
        }

        // Absolute Req limit check
        if (checkAbsoluteLimit && REQ_MEDIAN.csv_code in df.columnNames() && WHEEL_KM.csv_code in df.columnNames()) {
            val absLimit = if (rPackNominal != null) {
                rPackNominal * Constants.ABS_REQ_FACTOR
            } else {
                Constants.ABS_REQ_LIMIT
            }

            for (i in 0 until df.rowsCount()) {
                val req = (df[REQ_MEDIAN.csv_code][i] as? Number)?.toDouble() ?: continue
                val km = (df[WHEEL_KM.csv_code][i] as? Number)?.toDouble() ?: continue

                if (req > absLimit && km >= Constants.ABS_KM_LIMIT) {
                    val factor = if (rPackNominal != null) req / rPackNominal else 0.0
                    alarms.add(
                        Alarm(
                            file = (df[FILE.csv_code][i] as? String) ?: "",
                            wheelKm = km,
                            datetimeFirst = df[DATETIME_FIRST.csv_code][i] as? String,
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
