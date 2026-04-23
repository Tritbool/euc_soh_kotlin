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
import io.github.eucsoh.Constants.MetaColumns.WHEEL_KM
import io.github.eucsoh.model.ThresholdInfo
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import java.util.Collections
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Trend detection: linear regression and slope inflexions.
 * Port of detect_trend_linear() and detect_slope_inflexions().
 */
object TrendDetector {

    data class TrendResult(
        val slope: Double?,
        val pValue: Double?,
        val isSignificant: Boolean
    )

    data class SlopeInflexionResult(
        val slowIndices: List<Int>,
        val inflexionIndices: List<Int>,
        val localSlopes: Map<Int, Double>
    )

    // Utilitaire à mettre en top-level ou companion object
    private fun List<Double>.lowerBound(value: Double): Int {
        val r = this.binarySearch(value)
        return if (r >= 0) r else -(r + 1)
    }

    private fun List<Double>.upperBound(value: Double): Int {
        val r = this.binarySearch(value)
        return if (r >= 0) r + 1 else -(r + 1)
    }


    /**
     * Detects linear drift (slope > 0, p < 0.05).
     */
    fun detectTrendLinear(
        df: DataFrame<*>,
        metric: String,
        kmMinSpan: Double = 1000.0
    ): TrendResult {
        if (metric !in df.columnNames() || WHEEL_KM.csv_code !in df.columnNames()) {
            return TrendResult(null, null, false)
        }

        val dfClean = df.filter {
            it[metric] != null && it[WHEEL_KM.csv_code] != null &&
                    !(it[metric] as Double).isNaN() && !(it[WHEEL_KM.csv_code] as Double).isNaN()
        }.sortBy(WHEEL_KM.csv_code)

        if (dfClean.rowsCount() < 5) {
            return TrendResult(null, null, false)
        }

        val x = dfClean[WHEEL_KM.csv_code].values().map { (it as Number).toDouble() }
        val y = dfClean[metric].values().map { (it as Number).toDouble() }

        val spanKm = x.maxOrNull()!! - x.minOrNull()!!
        if (spanKm < kmMinSpan) {
            return TrendResult(null, null, false)
        }

        // Linear regression
        val n = x.size
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it * it }
        val sumY2 = y.sumOf { it * it }

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n

        val yPred = x.map { slope * it + intercept }
        val ssRes = y.zip(yPred).sumOf { (yi, ypi) -> (yi - ypi) * (yi - ypi) }
        val ssTot = y.sumOf { (it - y.average()) * (it - y.average()) }

        val rSquared = 1.0 - ssRes / ssTot

        // Approximate p-value (simplified)
        val tStat = slope / (kotlin.math.sqrt(ssRes / (n - 2)) / kotlin.math.sqrt(sumX2 - sumX * sumX / n))
        val pValue = 2.0 * (1.0 - studentTCDF(abs(tStat), n - 2))

        val isSignificant = abs(slope) > 0.0 && pValue < 0.05

