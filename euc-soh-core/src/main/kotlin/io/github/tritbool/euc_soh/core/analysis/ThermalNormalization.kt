package io.github.tritbool.euc_soh.core.analysis

import kotlin.math.exp
import kotlin.math.max

/**
 * Normalizes battery resistance to 25°C using Arrhenius law for Li-ion batteries.
 *
 * @param rBattMeasured Measured battery resistance at temp_measured_c
 * @param tempMeasuredC Temperature at which resistance was measured (°C)
 * @param eaJPerMol Activation energy (J/mol), default 20 kJ/mol
 * @return Resistance normalized to 25°C
 */
fun normalizeRBattTo25C(
    rBattMeasured: Double?,
    tempMeasuredC: Double?,
    eaJPerMol: Double = 20000.0
): Double {
    if (rBattMeasured == null || tempMeasuredC == null) {
        return rBattMeasured ?: 0.0
    }
    if (rBattMeasured.isNaN() || tempMeasuredC.isNaN()) {
        return Double.NaN
    }

    val R_GAS = 8.314 // J/(mol·K)
    val T_REF_K = 25.0 + 273.15 // Kelvin
    val T_MEAS_K = tempMeasuredC + 273.15

    // Safety check: temperature range [263K, 353K] = [-10°C, 80°C]
    if (T_MEAS_K < 263.15 || T_MEAS_K > 353.15) {
        // Outside reliable range, but continue
    }

    val exponent = (eaJPerMol / R_GAS) * (1.0 / T_MEAS_K - 1.0 / T_REF_K)
    val clampedExp = exponent.coerceIn(-100.0, 100.0)
    val factor = exp(clampedExp)

    val rBatt25C = rBattMeasured / factor
    return max(0.0, rBatt25C)
}

/**
 * Auto-calibrates activation energy E_a from resistance vs temperature data.
 * Fits Arrhenius law: ln(R) = (E_a/R_gas) * (1/T) + const
 *
 * @param resistances Array of resistance values
 * @param temperatures Array of temperatures (°C) corresponding to resistances
 * @return Calibrated E_a in J/mol, clamped to [10, 50] kJ/mol
 */
fun calibrateEaFromData(
    resistances: DoubleArray,
    temperatures: DoubleArray
): Double {
    val DEFAULT_EA = 20000.0 // 20 kJ/mol
    val R_GAS = 8.314

    if (resistances.size < 5 || temperatures.size != resistances.size) {
        return DEFAULT_EA
    }

    val tMin = temperatures.minOrNull() ?: return DEFAULT_EA
    val tMax = temperatures.maxOrNull() ?: return DEFAULT_EA
    if (tMax - tMin < 5.0) {
        // Temperature span too narrow
        return DEFAULT_EA
    }

    // Filter valid points (R > 0)
    val validPairs = resistances.zip(temperatures).filter { (r, _) -> r > 0.0 }
    if (validPairs.size < 5) {
        return DEFAULT_EA
    }

    val lnR = validPairs.map { kotlin.math.ln(it.first) }.toDoubleArray()
    val invT = validPairs.map { 1.0 / (it.second + 273.15) }.toDoubleArray()

    // Linear regression: ln(R) = slope * (1/T) + intercept
    val n = lnR.size
    val sumX = invT.sum()
    val sumY = lnR.sum()
    val sumXY = invT.zip(lnR).sumOf { it.first * it.second }
    val sumX2 = invT.sumOf { it * it }

    val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
    val eaCalibrated = slope * R_GAS

    return eaCalibrated.coerceIn(10000.0, 50000.0)
}
