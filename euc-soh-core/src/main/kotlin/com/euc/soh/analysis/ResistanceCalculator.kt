// ResistanceCalculator.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogData
import com.euc.soh.model.MOSFETParams
import com.euc.soh.model.RawDataPoint

/**
 * Calcul de la résistance équivalente et métriques associées
 */
object ResistanceCalculator {
    
    /**
     * Calcule les statistiques Req pour un ensemble de points sous charge
     * 
     * @param points Points de données bruts filtrés
     * @param vIdle Tension à vide de référence
     * @return Triple(reqMedian, req95p, sag95p) ou null si insuffisant
     */
    fun computeReqStats(
        points: List<RawDataPoint>,
        vIdle: Double
    ): ReqStats? {
        if (points.isEmpty()) return null
        
        // Calcul sag et Req pour chaque point
        val measurements = points.mapNotNull { point ->
            val sag = vIdle - point.voltage
            val current = kotlin.math.abs(point.current)
            
            if (current > 0.0 && sag >= 0.0) {
                val req = sag / current
                ReqMeasurement(req, sag, current)
            } else {
                null
            }
        }
        
        if (measurements.size < 10) return null
        
        // Calculs statistiques
        val reqValues = measurements.map { it.req }.sorted()
        val sagValues = measurements.map { it.sag }.sorted()
        val currentValues = measurements.map { it.current }.sorted()
        
        return ReqStats(
            reqMean = reqValues.average(),
            reqMedian = reqValues.percentile(0.5),
            req95p = reqValues.percentile(0.95),
            sag95p = sagValues.percentile(0.95),
            sagMax = sagValues.maxOrNull() ?: 0.0,
            i95p = currentValues.percentile(0.95),
            iMax = currentValues.maxOrNull() ?: 0.0,
            nPoints = measurements.size
        )
    }
    
    /**
     * Sépare la résistance équivalente en R_batt et R_MOSFET
     * 
     * @param reqMedian Résistance équivalente médiane totale
     * @param tempBoard Température carte (pour MOSFET)
     * @param mosfetParams Paramètres MOSFET si disponibles
     * @return Pair(rBattMedian, rMosfetHot) ou null
     */
    fun separateBatteryMosfetResistance(
        reqMedian: Double,
        tempBoard: Double?,
        mosfetParams: MOSFETParams?
    ): Pair<Double, Double>? {
        if (mosfetParams == null || tempBoard == null) {
            return null
        }
        
        val rMosfetHot = mosfetParams.rMosfetAtTemp(tempBoard)
        val rBattMedian = maxOf(0.0, reqMedian - rMosfetHot)
        
        return Pair(rBattMedian, rMosfetHot)
    }
    
    /**
     * Calcule la dose I² pour courant de phase (stress thermique MOSFET)
     * 
     * @param currentPhaseValues Valeurs de courant de phase
     * @param timestamps Timestamps correspondants
     * @return Intégrale de I_phase² dt (A²·s)
     */
    fun computePhase2Integral(
        currentPhaseValues: DoubleArray,
        timestamps: DoubleArray?
    ): Double {
        if (currentPhaseValues.isEmpty()) return 0.0
        
        val iPhaseAbs = currentPhaseValues.map { kotlin.math.abs(it) }.toDoubleArray()
        
        // Si pas de timestamps, on estime dt = 0.1s (10 Hz typique)
        if (timestamps == null || timestamps.size != iPhaseAbs.size) {
            val dt = 0.1
            return iPhaseAbs.sumOf { it * it } * dt
        }
        
        // Copie et tri des timestamps pour garantir l'ordre
        val t = timestamps.copyOf()
        val idx = t.indices.sortedBy { t[it] }
        val sortedT = DoubleArray(t.size) { t[idx[it]] }
        val sortedI = DoubleArray(iPhaseAbs.size) { iPhaseAbs[idx[it]] }

        // Intégration trapézoïdale avec clipping dt inférieur (>= 0.01) et supérieur (<= 1.0)
        var integral = 0.0
        for (i in 0 until sortedI.size - 1) {
            var dt = sortedT[i + 1] - sortedT[i]
            // Garde-fous similaires à Python: clip entre 0.01 et 1.0
            if (dt.isNaN()) continue
            dt = when {
                dt < 0.01 -> 0.01
                dt > 1.0 -> 1.0
                else -> dt
            }
            val i2_avg = (sortedI[i] * sortedI[i] + sortedI[i + 1] * sortedI[i + 1]) / 2.0
            integral += i2_avg * dt
        }
        
        return integral
    }
    
    /**
     * Filtre les points valides pour calcul Req
     * 
     * @param points Tous les points bruts
     * @param speedThreshold Seuil vitesse minimum (km/h)
     * @param currentMin Courant minimum (A)
     * @param currentMax Courant maximum (A)
     * @param socMin SoC minimum (%)
     * @param socMax SoC maximum (%)
     * @return Points filtrés
     */
    fun filterPointsForReq(
        points: List<RawDataPoint>,
        speedThreshold: Double = 20.0,
        currentMin: Double = 10.0,
        currentMax: Double = 80.0,
        socMin: Double = 20.0,
        socMax: Double = 90.0
    ): List<RawDataPoint> {
        return points.filter { point ->
            val currentAbs = kotlin.math.abs(point.current)
            val speedOk = point.speed > speedThreshold
            val currentOk = currentAbs in currentMin..currentMax
            val socOk = point.batteryLevel?.let { it in socMin..socMax } ?: true
            
            speedOk && currentOk && socOk
        }
    }
    
    /**
     * Détermine la fenêtre de courant pour calcul Req selon Ns
     */
    fun chooseBatteryCurrentWindow(ns: Int?): Pair<Double, Double> {
        return when {
            ns == null -> Pair(10.0, 80.0)
            ns <= 16 -> Pair(8.0, 60.0)
            ns <= 24 -> Pair(15.0, 100.0)
            ns <= 32 -> Pair(20.0, 150.0)
            else -> Pair(30.0, 200.0)
        }
    }
}

/**
 * Mesure individuelle Req
 */
private data class ReqMeasurement(
    val req: Double,
    val sag: Double,
    val current: Double
)

/**
 * Statistiques Req calculées
 */
data class ReqStats(
    val reqMean: Double,
    val reqMedian: Double,
    val req95p: Double,
    val sag95p: Double,
    val sagMax: Double,
    val i95p: Double,
    val iMax: Double,
    val nPoints: Int
)

/**
 * Extension pour calcul de percentile
 */
private fun List<Double>.percentile(p: Double): Double {
    require(p in 0.0..1.0) { "Percentile must be between 0 and 1" }
    if (isEmpty()) return 0.0
    
    val sorted = this.sorted()
    val index = (sorted.size - 1) * p
    val lower = index.toInt()
    val upper = minOf(lower + 1, sorted.size - 1)
    val weight = index - lower
    
    return sorted[lower] * (1 - weight) + sorted[upper] * weight
}
