// GaussianAlarmDetector.kt
package com.euc.soh.analysis

import com.euc.soh.model.Alarm
import com.euc.soh.model.AlarmDirection
import com.euc.soh.model.LogData
import com.euc.soh.model.Threshold
import com.euc.soh.config.Constants

/**
 * Détection d'alarmes via méthode gaussienne
 */
object GaussianAlarmDetector {
    
    /**
     * Calcule les seuils gaussiens pour toutes les métriques
     * 
     * @param logs Liste des logs
     * @param optimalFrac Fraction des meilleurs logs pour référence (0.0-1.0)
     * @param nSigma Nombre d'écarts-types pour seuil danger
     * @param useBattMetric Inclure métriques batterie séparées
     * @return Map métrique -> seuil
     */
    fun computeThresholds(
        logs: List<LogData>,
        optimalFrac: Double = 0.5,
        nSigma: Double = 2.0,
        useBattMetric: Boolean = false
    ): Map<String, Threshold> {
        // Tri par Req_median pour identifier les meilleurs logs
        val sortedByReq = logs.sortedBy { it.reqMedian }
        val nOptimal = maxOf(3, (sortedByReq.size * optimalFrac).toInt())
        val optimalLogs = sortedByReq.take(nOptimal)
        
        // Métriques à surveiller
        val metrics = mutableListOf(
            "Req_median",
            "Req_95p",
            "sag_95p",
            "sag_max",
            "temp_board_max",
            "temp_motor_max",
            "v_min_strong"
        )
        
        if (useBattMetric) {
            metrics.add("R_batt_median")
            metrics.add("R_mosfet_hot")
        }
        
        // Ajout métriques courant de phase si disponibles
        if (logs.any { it.iPhase2Int != null }) metrics.add("I_phase2_int")
        if (logs.any { it.iPhaseMax != null }) metrics.add("i_phase_max")
        if (logs.any { it.iPhase95p != null }) metrics.add("i_phase_95p")
        
        // Directions (plus haut = plus mauvais, sauf v_min_strong)
        val directions = mapOf(
            "Req_median" to AlarmDirection.HIGHER_IS_BAD,
            "Req_95p" to AlarmDirection.HIGHER_IS_BAD,
            "sag_95p" to AlarmDirection.HIGHER_IS_BAD,
            "sag_max" to AlarmDirection.HIGHER_IS_BAD,
            "temp_board_max" to AlarmDirection.HIGHER_IS_BAD,
            "temp_motor_max" to AlarmDirection.HIGHER_IS_BAD,
            "R_batt_median" to AlarmDirection.HIGHER_IS_BAD,
            "R_mosfet_hot" to AlarmDirection.HIGHER_IS_BAD,
            "v_min_strong" to AlarmDirection.LOWER_IS_BAD,
            "I_phase2_int" to AlarmDirection.HIGHER_IS_BAD,
            "i_phase_max" to AlarmDirection.HIGHER_IS_BAD,
            "i_phase_95p" to AlarmDirection.HIGHER_IS_BAD
        )
        
        val thresholds = mutableMapOf<String, Threshold>()
        
        for (metric in metrics) {
            val values = optimalLogs.mapNotNull { it.getMetric(metric) }
                .filter { !it.isNaN() }
            
            if (values.size < 3) continue
            
            val mean = values.average()
            val std = values.standardDeviation()
            
            val direction = directions[metric] ?: AlarmDirection.HIGHER_IS_BAD
            val limit = when (direction) {
                AlarmDirection.HIGHER_IS_BAD -> mean + nSigma * std
                AlarmDirection.LOWER_IS_BAD -> mean - nSigma * std
            }
            
            thresholds[metric] = Threshold(mean, std, limit, direction)
        }
        
        return thresholds
    }
    
