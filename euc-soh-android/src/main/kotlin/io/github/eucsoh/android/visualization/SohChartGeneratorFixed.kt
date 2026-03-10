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
import io.github.eucsoh.android.data.model.ReqStatsResult
import kotlin.math.max
import kotlin.math.min

/**
 * Générateur de graphiques SoH — comportement iso Python soh_core_en.py.
 *
 * Corrections appliquées vs version précédente :
 * 1. Subset optimal trié par reqMedian (proxy santé globale), pas par la métrique affichée.
 *    Raison : on veut les logs "sains" comme baseline, pas les logs avec la valeur la plus basse
 *    de la métrique (qui peut être biaisé par des conditions extérieures, ex. température).
 * 2. Bandes vert/orange via LimitLines asymétriques (côté "bad" uniquement), iso axhspan Python.
 *    createBandDataset supprimé (rendu incorrect : dessinait sous la ligne centrale, pas entre deux valeurs).
 * 3. N_SIGMA_DANGER = 2.0 (aligné sur n_sigma_danger=2.0 dans plot_metric_gauss Python).
 * 4. Y-axis range corrigé pour lower_is_bad (vMinStrong) : étend vers le bas pour inclure la zone danger.
 * 5. calculateStdDev : ddof=1 (variance corrigée de Bessel), iso numpy std(ddof=1) Python.
 */
class SohChartGeneratorFixed(private val context: Context) {

    companion object {
        const val CHART_WIDTH = 1200
        const val CHART_HEIGHT = 800

        // Fraction des meilleurs logs (triés par reqMedian) pour calculer μ/σ de référence
        const val OPTIMAL_FRAC = 0.5f

        // Seuils gaussiens — iso Python : n_sigma_band=1.0, n_sigma_danger=2.0
        const val N_SIGMA_BAND    = 1.0f  // Lignes vertes  : μ ± 1σ
        const val N_SIGMA_WARNING = 2.0f  // Ligne orange   : μ + 2σ (côté bad uniquement)
        const val N_SIGMA_DANGER  = 2.0f  // Ligne rouge    : μ + 2σ (= warning, iso Python)

        // Couleurs LimitLines
        const val COLOR_GREEN   = 0xFF4CAF50.toInt()
        const val COLOR_ORANGE  = 0xFFFF9800.toInt()
        const val COLOR_RED     = 0xFFFF0000.toInt()
        const val COLOR_DATA_BLUE = 0xFF2196F3.toInt()

        val METRIC_LABELS = mapOf(
            "reqMedian"    to "Rₑₖ median (Ω)",
            "req95p"       to "Rₑₖ 95p (Ω)",
            "sagMedian"    to "Sag median (V)",
            "sag95p"       to "Sag 95p (V)",
            "sagMax"       to "Sag max (V)",
            "vMinStrong"   to "V min strong (V)",
            "iMax"         to "I max (A)",
            "i95p"         to "I 95p (A)",
            "tempBoardMax" to "T board (°C)",
            "tempMotorMax" to "T motor (°C)"
        )
    }

