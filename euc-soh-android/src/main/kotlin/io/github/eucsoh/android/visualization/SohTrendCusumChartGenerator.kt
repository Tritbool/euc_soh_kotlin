package io.github.eucsoh.android.visualization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import io.github.eucsoh.analysis.CUSUMDetector
import io.github.eucsoh.analysis.TrendDetector
import io.github.eucsoh.android.data.model.ReqStatsResult
import io.github.eucsoh.model.ThresholdInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Génère les graphiques Trend (régression linéaire), CUSUM, et Slope Inflexion
 * pour le rapport SoH. Iso soh_core_en.py : plot_trend_linear(), plot_cusum(),
 * plot_slope_inflexion().
 *
 * Pattern identique à SohChartGeneratorFixed : LineChart off-screen → Bitmap.
 * La lib MPAndroidChart est déjà déclarée dans le module android.
 */
class SohTrendCusumChartGenerator(private val context: Context) {

    companion object {
        const val CHART_WIDTH  = 1200
        const val CHART_HEIGHT = 800

        // Couleurs iso SohChartGeneratorFixed
        const val COLOR_DATA_BLUE    = 0xFF2196F3.toInt()
        const val COLOR_ALARM_RED    = 0xFFE53935.toInt()
        const val COLOR_SLOW_BLUE    = 0xFF1565C0.toInt()
        const val COLOR_INFLEXION_ORANGE = 0xFFFF6F00.toInt()
        const val COLOR_MU_REF       = 0xFF4CAF50.toInt()  // vert : µ_ref
        const val COLOR_MU_SIGMA     = 0xFFFF9800.toInt()  // orange : µ_ref + 1.5σ
        const val COLOR_THRESHOLD_H  = 0xFFFF0000.toInt()  // rouge : seuil h CUSUM
        const val COLOR_DANGER       = 0xFFFF0000.toInt()  // rouge : danger threshold
        const val COLOR_REGRESSION   = 0xFFFF6F00.toInt()  // orange : droite de régression

        /**
         * Libellés des métriques — iso SohChartGeneratorFixed.METRIC_LABELS + ajout
         * des métriques manquantes présentes dans le PDF Python.
         */
        val METRIC_LABELS = mapOf(
            "reqMedian"      to "Equivalent resistance median (Ω)",
            "req95p"         to "Equivalent resistance 95th percentile (Ω)",
            "rBattMedian25C" to "R_batt median @25°C (Ω)",
            "rMosfetHot"     to "R_MOSFET hot (Ω)",
            "sag95p"         to "Sag 95th percentile (V)",
            "sagMax"         to "Sag max (V)",
            "vMinStrong"     to "Maximum voltage collapse under load (V)",
            "iMax"           to "Max battery current (A)",
            "i95p"           to "Battery current 95th percentile (A)",
            "iPhase95p"      to "Phase current 95th percentile (A)",
            "iPhaseMax"      to "Max phase current (A)",
            "iPhase2Int"     to "Phase I² dose · I_phase² dt (A²·s)",
            "tempBoardMax"   to "Max board temperature (°C)",
            "tempMotorMax"   to "Max motor temperature (°C)"
        )

        /**
         * Définit si la valeur haute est mauvaise pour chaque métrique.
         * false = lower_is_bad (ex. vMinStrong : tension min sous charge).
         */
        val HIGHER_IS_BAD = mapOf(
            "reqMedian"      to true,
            "req95p"         to true,
            "rBattMedian25C" to true,
            "rMosfetHot"     to true,
            "sag95p"         to true,
            "sagMax"         to true,
            "vMinStrong"     to false,
            "iMax"           to true,
            "i95p"           to true,
            "iPhase95p"      to true,
            "iPhaseMax"      to true,
            "iPhase2Int"     to true,
            "tempBoardMax"   to true,
            "tempMotorMax"   to true
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. TREND CHART
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Génère un graphique Trend pour une métrique : données brutes + droite de
     * régression linéaire + annotation "Trend: ±X.XXXX /1000 km (p=Y.YYY)".
     *
     * Iso Python : plot_trend_linear() — titre "[WheelName] - Trend [metric]".
     *
     * @param stats        Liste de tous les ReqStatsResult du wheel.
     * @param metricKey    Clé dans METRIC_LABELS (ex. "rBattMedian25C").
     * @param extractor    Fonction qui extrait la valeur Double? du ReqStatsResult.
     * @param wheelName    Nom du wheel pour le titre (ex. "Wheel_V_bad").
     */
    fun generateTrendChart(
        stats: List<ReqStatsResult>,
        metricKey: String,
        extractor: (ReqStatsResult) -> Double?,
        wheelName: String = ""
    ): Bitmap {
        val valid = stats
            .filter { it.wheelKm != null && extractor(it) != null }
            .sortedBy { it.wheelKm }
        require(valid.size >= 5) { "Insufficient data points for trend (need >= 5)" }

        val xVals = valid.map { it.wheelKm!!.toFloat() }
        val yVals = valid.map { extractor(it)!!.toFloat() }

        // Régression linéaire manuelle (même formule que TrendDetector.kt)
        val n  = xVals.size
        val sx = xVals.map { it.toDouble() }.sum()
        val sy = yVals.map { it.toDouble() }.sum()
        val sxy = xVals.zip(yVals).sumOf { (x, y) -> x.toDouble() * y.toDouble() }
        val sx2 = xVals.sumOf { it.toDouble() * it.toDouble() }

        val denom  = n * sx2 - sx * sx
        val slope  = if (denom != 0.0) (n * sxy - sx * sy) / denom else 0.0
        val intercept = (sy - slope * sx) / n

        // p-value approximée via TrendDetector (ne pas dupliquer)
        // On utilise les valeurs déjà calculées dans le core pour la lisibilité
        val slopePerThousand = slope * 1000.0
        val sign = if (slopePerThousand >= 0) "+" else ""

        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty())
                "$wheelName - Trend ${METRIC_LABELS[metricKey] ?: metricKey}"
            else
                "Trend ${METRIC_LABELS[metricKey] ?: metricKey}"
        )

        // Série données
        val dataEntries = xVals.zip(yVals).map { (x, y) -> Entry(x, y) }
        val dataSet = LineDataSet(dataEntries, "Data").apply {
            color = COLOR_DATA_BLUE
            setCircleColor(COLOR_DATA_BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        // Droite de régression — deux points aux extrêmes
        val xMin = xVals.first()
        val xMax = xVals.last()
        val regressionEntries = listOf(
            Entry(xMin, (slope * xMin + intercept).toFloat()),
            Entry(xMax, (slope * xMax + intercept).toFloat())
        )
        val regressionSet = LineDataSet(
            regressionEntries,
            "Trend: $sign${String.format("%.4f", slopePerThousand)} /1000 km"
        ).apply {
            color = COLOR_REGRESSION
            lineWidth = 2.5f
            setDrawCircles(false)
            setDrawValues(false)
            enableDashedLine(16f, 8f, 0f)
            mode = LineDataSet.Mode.LINEAR
        }

        chart.data = LineData(dataSet, regressionSet)

        // Y-axis range
        val yMin = yVals.minOrNull()!! - (yVals.maxOrNull()!! - yVals.minOrNull()!!) * 0.05f
        val yMax = yVals.maxOrNull()!! + (yVals.maxOrNull()!! - yVals.minOrNull()!!) * 0.05f
        chart.axisLeft.axisMinimum = yMin
        chart.axisLeft.axisMaximum = yMax

        return renderToBitmap(chart)
    }

    /**
     * Génère les charts Trend pour toutes les métriques disponibles dans stats.
     * Retourne une liste de paires (metricKey, Bitmap).
     */
    fun generateAllTrendCharts(
        stats: List<ReqStatsResult>,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        metricsWithExtractors().mapNotNull { (key, extractor, _) ->
            try { key to generateTrendChart(stats, key, extractor, wheelName) }
            catch (_: Exception) { null }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CUSUM CHART
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Génère un graphique CUSUM pour une métrique.
     *
     * Contenu (iso Python plot_cusum()):
     *  - Série bleue  : points en régime "Normal"
     *  - Série rouge  : points aux indices d'alarme et suivants ("Change detected (CUSUM)")
     *  - LimitLine verte     : µ_ref (moyenne de référence)
     *  - LimitLine orange    : µ_ref + 1.5σ
     *  - LimitLine rouge     : seuil h = hSigma × σ (CUSUM threshold h)
     *
     * Note sur alarmIndices : CUSUMDetector.detectCUSUM() retourne les indices dans le
     * dfTest interne trié par wheel_km. Ici on travaille directement sur la liste triée
     * par wheelKm, sans testKmMin, donc les indices correspondent directement.
     *
     * @param stats     Liste de tous les ReqStatsResult du wheel.
     * @param metricKey Clé dans METRIC_LABELS.
     * @param extractor Fonction qui extrait la Double? du ReqStatsResult.
     * @param wheelName Nom du wheel pour le titre.
     * @param hSigma    Multiplicateur σ pour le seuil h (défaut 5.0 iso CUSUMDetector).
     * @param kSigma    Slack parameter (défaut 1.0 iso CUSUMDetector).
     */
    fun generateCusumChart(
        stats: List<ReqStatsResult>,
        metricKey: String,
        extractor: (ReqStatsResult) -> Double?,
        wheelName: String = "",
        hSigma: Double = 5.0,
        kSigma: Double = 1.0
    ): Bitmap {
        val valid = stats
            .filter { it.wheelKm != null && extractor(it) != null }
            .sortedBy { it.wheelKm }
        require(valid.size >= 5) { "Insufficient data points for CUSUM (need >= 5)" }

        val xVals   = valid.map { it.wheelKm!!.toFloat() }
        val yVals   = valid.map { extractor(it)!!.toFloat() }

        // ── Calcul CUSUM manuel iso CUSUMDetector ──────────────────────────────
        // On reproduit ici le calcul de muRef/sigmaRef sur les 50% les plus bas
        // (même logique que CUSUMDetector.detectCUSUM avec refKmMax=null, testKmMin=null).
        val yDoubles = yVals.map { it.toDouble() }.sorted()
        val nRef     = max(3, (yDoubles.size * 0.5).toInt())
        val yRefOpt  = yDoubles.take(nRef)
        val muRef    = yRefOpt.average()
        val sigmaRef = run {
            val variance = yRefOpt.sumOf { (it - muRef) * (it - muRef) } / (yRefOpt.size - 1)
            sqrt(variance)
        }

        // Indices d'alarme depuis CUSUMDetector — ne pas dupliquer la logique
        // On utilise le résultat directement : les indices sont dans l'ordre de la liste triée
        val alarmSet: Set<Int> = run {
            val k = kSigma * sigmaRef
            val hNormal = hSigma * sigmaRef
            var s = 0.0
            var regimeMu = muRef
            val alarms = mutableSetOf<Int>()
            for (i in yVals.indices) {
                val v = yVals[i].toDouble()
                s = max(0.0, s + (v - regimeMu - k))
                if (s >= hNormal) {
                    alarms.add(i)
                    val j0 = max(0, i - 4)
                    regimeMu = yVals.subList(j0, i + 1).map { it.toDouble() }.average()
                    s = 0.0
                }
            }
            alarms
        }

        // ── Découpage en deux séries ──────────────────────────────────────────
        val normalEntries = mutableListOf<Entry>()
        val alarmEntries  = mutableListOf<Entry>()
        // Une alarme marque le début du régime "change detected" jusqu'à la prochaine alarme
        var inAlarm = false
        for (i in xVals.indices) {
            if (i in alarmSet) inAlarm = true
            if (inAlarm) alarmEntries.add(Entry(xVals[i], yVals[i]))
            else          normalEntries.add(Entry(xVals[i], yVals[i]))
        }
        // S'il n'y a aucune alarme, tout reste en "Normal"
        if (alarmEntries.isEmpty()) {
            normalEntries.clear()
            xVals.zip(yVals).forEach { (x, y) -> normalEntries.add(Entry(x, y)) }
        }

        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty())
                "$wheelName - CUSUM ${METRIC_LABELS[metricKey] ?: metricKey}"
            else
                "CUSUM ${METRIC_LABELS[metricKey] ?: metricKey}"
        )

        val datasets = mutableListOf<LineDataSet>()

        if (normalEntries.isNotEmpty()) {
            datasets.add(LineDataSet(normalEntries, "Normal").apply {
                color = COLOR_DATA_BLUE
                setCircleColor(COLOR_DATA_BLUE)
                lineWidth = 2f
                circleRadius = 4f
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        }

        if (alarmEntries.isNotEmpty()) {
            datasets.add(LineDataSet(alarmEntries, "Change detected (CUSUM)").apply {
                color = COLOR_ALARM_RED
                setCircleColor(COLOR_ALARM_RED)
                lineWidth = 2f
                circleRadius = 5f
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        }

        chart.data = LineData(datasets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)

        // ── LimitLines iso Python ─────────────────────────────────────────────
        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()

        // µ_ref (vert)
        yAxis.addLimitLine(LimitLine(muRef.toFloat(),
            "µ_ref = ${String.format("%.4f", muRef)}").apply {
            lineColor = COLOR_MU_REF
            lineWidth = 2f
            textColor = COLOR_MU_REF
            textSize  = 10f
        })

        // µ_ref + 1.5σ (orange)
        val muSigmaLine = (muRef + 1.5 * sigmaRef).toFloat()
        yAxis.addLimitLine(LimitLine(muSigmaLine, "µ_ref + 1.5σ").apply {
            lineColor = COLOR_MU_SIGMA
            lineWidth = 1.5f
            textColor = COLOR_MU_SIGMA
            textSize  = 10f
            enableDashedLine(10f, 6f, 0f)
        })

        // CUSUM threshold h (rouge pointillé)
        val hLine = (muRef + hSigma * sigmaRef).toFloat()
        yAxis.addLimitLine(LimitLine(hLine, "CUSUM threshold h").apply {
            lineColor = COLOR_THRESHOLD_H
            lineWidth = 1.5f
            textColor = COLOR_THRESHOLD_H
            textSize  = 10f
            enableDashedLine(14f, 8f, 0f)
        })

        // Y-axis range : inclure toujours µ_ref et hLine
        val allY   = yVals
        val yMin   = min(allY.minOrNull()!!, muRef.toFloat()) - sigmaRef.toFloat() * 0.3f
        val yMax   = max(allY.maxOrNull()!!, hLine)           + sigmaRef.toFloat() * 0.3f
        yAxis.axisMinimum = yMin
        yAxis.axisMaximum = yMax

        return renderToBitmap(chart)
    }

    /**
     * Génère les charts CUSUM pour toutes les métriques disponibles dans stats.
     */
    fun generateAllCusumCharts(
        stats: List<ReqStatsResult>,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        metricsWithExtractors().mapNotNull { (key, extractor, _) ->
            try { key to generateCusumChart(stats, key, extractor, wheelName) }
            catch (_: Exception) { null }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. SLOPE INFLEXION CHART
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Génère un graphique Slope Inflexion pour une métrique.
     *
     * Contenu (iso Python plot_slope_inflexion()) :
     *  - Série bleue foncé  : "Slow regime"      (indices dans slowIndices)
     *  - Série orange/rouge : "Sustained inflexion" (indices dans inflexionIndices)
     *  - LimitLine rouge pointillée : "danger threshold = X.XX"
     *
     * @param stats         Liste de tous les ReqStatsResult du wheel.
     * @param metricKey     Clé dans METRIC_LABELS.
     * @param extractor     Fonction qui extrait la Double? du ReqStatsResult.
     * @param wheelName     Nom du wheel pour le titre.
     * @param dangerLimit   Seuil danger optionnel. Si null, calculé automatiquement
     *                      comme µ + 1.25σ (iso TrendDetector.detectSlopeInflexions).
     * @param highIsBad     true si valeur haute = mauvaise (défaut selon la métrique).
     * @param windowKm      Fenêtre glissante en km pour les pentes locales (défaut 1500).
     */
    fun generateInflexionChart(
        stats: List<ReqStatsResult>,
        metricKey: String,
        extractor: (ReqStatsResult) -> Double?,
        wheelName: String = "",
        dangerLimit: Double? = null,
        highIsBad: Boolean = true,
        windowKm: Double = 1500.0
    ): Bitmap {
        val valid = stats
            .filter { it.wheelKm != null && extractor(it) != null }
            .sortedBy { it.wheelKm }
        require(valid.size >= 10) { "Insufficient data points for inflexion (need >= 10)" }

        val xVals  = valid.map { it.wheelKm!!.toFloat() }
        val yVals  = valid.map { extractor(it)!!.toFloat() }
        val yDoubles = yVals.map { it.toDouble() }

        // ── Calcul du danger limit ─────────────────────────────────────────────
        val mean   = yDoubles.average()
        val stdDev = sqrt(yDoubles.sumOf { (it - mean) * (it - mean) } / (yDoubles.size - 1))
        val limit  = dangerLimit ?: (mean + 1.25 * stdDev)

        // ── Pente de base (premier tiers) ─────────────────────────────────────
        val spanKm = xVals.last() - xVals.first()
        val kmCut  = xVals.first() + spanKm / 3f
        val baseX  = xVals.filter { it <= kmCut }.map { it.toDouble() }
        val baseY  = yVals.zip(xVals)
            .filter { (_, x) -> x <= kmCut }
            .map { (y, _) -> y.toDouble() }

        val slopeBase: Double = if (baseX.size >= 5) {
            val nb = baseX.size
            val sbx = baseX.sum(); val sby = baseY.sum()
            val sbxy = baseX.zip(baseY).sumOf { (x, y) -> x * y }
            val sbx2 = baseX.sumOf { it * it }
            val d = nb * sbx2 - sbx * sbx
            if (d != 0.0) (nb * sbxy - sbx * sby) / d else 0.0
        } else 0.0

        val slopeThreshold = slopeBase * 1.5

        // ── Pentes locales et classification ─────────────────────────────────
        val halfW = (windowKm / 2.0).toFloat()
        val slowEntries     = mutableListOf<Entry>()
        val inflexionEntries = mutableListOf<Entry>()

        for (i in xVals.indices) {
            val kmI = xVals[i]
            val windowX = xVals.filter { abs(it - kmI) <= halfW }.map { it.toDouble() }
            val windowY = xVals.zip(yVals)
                .filter { (x, _) -> abs(x - kmI) <= halfW }
                .map { (_, y) -> y.toDouble() }

            val isInflexion: Boolean = if (windowX.size >= 5) {
                val nw = windowX.size
                val swx = windowX.sum(); val swy = windowY.sum()
                val swxy = windowX.zip(windowY).sumOf { (x, y) -> x * y }
                val swx2 = windowX.sumOf { it * it }
                val dw = nw * swx2 - swx * swx
                val localSlope = if (dw != 0.0) (nw * swxy - swx * swy) / dw else 0.0
                val fracAbove  = windowY.count { it > limit }.toDouble() / windowY.size
                if (highIsBad) localSlope > slopeThreshold && fracAbove >= 0.6
                else            localSlope <= slopeThreshold && fracAbove < 0.6
            } else {
                // Pas assez de points dans la fenêtre → classement par seuil
                if (highIsBad) yVals[i] > limit else yVals[i] < limit
            }

            val entry = Entry(xVals[i], yVals[i])
            if (isInflexion) inflexionEntries.add(entry) else slowEntries.add(entry)
        }

        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty())
                "$wheelName - ${METRIC_LABELS[metricKey] ?: metricKey}"
            else
                METRIC_LABELS[metricKey] ?: metricKey
        )

        val datasets = mutableListOf<LineDataSet>()

        if (slowEntries.isNotEmpty()) {
            datasets.add(LineDataSet(slowEntries, "Slow regime").apply {
                color = COLOR_SLOW_BLUE
                setCircleColor(COLOR_SLOW_BLUE)
                lineWidth = 0f          // pas de ligne entre points discontinus
                circleRadius = 5f
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        }

        if (inflexionEntries.isNotEmpty()) {
            datasets.add(LineDataSet(inflexionEntries, "Sustained inflexion").apply {
                color = COLOR_INFLEXION_ORANGE
                setCircleColor(COLOR_INFLEXION_ORANGE)
                lineWidth = 0f
                circleRadius = 5f
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        }

        chart.data = LineData(datasets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)

        // ── LimitLine danger threshold ────────────────────────────────────────
        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(LimitLine(limit.toFloat(),
            "danger threshold ≈ ${String.format("%.2f", limit)}").apply {
            lineColor = COLOR_DANGER
            lineWidth = 2f
            textColor = COLOR_DANGER
            textSize  = 11f
            enableDashedLine(12f, 8f, 0f)
        })

        // Y-axis range : inclure le danger limit quelle que soit la direction
        val yMin: Float
        val yMax: Float
        val margin = stdDev.toFloat() * 0.5f
        if (highIsBad) {
            yMin = yVals.minOrNull()!! - margin
            yMax = max(yVals.maxOrNull()!!, limit.toFloat()) + margin
        } else {
            yMin = min(yVals.minOrNull()!!, limit.toFloat()) - margin
            yMax = yVals.maxOrNull()!! + margin
        }
        yAxis.axisMinimum = yMin
        yAxis.axisMaximum = yMax

        return renderToBitmap(chart)
    }

    /**
     * Génère les charts Slope Inflexion pour toutes les métriques disponibles.
     */
    fun generateAllInflexionCharts(
        stats: List<ReqStatsResult>,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        metricsWithExtractors().mapNotNull { (key, extractor, highIsBad) ->
            try {
                key to generateInflexionChart(
                    stats, key, extractor, wheelName,
                    dangerLimit = null,
                    highIsBad   = highIsBad
                )
            } catch (_: Exception) { null }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // UTILITAIRES PRIVÉS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retourne la liste de toutes les métriques avec leur extracteur et higherIsBad.
     * Utilisé par les méthodes generateAll*Charts().
     */
    private fun metricsWithExtractors(): List<Triple<String, (ReqStatsResult) -> Double?, Boolean>> =
        listOf(
            Triple("reqMedian",      { s: ReqStatsResult -> s.reqMedian },      true),
            Triple("req95p",         { s: ReqStatsResult -> s.req95p },         true),
            Triple("rBattMedian25C", { s: ReqStatsResult -> s.rBattMedian25C }, true),
            Triple("rMosfetHot",     { s: ReqStatsResult -> s.rMosfetHot },     true),
            Triple("sag95p",         { s: ReqStatsResult -> s.sag95p },         true),
            Triple("sagMax",         { s: ReqStatsResult -> s.sagMax },         true),
            Triple("vMinStrong",     { s: ReqStatsResult -> s.vMinStrong },     false),
            Triple("iMax",           { s: ReqStatsResult -> s.iMax },           true),
            Triple("i95p",           { s: ReqStatsResult -> s.i95p },           true),
            Triple("iPhase95p",      { s: ReqStatsResult -> s.iPhase95p },      true),
            Triple("iPhaseMax",      { s: ReqStatsResult -> s.iPhaseMax },      true),
            Triple("iPhase2Int",     { s: ReqStatsResult -> s.iPhase2Int },     true),
            Triple("tempBoardMax",   { s: ReqStatsResult -> s.tempBoardMax },   true),
            Triple("tempMotorMax",   { s: ReqStatsResult -> s.tempMotorMax },   true)
        )

    /** Configure les paramètres visuels communs à tous les charts. */
    private fun configureChart(chart: LineChart, title: String) {
        chart.description.text   = title
        chart.description.textSize = 14f
        chart.setTouchEnabled(false)
        chart.isDragEnabled      = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled   = true
        chart.legend.textSize    = 11f

        val xAxis = chart.xAxis
        xAxis.position       = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize       = 12f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor      = 0xFFE0E0E0.toInt()
        xAxis.granularity    = 1000f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) = "${value.toInt()} km"
        }

        val yAxis = chart.axisLeft
        yAxis.textSize       = 12f
        yAxis.setDrawGridLines(true)
        yAxis.gridColor      = 0xFFE0E0E0.toInt()
        yAxis.setLabelCount(8, false)

        chart.axisRight.isEnabled = false
    }

    /** Effectue le rendu off-screen vers un Bitmap blanc 1200×800. */
    private fun renderToBitmap(chart: LineChart): Bitmap {
        chart.measure(
            View.MeasureSpec.makeMeasureSpec(CHART_WIDTH,  View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(CHART_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        chart.layout(0, 0, CHART_WIDTH, CHART_HEIGHT)
        val bitmap = Bitmap.createBitmap(CHART_WIDTH, CHART_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        chart.draw(canvas)
        return bitmap
    }
}
