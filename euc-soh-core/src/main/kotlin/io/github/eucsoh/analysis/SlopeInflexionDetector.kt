package io.github.eucsoh.analysis

import io.github.eucsoh.model.ThresholdInfo
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.abs

/**
 * Detects slope inflexions (sustained regime changes) using sliding window regression.
 * Port of detect_slope_inflexions() from soh_core_en.py
 */
object SlopeInflexionDetector {

    data class InflexionResult(
        val slowIdx: List<Int>,
        val inflexIdx: List<Int>,
        val localSlopes: Map<Int, Double>
    )

    /**
     * Classifies points as "slow regime" (green) or "sustained inflexion" (red)
     * based on local slope and fraction above danger threshold.
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
    ): InflexionResult {
        if (metric !in df.columnNames() || "wheel_km" !in df.columnNames()) {
            return InflexionResult(emptyList(), emptyList(), emptyMap())
        }

        val dfClean = df.filter {
            it[metric] != null && it["wheel_km"] != null &&
            !(it[metric] as Double).isNaN() && !(it["wheel_km"] as Double).isNaN()
        }.sortBy("wheel_km")

        if (dfClean.rowsCount() < 10) {
            return InflexionResult(
                slowIdx = (0 until dfClean.rowsCount()).toList(),
                inflexIdx = emptyList(),
                localSlopes = emptyMap()
            )
        }

        val x = dfClean["wheel_km"].values().filterIsInstance<Number>().map { it.toDouble() }
        val y = dfClean[metric].values().filterIsInstance<Number>().map { it.toDouble() }

        val spanKm = x.maxOrNull()!! - x.minOrNull()!!
        if (spanKm < minKmSpan) {
            return InflexionResult(
                slowIdx = (0 until dfClean.rowsCount()).toList(),
                inflexIdx = emptyList(),
                localSlopes = emptyMap()
            )
        }

        // Danger limit from thresholds
        val dangerLimit = thresholds?.get(metric)?.limit ?: run {
            val yMean = y.average()
            val yStd = kotlin.math.sqrt(y.map { (it - yMean) * (it - yMean) }.average())
            if (highIsBad) yMean + 1.25 * yStd else yMean - 1.25 * yStd
        }

        // Baseline slope from first third
        val kmCut = x.minOrNull()!! + spanKm / 3.0
        val baseIndices = x.indices.filter { x[it] <= kmCut }

        val slopeBase = if (baseIndices.size >= 5) {
            val xb = baseIndices.map { x[it] }
            val yb = baseIndices.map { y[it] }
            val n = xb.size
            val sumX = xb.sum()
            val sumY = yb.sum()
            val sumXY = xb.zip(yb).sumOf { it.first * it.second }
            val sumX2 = xb.sumOf { it * it }
            (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        } else {
            // Fallback to global
            val n = x.size
            val sumX = x.sum()
            val sumY = y.sum()
            val sumXY = x.zip(y).sumOf { it.first * it.second }
            val sumX2 = x.sumOf { it * it }
            (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        }

        // Compute local slopes and fraction above limit
        val slopesLocal = MutableList(y.size) { Double.NaN }
        val fracAboveLimit = DoubleArray(y.size)

        val halfW = windowKm / 2.0

        for (i in y.indices) {
            val kmI = x[i]
            val kmMin = kmI - halfW
            val kmMax = kmI + halfW
            val windowIndices = x.indices.filter { x[it] in kmMin..kmMax }

            if (windowIndices.size < 5) continue

            val xw = windowIndices.map { x[it] }
            val yw = windowIndices.map { y[it] }

            // Linear regression on window
            val n = xw.size
            val sumX = xw.sum()
            val sumY = yw.sum()
            val sumXY = xw.zip(yw).sumOf { it.first * it.second }
            val sumX2 = xw.sumOf { it * it }
            val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)

            slopesLocal[i] = slope
            fracAboveLimit[i] = yw.count { it > dangerLimit }.toDouble() / yw.size
        }

        // Classify points
        val slowIdx = mutableListOf<Int>()
        val inflexIdx = mutableListOf<Int>()
        val slopeThreshold = slopeBase * slopeFactor

        for (i in y.indices) {
            val sLoc = slopesLocal[i]
            val frac = fracAboveLimit[i]
            val val_i = y[i]

            if (sLoc.isNaN()) {
                // No slope computed, classify by value
                if (val_i > dangerLimit) {
                    inflexIdx.add(i)
                } else {
                    slowIdx.add(i)
                }
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

        val slopeMap = y.indices.associateWith { slopesLocal[it] }.filterValues { !it.isNaN() }

        return InflexionResult(slowIdx, inflexIdx, slopeMap)
    }
}
