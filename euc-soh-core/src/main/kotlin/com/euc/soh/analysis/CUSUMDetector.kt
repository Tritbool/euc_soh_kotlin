// CUSUMDetector.kt
package com.euc.soh.analysis

import com.euc.soh.model.CUSUMResult
import com.euc.soh.model.LogData
import kotlin.math.abs

/**
 * Détecteur CUSUM (Cumulative Sum) pour changements de régime
 */
object CUSUMDetector {
    
    /**
     * Détecte les changements de régime via CUSUM unilatéral
     * 
     * @param logs Liste des logs triés par kilométrage
     * @param metric Nom de la métrique à surveiller
     * @param refKmMax Kilométrage max pour période de référence (null = 30% du total)
     * @param testKmMin Kilométrage min pour période de test (null = après ref)
     * @param kSigma Paramètre k en nombre de σ (détection drift minimal)
     * @param hSigma Paramètre h en nombre de σ (seuil déclenchement)
     * @param cooldownKm Distance de refroidissement après alarme (km)
     * @param relativeJumpMin Saut relatif minimum pour déclencher en cooldown
     * @param hSigmaCooldown Seuil h renforcé pendant cooldown
     * @return Résultat CUSUM avec indices des alarmes
     */
    fun detectCUSUM(
        logs: List<LogData>,
        metric: String = "Req_median",
        refKmMax: Double? = null,
        testKmMin: Double? = null,
        kSigma: Double = 1.0,
        hSigma: Double = 5.0,
        cooldownKm: Double = 500.0,
        relativeJumpMin: Double = 0.05,
        hSigmaCooldown: Double = 6.0
    ): CUSUMResult {
        // Filtre logs avec données valides
        val validLogs = logs.filter { log ->
            val value = log.getMetric(metric)
            val km = log.wheelKm
            value != null && !value.isNaN() && km != null && !km.isNaN()
        }
        
        if (validLogs.size < 5) {
            return CUSUMResult(emptyList(), null, null)
        }
        
        // Calcul période de référence
        val actualRefKmMax = refKmMax ?: (validLogs.last().wheelKm!! * 0.3)
        val refLogs = validLogs.filter { it.wheelKm!! <= actualRefKmMax }
        
        if (refLogs.size < 3) {
            return CUSUMResult(emptyList(), null, null)
        }
        
        // Statistiques de référence (50% meilleurs logs)
        val refValues = refLogs.mapNotNull { it.getMetric(metric) }
            .sorted()
            .take((refLogs.size * 0.5).toInt().coerceAtLeast(3))
        
        val muRef = refValues.average()
        val sigmaRef = refValues.standardDeviation()
        
        if (sigmaRef == 0.0) {
            return CUSUMResult(emptyList(), muRef, sigmaRef)
        }
        
        // Logs à tester
        val actualTestKmMin = testKmMin ?: actualRefKmMax
        val testLogs = validLogs.filter { it.wheelKm!! >= actualTestKmMin }
        
        if (testLogs.size < 3) {
            return CUSUMResult(emptyList(), muRef, sigmaRef)
        }
        
        // Calcul CUSUM
        val k = kSigma * sigmaRef
        val hNormal = hSigma * sigmaRef
        val hCooldown = hSigmaCooldown * sigmaRef
        
        var s = 0.0
        var inCooldown = false
        var cooldownEndKm: Double? = null
        var regimeMu = muRef
        
        val alarmIndices = mutableListOf<Int>()
        
        testLogs.forEachIndexed { index, log ->
            val value = log.getMetric(metric) ?: return@forEachIndexed
            val km = log.wheelKm ?: return@forEachIndexed
            
            // Sortie de cooldown
            if (inCooldown && cooldownEndKm != null && km > cooldownEndKm!!) {
                inCooldown = false
                cooldownEndKm = null
                s = 0.0
            }
            
            // Seuil adaptatif
            val hCurrent = if (inCooldown) hCooldown else hNormal
            
            // Update CUSUM
            s = maxOf(0.0, s + (value - regimeMu - k))
            
            // Détection
            var triggered = s >= hCurrent
            
            // Filtre saut relatif pendant cooldown
            if (triggered && inCooldown && regimeMu > 0) {
                val relJump = (value - regimeMu) / regimeMu
                if (relJump < relativeJumpMin) {
                    triggered = false
                    s *= 0.5 // Pénalité
                }
            }
            
            if (triggered) {
                alarmIndices.add(logs.indexOf(log))
                
                // Nouveau régime = moyenne locale des 5 derniers points
                val startIdx = maxOf(0, index - 4)
                val localWindow = testLogs.subList(startIdx, index + 1)
                regimeMu = localWindow.mapNotNull { it.getMetric(metric) }.average()
                
                // Activation cooldown
                inCooldown = true
                cooldownEndKm = km + cooldownKm
                s = 0.0
            }
        }
        
        return CUSUMResult(alarmIndices, muRef, sigmaRef)
    }
    
    /**
     * Variante simplifiée pour détection rapide
     */
    fun detectCUSUMSimple(
        values: DoubleArray,
        muRef: Double,
        sigmaRef: Double,
        kSigma: Double = 1.0,
        hSigma: Double = 5.0
    ): List<Int> {
        val k = kSigma * sigmaRef
        val h = hSigma * sigmaRef
        var s = 0.0
        
        val alarms = mutableListOf<Int>()
        
        values.forEachIndexed { index, value ->
            s = maxOf(0.0, s + (value - muRef - k))
            if (s >= h) {
                alarms.add(index)
                s = 0.0 // Reset
            }
        }
        
        return alarms
    }
}

/**
 * Calcul écart-type
 */
private fun List<Double>.standardDeviation(): Double {
    if (size < 2) return 0.0
    val mean = average()
    val variance = map { (it - mean) * (it - mean) }.average()
    return kotlin.math.sqrt(variance)
}
