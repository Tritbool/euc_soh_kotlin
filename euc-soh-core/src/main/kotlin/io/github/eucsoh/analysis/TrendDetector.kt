package io.github.eucsoh.analysis

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.sqrt

/**
 * Linear trend detection using simple regression.
 * Port of detect_trend_linear() from soh_core_en.py
 */
object TrendDetector {

    data class TrendResult(
        val slope: Double,
        val pValue: Double,
        val isSignificant: Boolean
    )

    /**
     * Detects linear drift of metric vs wheel_km.
     * 
     * @param metric column to analyze
     * @param kmMinSpan minimum km span required
     * @return TrendResult or null if insufficient data
     */
    fun detectTrendLinear(
        df: DataFrame<*>,
        metric: String,
        kmMinSpan: Double = 1000.0
    ): TrendResult? {
        if (metric !in df.columnNames() || "wheel_km" !in df.columnNames()) {
            return null
        }

        val dfClean = df.filter {
            it[metric] != null && it["wheel_km"] != null &&
            !(it[metric] as Double).isNaN() && !(it["wheel_km"] as Double).isNaN()
        }.sortBy("wheel_km")

        if (dfClean.rowsCount() < 5) return null

        val x = dfClean["wheel_km"].values().filterIsInstance<Number>().map { it.toDouble() }
        val y = dfClean[metric].values().filterIsInstance<Number>().map { it.toDouble() }

        val spanKm = x.maxOrNull()!! - x.minOrNull()!!
        if (spanKm < kmMinSpan) return null

        // Linear regression: y = slope * x + intercept
        val n = x.size
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it * it }
        val sumY2 = y.sumOf { it * it }

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n

        // R-squared and p-value estimation
        val yMean = sumY / n
        val ssTotal = sumY2 - n * yMean * yMean
        val yPred = x.map { slope * it + intercept }
        val ssRes = y.zip(yPred).sumOf { (yi, ypi) -> (yi - ypi) * (yi - ypi) }
        val rSquared = 1.0 - ssRes / ssTotal

        // Simple t-test for slope significance
        val se = sqrt(ssRes / (n - 2)) / sqrt(sumX2 - sumX * sumX / n)
        val tStat = slope / se

        // Very rough p-value approximation (proper implementation would use t-distribution)
        val pValue = if (tStat > 2.0) 0.05 else if (tStat > 3.0) 0.01 else 0.1

        val isSignificant = slope > 0 && pValue < 0.05

        return TrendResult(slope, pValue, isSignificant)
    }
}