    /**
     * Génère un graphique pour une métrique donnée avec bandes gaussiennes iso Python.
     *
     * @param stats          Liste de tous les ReqStatsResult du wheel (tous logs confondus).
     * @param metricExtractor Fonction qui extrait la valeur de la métrique d'un ReqStatsResult.
     * @param metricName     Clé de la métrique (pour label et METRIC_LABELS).
     * @param higherIsBad    true  → danger côté haut (résistances, températures, sag…)
     *                       false → danger côté bas  (vMinStrong : tension min sous charge)
     */
    fun generateMetricChart(
        stats: List<ReqStatsResult>,
        metricExtractor: (ReqStatsResult) -> Double?,
        metricName: String,
        higherIsBad: Boolean = true
    ): Bitmap {
        require(stats.isNotEmpty()) { "Cannot generate chart with empty stats" }

        // 1. Filtrer les points valides et trier par km (axe X)
        val validStats = stats
            .filter { it.wheelKm != null && metricExtractor(it) != null }
            .sortedBy { it.wheelKm }

        require(validStats.size >= 3) {
            "Insufficient data points (need >= 3, got ${validStats.size})"
        }

        // 2. Subset optimal : trier par reqMedian (proxy santé globale), iso Python plot_soh_overview_all.
        //    Les logs avec reqMedian null sont mis en dernière position (traités comme "mauvais").
        val sortedByReqMedian = validStats.sortedBy { it.reqMedian ?: Double.MAX_VALUE }
        val optimalCount = max(3, (sortedByReqMedian.size * OPTIMAL_FRAC).toInt())
        val optimalSubset = sortedByReqMedian.take(optimalCount)

        // 3. μ/σ calculés sur le subset optimal (ddof=1, iso numpy std ddof=1)
        val optimalValues = optimalSubset.mapNotNull { metricExtractor(it) }
        val mu = optimalValues.average().toFloat()
        val sigma = calculateStdDev(optimalValues).toFloat()

        // 4. Plage globale des données (pour le range Y-axis)
        val allValues = validStats.mapNotNull { metricExtractor(it) }
        val globalMin = allValues.minOrNull()?.toFloat() ?: mu
        val globalMax = allValues.maxOrNull()?.toFloat() ?: mu

        // 5. Calcul des seuils gaussiens
        val greenLow  = mu - N_SIGMA_BAND * sigma
        val greenHigh = mu + N_SIGMA_BAND * sigma
        // danger et warning : côté "bad" uniquement, iso Python axhspan asymétrique
        val warningThreshold = if (higherIsBad) mu + N_SIGMA_WARNING * sigma else mu - N_SIGMA_WARNING * sigma
        val dangerThreshold  = if (higherIsBad) mu + N_SIGMA_DANGER  * sigma else mu - N_SIGMA_DANGER  * sigma

        // 6. Données
        val chart = LineChart(context)
        configureChart(chart, metricName)

        val entries = validStats.map { stat ->
            Entry(stat.wheelKm!!.toFloat(), metricExtractor(stat)!!.toFloat())
        }
        val dataSet = LineDataSet(entries, METRIC_LABELS[metricName] ?: metricName).apply {
            color = COLOR_DATA_BLUE
            setCircleColor(COLOR_DATA_BLUE)
            lineWidth = 2.5f
            circleRadius = 5f
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
        chart.data = LineData(dataSet)

        // 7. LimitLines — iso Python axhspan + axhline
        //    Bandes vert : délimitées par deux LimitLines (une basse, une haute) à ±1σ
        //    Bande orange : une seule LimitLine côté "bad" à ±2σ (asymétrique)
        //    Ligne rouge danger : LimitLine pointillée à ±2σ (= warning, iso Python n_sigma_danger=2.0)
        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()

        // Vert bas
        yAxis.addLimitLine(LimitLine(greenLow, "").apply {
            lineColor = COLOR_GREEN
            lineWidth = 1.5f
            textSize = 0f
        })
        // Vert haut (labelisé)
        yAxis.addLimitLine(LimitLine(greenHigh, "±1σ").apply {
            lineColor = COLOR_GREEN
            lineWidth = 1.5f
            textColor = COLOR_GREEN
            textSize = 10f
        })
        // Orange : côté bad uniquement
        yAxis.addLimitLine(LimitLine(warningThreshold, "±2σ").apply {
            lineColor = COLOR_ORANGE
            lineWidth = 1.5f
            textColor = COLOR_ORANGE
            textSize = 10f
        })
        // Rouge danger (pointillé) — même valeur que warning car N_SIGMA_DANGER=N_SIGMA_WARNING=2.0
        // On ajoute quand même la ligne rouge pour la lisibilité (label "Danger: X.XXX")
        yAxis.addLimitLine(LimitLine(dangerThreshold,
            "Danger: ${String.format("%.3f", dangerThreshold)}").apply {
            lineColor = COLOR_RED
            lineWidth = 3f
            enableDashedLine(12f, 8f, 0f)
            textColor = COLOR_RED
            textSize = 11f
        })

        // 8. Y-axis range : tenir compte de la direction pour inclure la zone danger
        //    higher_is_bad → danger en haut  → étendre yMax
        //    lower_is_bad  → danger en bas   → étendre yMin  (correction vMinStrong)
        val yMin: Float
        val yMax: Float
        if (higherIsBad) {
            yMin = min(globalMin, greenLow)      - sigma * 0.5f
            yMax = max(globalMax, dangerThreshold) + sigma * 0.5f
        } else {
            yMin = min(globalMin, dangerThreshold) - sigma * 0.5f
            yMax = max(globalMax, greenHigh)       + sigma * 0.5f
        }
        yAxis.axisMinimum = yMin
        yAxis.axisMaximum = yMax

        // 9. Render → Bitmap
        chart.measure(
            View.MeasureSpec.makeMeasureSpec(CHART_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(CHART_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        chart.layout(0, 0, CHART_WIDTH, CHART_HEIGHT)

        val bitmap = Bitmap.createBitmap(CHART_WIDTH, CHART_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        chart.draw(canvas)

        return bitmap
    }

    /**
     * Génère tous les graphiques overview pour un wheel.
     * L'ordre et les métriques sont alignés sur plot_soh_overview_all Python.
     */
    fun generateOverviewCharts(
        stats: List<ReqStatsResult>
    ): List<Pair<String, Bitmap>> {
        val metrics = listOf(
            Triple("reqMedian",    { s: ReqStatsResult -> s.reqMedian },    true),
            Triple("req95p",       { s: ReqStatsResult -> s.req95p },       true),
            Triple("sag95p",       { s: ReqStatsResult -> s.sag95p },       true),
            Triple("sagMax",       { s: ReqStatsResult -> s.sagMax },       true),
            Triple("vMinStrong",   { s: ReqStatsResult -> s.vMinStrong },   false),  // lower_is_bad
            Triple("iMax",         { s: ReqStatsResult -> s.iMax },         true),
            Triple("i95p",         { s: ReqStatsResult -> s.i95p },         true),
            Triple("tempBoardMax", { s: ReqStatsResult -> s.tempBoardMax }, true)
        )

        return metrics.mapNotNull { (name, extractor, higherIsBad) ->
            try {
                name to generateMetricChart(stats, extractor, name, higherIsBad)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun configureChart(chart: LineChart, title: String) {
        chart.description.text = METRIC_LABELS[title] ?: title
        chart.description.textSize = 16f
        chart.setTouchEnabled(false)
        chart.isDragEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = true
        chart.legend.textSize = 11f

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 12f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = 0xFFE0E0E0.toInt()
        xAxis.granularity = 100f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String = "${value.toInt()} km"
        }

        val yAxisLeft = chart.axisLeft
        yAxisLeft.textSize = 12f
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.gridColor = 0xFFE0E0E0.toInt()
        yAxisLeft.setLabelCount(8, false)

        chart.axisRight.isEnabled = false
    }

    /**
     * Écart-type avec correction de Bessel (ddof=1), iso numpy std(ddof=1) Python.
     * ddof=0 (variance population) introduit un biais systématique sur petits N.
     */
    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return kotlin.math.sqrt(variance)
    }
}
