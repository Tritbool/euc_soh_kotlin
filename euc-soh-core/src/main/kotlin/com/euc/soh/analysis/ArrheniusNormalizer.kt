// ArrheniusNormalizer.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogData
import kotlin.math.exp
import kotlin.math.ln

/**
 * Normalisation thermique Arrhenius pour batteries Li-ion
 */
object ArrheniusNormalizer {
    
    private const val R_GAS = 8.314 // J/(mol·K)
    private const val T_REF_C = 25.0 // °C
    private const val DEFAULT_EA = 20000.0 // J/mol
    
    /**
     * Normalise une résistance mesurée à 25°C via loi d'Arrhenius
     * 
     * @param rBattMeasured Résistance mesurée (Ω)
     * @param tempMeasuredC Température de mesure (°C)
     * @param eaJPerMol Énergie d'activation (J/mol)
     * @return Résistance normalisée à 25°C (Ω)
     */
    fun normalizeRBattTo25C(
        rBattMeasured: Double,
        tempMeasuredC: Double?,
        eaJPerMol: Double = DEFAULT_EA
    ): Double {
        // Cas invalides
        if (tempMeasuredC == null || tempMeasuredC.isNaN()) {
            return rBattMeasured
        }
        
        if (rBattMeasured.isNaN()) {
            return Double.NaN
        }
        
        // Conversion Kelvin
        val tRefK = T_REF_C + 273.15
        val tMeasK = tempMeasuredC + 273.15
        
        // Garde-fou température
        if (tMeasK < 263.15 || tMeasK > 353.15) {
            // Hors plage fiable (-10°C à 80°C)
            return rBattMeasured
        }
        
        // Calcul Arrhenius
        val exponent = (eaJPerMol / R_GAS) * (1.0 / tMeasK - 1.0 / tRefK)
        val clippedExponent = exponent.coerceIn(-100.0, 100.0)
        val factor = exp(clippedExponent)
        
        val rBatt25C = rBattMeasured / factor
        return maxOf(0.0, rBatt25C)
    }
    
    /**
     * Calibre automatiquement l'énergie d'activation E_a depuis les données
     * en ajustant une régression linéaire sur ln(R) vs 1/T
     * 
     * @param logs Liste des logs avec résistances et températures
     * @param metric Nom de la métrique à utiliser (ex: "Req_median")
     * @param tempMetric Nom de la métrique température (ex: "temp_board_max")
     * @param socMin SoC minimum pour filtrage (%)
     * @param socMax SoC maximum pour filtrage (%)
     * @return Énergie d'activation calibrée (J/mol)
     */
    fun calibrateEaFromLogs(
        logs: List<LogData>,
        metric: String = "Req_median",
        tempMetric: String = "temp_board_max",
        socMin: Double = 20.0,
        socMax: Double = 90.0
    ): Double {
        // Filtre des logs avec données valides
        val validLogs = logs.filter { log ->
            val r = log.getMetric(metric)
            val t = when (tempMetric) {
                "temp_board_max" -> log.tempBoardMax
                "temp_motor_max" -> log.tempMotorMax
                else -> null
            }
            r != null && !r.isNaN() && r > 0.0 && t != null && !t.isNaN()
        }
        
        if (validLogs.size < 5) {
            return DEFAULT_EA
        }
        
        // Extraction des températures
        val temperatures = validLogs.mapNotNull { log ->
            when (tempMetric) {
                "temp_board_max" -> log.tempBoardMax
                "temp_motor_max" -> log.tempMotorMax
                else -> null
            }
        }
        
        val tMin = temperatures.minOrNull() ?: return DEFAULT_EA
        val tMax = temperatures.maxOrNull() ?: return DEFAULT_EA
        val tSpan = tMax - tMin
        
        // Besoin d'au moins 5°C d'écart
        if (tSpan < 5.0) {
            return DEFAULT_EA
        }
        
        // Préparation données pour régression
        val rValues = validLogs.mapNotNull { it.getMetric(metric) }
            .filter { it > 0.0 }
            .toDoubleArray()
        
        val tCelsius = validLogs.mapNotNull {
            when (tempMetric) {
                "temp_board_max" -> it.tempBoardMax
                "temp_motor_max" -> it.tempMotorMax
                else -> null
            }
        }.toDoubleArray()
        
        if (rValues.size < 5) {
            return DEFAULT_EA
        }
        
        // Régression linéaire : ln(R) = A/T + B
        val lnR = rValues.map { ln(it) }.toDoubleArray()
        val invT = tCelsius.map { 1.0 / (it + 273.15) }.toDoubleArray()
        
        val (slope, _) = linearRegression(invT, lnR)
        
        // E_a = slope * R_gas
        val eaCalibrated = slope * R_GAS
        
        // Clipping raisonnable pour batteries Li-ion
        return eaCalibrated.coerceIn(10000.0, 50000.0)
    }
    
    /**
     * Régression linéaire simple
     * @return Pair(slope, intercept)
     */
    private fun linearRegression(x: DoubleArray, y: DoubleArray): Pair<Double, Double> {
        require(x.size == y.size && x.isNotEmpty()) { "Arrays must have same non-zero length" }
        
        val n = x.size
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { it.first * it.second }
        val sumX2 = x.sumOf { it * it }
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n
        
        return Pair(slope, intercept)
    }
    
    /**
     * Calcule le coefficient de détermination R²
     */
    fun calculateR2(x: DoubleArray, y: DoubleArray, slope: Double, intercept: Double): Double {
        val yMean = y.average()
        val yPredicted = x.map { slope * it + intercept }
        
        val ssRes = y.zip(yPredicted).sumOf { (yi, yPred) ->
            val diff = yi - yPred
            diff * diff
        }
        
        val ssTot = y.sumOf { yi ->
            val diff = yi - yMean
            diff * diff
        }
        
        return 1.0 - (ssRes / ssTot)
    }
}