        return TrendResult(slope, pValue, isSignificant)
    }

    /**
     * Classifies logs into slow regime vs sustained inflexion based on local slopes.
     */
    fun detectSlopeInflexions(
        df: DataFrame<*>,
        metric: String,
        thresholds: Map<String, ThresholdInfo>?,
        highIsBad: Boolean = true,
        windowKm: Double = 1500.0,
        minKmSpan: Double = 3000.0,
        slopeFactor: Double = 1.5,
        minFractionAboveLimit: Double = 0.6
    ): SlopeInflexionResult {
        if (metric !in df.columnNames() || WHEEL_KM.csv_code !in df.columnNames()) {
            return SlopeInflexionResult(emptyList(), emptyList(), emptyMap())
        }

        val dfClean = df.filter {
            it[metric] != null && it[WHEEL_KM.csv_code] != null &&
                    !(it[metric] as Double).isNaN() && !(it[WHEEL_KM.csv_code] as Double).isNaN()
        }.sortBy(WHEEL_KM.csv_code)

        if (dfClean.rowsCount() < 10) {
            return SlopeInflexionResult(
                slowIndices = (0 until dfClean.rowsCount()).toList(),
                inflexionIndices = emptyList(),
                localSlopes = emptyMap()
            )
        }

        val x = dfClean[WHEEL_KM.csv_code].values().map { (it as Number).toDouble() }
        val y = dfClean[metric].values().map { (it as Number).toDouble() }

        val spanKm = x.maxOrNull()!! - x.minOrNull()!!
        if (spanKm < minKmSpan) {
            return SlopeInflexionResult(
                slowIndices = (0 until dfClean.rowsCount()).toList(),
                inflexionIndices = emptyList(),
                localSlopes = emptyMap()
            )
        }

        val dangerLimit = thresholds?.get(metric)?.limit
            ?: (y.average() + 1.25 * kotlin.math.sqrt(y.sumOf { (it - y.average()) * (it - y.average()) } / (y.size - 1)))

        // Baseline slope (first third)
        val kmCut = x.minOrNull()!! + spanKm / 3.0
        val baseMask = x.map { it <= kmCut }
        val xBase = x.filterIndexed { i, _ -> baseMask[i] }
        val yBase = y.filterIndexed { i, _ -> baseMask[i] }

        val slopeBase = if (xBase.size >= 5) {
            val n = xBase.size
            val sumX = xBase.sum()
            val sumY = yBase.sum()
            val sumXY = xBase.zip(yBase).sumOf { it.first * it.second }
            val sumX2 = xBase.sumOf { it * it }
            (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        } else {
            0.0
        }

        // Local slopes
        val halfW = windowKm / 2.0
        val slopesLocal = mutableMapOf<Int, Double>()
        val fracAboveLimit = mutableMapOf<Int, Double>()

        for (i in x.indices) {
            val kmI = x[i]
            //val mask = x.map { it >= kmI - halfW && it <= kmI + halfW }
            //val xWindow = x.filterIndexed { idx, _ -> mask[idx] }
            //val yWindow = y.filterIndexed { idx, _ -> mask[idx] }
            val lo = x.lowerBound(kmI - halfW)
            val hi = x.upperBound(kmI + halfW)
            val xWindow = x.subList(lo, hi)
            val yWindow = y.subList(lo, hi)
            if (xWindow.size < 5) continue

            val n = xWindow.size
            val sumX = xWindow.sum()
            val sumY = yWindow.sum()
            val sumXY = xWindow.zip(yWindow).sumOf { it.first * it.second }
            val sumX2 = xWindow.sumOf { it * it }

            val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
            slopesLocal[i] = slope

            val frac = yWindow.count { it > dangerLimit }.toDouble() / yWindow.size
            fracAboveLimit[i] = frac
        }

        val slopeThreshold = slopeBase * slopeFactor

        val slowIdx = mutableListOf<Int>()
        val inflexIdx = mutableListOf<Int>()

        for (i in y.indices) {
            val value = y[i]
            val sLoc = slopesLocal[i]
            val frac = fracAboveLimit[i] ?: 0.0

            if (sLoc == null) {
                if (value > dangerLimit) inflexIdx.add(i) else slowIdx.add(i)
                continue
            }

            val isInflexion = if (highIsBad) {
                sLoc > slopeThreshold && frac >= minFractionAboveLimit
            } else {
                sLoc <= slopeThreshold && frac < minFractionAboveLimit
            }

            if (isInflexion) {
                inflexIdx.add(i)
            } else {
                slowIdx.add(i)
            }
        }

        return SlopeInflexionResult(slowIdx, inflexIdx, slopesLocal)
    }

    // Simplified Student's t CDF (approximation)
    private fun studentTCDF(t: Double, df: Int): Double {
        // Very rough approximation for p-value calculation
        // For production, use Apache Commons Math or similar
        return when {
            t < 0 -> 0.0
            t > 10 -> 1.0
            else -> 0.5 + 0.05 * t // Crude placeholder
        }
    }
}
