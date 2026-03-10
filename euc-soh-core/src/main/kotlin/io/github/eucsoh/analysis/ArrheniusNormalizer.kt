package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Arrhenius thermal normalization for Li-ion battery resistance.
 */
object ArrheniusNormalizer {

    private const val R_GAS = 8.314 // J/(mol·K)
    private const val T_REF_C = 25.0
    private const val T_REF_K = T_REF_C + 273.15
    private const val DEFAULT_EA = 20000.0 // 20 kJ/mol

    /**
     * Normalizes battery resistance to 25°C using Arrhenius law.
     * R_25C = R_measured / exp((Ea/R_gas) * (1/T_meas - 1/T_ref))
     */
    fun normalizeRBattTo25C(
        rBattMeasured: Double?,
        tempMeasuredC: Double?,
        eaJPerMol: Double = DEFAULT_EA
    ): Double {
        if (rBattMeasured == null || tempMeasuredC == null) {
            return rBattMeasured ?: 0.0
        }
        if (rBattMeasured.isNaN() || tempMeasuredC.isNaN()) {
            return Double.NaN
        }

        val tMeasK = tempMeasuredC + 273.15

        // Safety check
        if (tMeasK < 263.15 || tMeasK > 353.15) {
            if (Constants.DEBUG) {
                println("[WARNING] normalize: T=$tempMeasuredC°C outside reliable range")
            }
        }

        val exponent = (eaJPerMol / R_GAS) * (1.0 / tMeasK - 1.0 / T_REF_K)
        val clampedExp = exponent.coerceIn(-100.0, 100.0)
        val factor = exp(clampedExp)

        val rBatt25C = rBattMeasured / factor
        return max(0.0, rBatt25C)
    }

    /**
     * Auto-calibrates Ea from DataFrame with metric + temperature columns.
     * Fits ln(R) = (Ea/R_gas) * (1/T) + const
     */
    fun calibrateEaFromDataFrame(
        df: DataFrame<*>,
        metric: String = "Req_median",
        tempCol: String = "temp_board_max"
    ): Double {
        if (metric !in df.columnNames() || tempCol !in df.columnNames()) {
            return DEFAULT_EA
        }

        val dfClean = df
            .filter { it[metric] != null && it[tempCol] != null }
            .filter { !(it[metric] as Double).isNaN() && !(it[tempCol] as Double).isNaN() }

        if (dfClean.rowsCount() < 5) {
            if (Constants.DEBUG) {
                println("[calibrateEa] Insufficient points, using default Ea=${DEFAULT_EA / 1000}kJ/mol")
            }
            return DEFAULT_EA
        }

        val temps = dfClean[tempCol].values().map { (it as Number).toDouble() }
        val tMin = temps.minOrNull() ?: return DEFAULT_EA
        val tMax = temps.maxOrNull() ?: return DEFAULT_EA
        val tSpan = tMax - tMin

        if (tSpan < 5.0) {
            if (Constants.DEBUG) {
                println("[calibrateEa] Temperature span too narrow ($tSpan°C < 5°C), using default")
            }
            return DEFAULT_EA
        }

        val rValues = dfClean[metric].values().map { (it as Number).toDouble() }.filter { it > 0.0 }
        val tValues = temps.filter { it > 0.0 }

        if (rValues.size < 5 || rValues.size != tValues.size) {
            return DEFAULT_EA
        }

        val lnR = rValues.map { ln(it) }
        val invT = tValues.map { 1.0 / (it + 273.15) }

        // Linear regression: ln(R) = slope * (1/T) + intercept
        val n = lnR.size
        val sumX = invT.sum()
        val sumY = lnR.sum()
        val sumXY = invT.zip(lnR).sumOf { it.first * it.second }
        val sumX2 = invT.sumOf { it * it }

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val eaCalibrated = slope * R_GAS

        val eaClamped = eaCalibrated.coerceIn(DEFAULT_EA, 50000.0)

        if (Constants.DEBUG) {
            println("[calibrateEa] Calibrated Ea=${eaClamped / 1000}kJ/mol (T range: $tMin–$tMax°C, n=$n)")
        }

        return eaClamped
    }
}