    /**
     * Détecte les alarmes sur les logs
     * 
     * @param logs Liste des logs à vérifier
     * @param thresholds Seuils précalculés
     * @param checkAbsoluteLimit Vérifier limite absolue Req
     * @param absReqLimit Limite absolue Req (Ω)
     * @param absKmLimit Kilométrage minimum pour alarme absolue
     * @param rPackNominal Résistance pack nominale (optionnelle) pour calculer abs limit
     * @return Liste des alarmes
     */
    fun detectAlarms(
        logs: List<LogData>,
        thresholds: Map<String, Threshold>,
        checkAbsoluteLimit: Boolean = true,
        absReqLimit: Double = Constants.ABS_REQ_LIMIT,
        absKmLimit: Double = Constants.ABS_KM_LIMIT,
        rPackNominal: Double? = null
    ): List<Alarm> {
        val alarms = mutableListOf<Alarm>()
        
        // Alarmes gaussiennes
        for (log in logs) {
            val reasons = mutableListOf<String>()
            
            for ((metric, threshold) in thresholds) {
                val value = log.getMetric(metric) ?: continue
                if (value.isNaN()) continue
                
                val isBad = when (threshold.direction) {
                    AlarmDirection.HIGHER_IS_BAD -> value > threshold.limit
                    AlarmDirection.LOWER_IS_BAD -> value < threshold.limit
                }
                
                if (isBad) {
                    reasons.add(
                        "$metric (${threshold.direction}) " +
                        "limite=${String.format("%.3f", threshold.limit)}, " +
                        "val=${String.format("%.3f", value)}, " +
                        "µ=${String.format("%.3f", threshold.mean)}, " +
                        "σ=${String.format("%.3f", threshold.std)}"
                    )
                }
            }
            
            if (reasons.isNotEmpty()) {
                alarms.add(
                    Alarm(
                        fileName = log.fileName,
                        wheelKm = log.wheelKm,
                        datetimeFirst = log.datetimeFirst,
                        reasons = reasons.joinToString("; ")
                    )
                )
            }
        }
        
        // Alarme absolue Req
        if (checkAbsoluteLimit) {
            // if rPackNominal is provided, compute abs limit from it (like Python)
            val computedAbsLimit = rPackNominal?.let { it * Constants.ABS_REQ_FACTOR } ?: absReqLimit

            for (log in logs) {
                val req = log.reqMedian
                val km = log.wheelKm ?: continue
                
                if (req > computedAbsLimit && km >= absKmLimit) {
                    alarms.add(
                        Alarm(
                            fileName = log.fileName,
                            wheelKm = km,
                            datetimeFirst = log.datetimeFirst,
                            reasons = "Req_median absolu élevé: ${String.format("%.3f", req)} Ω " +
                                     "(> ${String.format("%.3f", computedAbsLimit)} Ω) à ${km.toInt()} km"
                        )
                    )
                }
            }
        }
        
        return alarms
    }
    
    /**
     * Calcule bande gaussienne pour une métrique
     * 
     * @param logs Logs de référence
     * @param metric Nom de la métrique
     * @param optimalFrac Fraction pour référence
     * @param nSigmaBand Nb d'écarts-types pour bande "saine"
     * @return Quadruple(mean, std, bandLow, bandHigh)
     */
    fun computeBand(
        logs: List<LogData>,
        metric: String,
        optimalFrac: Double = 0.5,
        nSigmaBand: Double = 1.0
    ): GaussianBand? {
        val sorted = logs.sortedBy { it.getMetric(metric) ?: Double.MAX_VALUE }
        val nOptimal = maxOf(3, (sorted.size * optimalFrac).toInt())
        val optimal = sorted.take(nOptimal)
        
        val values = optimal.mapNotNull { it.getMetric(metric) }
            .filter { !it.isNaN() }
        
        if (values.size < 3) return null
        
        val mean = values.average()
        val std = values.standardDeviation()
        
        return GaussianBand(
            mean = mean,
            std = std,
            bandLow = mean - nSigmaBand * std,
            bandHigh = mean + nSigmaBand * std
        )
    }
}

/**
 * Bande gaussienne
 */
data class GaussianBand(
    val mean: Double,
    val std: Double,
    val bandLow: Double,
    val bandHigh: Double
)

/**
 * Extension écart-type
 */
private fun List<Double>.standardDeviation(): Double {
    if (size < 2) return 0.0
    val mean = average()
    val variance = map { (it - mean) * (it - mean) }.average()
    return kotlin.math.sqrt(variance)
}
