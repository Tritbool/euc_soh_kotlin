// TrendAnalyzer.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogData
import com.euc.soh.model.TrendResult
import org.apache.commons.math3.stat.regression.SimpleRegression

/**
 * Détection de tendances linéaires sur les métriques de logs.
 * Port de detect_trend_linear() depuis soh_core_en.py.
 */
object TrendAnalyzer {

    /**
     * Détecte une dérive linéaire de la métrique en fonction du kilométrage roue.
     *
     * @param logs     Liste des logs triés (ou non) par kilométrage
     * @param metric   Nom de la métrique à analyser (ex: "Req_median")
     * @param kmMinSpan Étendue kilométrique minimale requise pour une analyse valide (défaut: 1000 km)
     * @return TrendResult(slope, pValue, isSignificant)
     *         - slope : pente en [unité_métrique / km], null si analyse impossible
     *         - pValue : probabilité de la pente sous H0 (pente = 0), null si insuffisant
     *         - isSignificant : true si slope > 0 ET p-value < 0.05 (même logique que Python)
     */
    fun detectTrendLinear(
        logs: List<LogData>,
        metric: String = "Req_median",
        kmMinSpan: Double = 1000.0
    ): TrendResult {
        // Filtre logs valides pour cette métrique
        val valid = logs.filter { log ->
            val km = log.wheelKm
            val v = log.getMetric(metric)
            km != null && !km.isNaN() && v != null && !v.isNaN()
        }.sortedBy { it.wheelKm }

        if (valid.size < 5) {
            return TrendResult(null, null, false)
        }

        val kmValues = valid.map { it.wheelKm!! }
        val spanKm = kmValues.last() - kmValues.first()

        if (spanKm < kmMinSpan) {
            return TrendResult(null, null, false)
        }

        val regression = SimpleRegression()
        for (log in valid) {
            regression.addData(log.wheelKm!!, log.getMetric(metric)!!)
        }

        val slope = regression.slope
        val pValue = regression.significance
        val isSignificant = slope > 0.0 && !pValue.isNaN() && pValue < 0.05

        return TrendResult(slope, pValue, isSignificant)
    }
}
