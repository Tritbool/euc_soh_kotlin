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
import io.github.eucsoh.Constants.Metrics
import io.github.eucsoh.android.data.model.ReqStatsResult
import kotlin.math.max
import kotlin.math.min

/**
 * Génère les graphiques gaussiens SoH — iso Python soh_core_en.py.
 *
 * Les labels et le flag higher_is_bad proviennent désormais de [Metrics]
 * (core), ce qui garantit la cohérence avec les analyseurs.
 *
 * Corrections conservées vs version précédente :
 * 1. Subset optimal trié par reqMedian (proxy santé globale).
 * 2. Bandes vert/orange via LimitLines asymétriques (iso axhspan Python).
 * 3. N_SIGMA_DANGER = 2.0.
 * 4. Y-axis range corrigé pour lower_is_bad (vMinStrong).
 * 5. calculateStdDev : ddof=1 (variance corrigée de Bessel).
 */
class SohChartGeneratorFixed(private val context: Context) {

    companion object {
        const val CHART_WIDTH  = 1200
        const val CHART_HEIGHT = 800

        const val OPTIMAL_FRAC    = 1.0f
        const val N_SIGMA_BAND    = 1.0f
        const val N_SIGMA_WARNING = 2.0f
        const val N_SIGMA_DANGER  = 2.0f

        const val COLOR_GREEN      = 0xFF4CAF50.toInt()
        const val COLOR_ORANGE     = 0xFFFF9800.toInt()
        const val COLOR_RED        = 0xFFFF0000.toInt()
        const val COLOR_DATA_BLUE  = 0xFF2196F3.toInt()

        /**
         * Résout le label d'affichage d'une métrique.
         * Cherche d'abord dans [Metrics] par csv_code, renvoie la clé brute
         * en dernier recours.
         */
        fun resolveLabel(csvCode: String): String =
            Metrics.entries.find { it.csv_code == csvCode }?.label ?: csvCode
    }

    /**
     * Génère un graphique gaussien pour une métrique.
     *
     * @param stats           Liste des [ReqStatsResult] du wheel.
     * @param metricExtractor Extrait la valeur de la métrique d'un [ReqStatsResult].
     * @param metricName      Clé de la métrique (csv_code de [Metrics] ou nom Kotlin).
     * @param higherIsBad     true  → danger côté haut ; false → danger côté bas.
     */
    fun generateMetricChart(
        stats: List<ReqStatsResult>,
        metricExtractor: (ReqStatsResult) -> Double?,
        metricName: String,
        higherIsBad: Boolean = true
    ): Bitmap {
        require(stats.isNotEmpty()) { "Cannot generate chart with empty stats" }

        val validStats = stats
            .filter { it.wheelKm != null && metricExtractor(it) != null }
            .sortedBy { it.wheelKm }
        require(validStats.size >= 3) {
            "Insufficient data points (need >= 3, got ${validStats.size})"
        }

        val sortedByReqMedian = validStats.sortedBy { it.reqMedian ?: Double.MAX_VALUE }
        val optimalCount  = max(3, (sortedByReqMedian.size * OPTIMAL_FRAC).toInt())
        val optimalSubset = sortedByReqMedian.take(optimalCount)

        val optimalValues = optimalSubset.mapNotNull { metricExtractor(it) }
        val mu    = optimalValues.average().toFloat()
        val sigma = calculateStdDev(optimalValues).toFloat()

        val allValues  = validStats.mapNotNull { metricExtractor(it) }
        val globalMin  = allValues.minOrNull()?.toFloat() ?: mu
        val globalMax  = allValues.maxOrNull()?.toFloat() ?: mu

        val greenLow  = if (higherIsBad) mu - N_SIGMA_BAND * sigma    else mu + N_SIGMA_BAND * sigma
        val greenHigh = if (higherIsBad) mu + N_SIGMA_BAND * sigma    else mu - N_SIGMA_BAND * sigma
        val warningThreshold = if (higherIsBad) mu + N_SIGMA_WARNING * sigma else mu - N_SIGMA_WARNING * sigma
        val dangerThreshold  = if (higherIsBad) mu + N_SIGMA_DANGER  * sigma else mu - N_SIGMA_DANGER  * sigma

        val chart = LineChart(context)
        configureChart(chart, resolveLabel(metricName))

        val entries = validStats.map { stat ->
            Entry(stat.wheelKm!!.toFloat(), metricExtractor(stat)!!.toFloat())
        }
        val dataSet = LineDataSet(entries, resolveLabel(metricName)).apply {
            color = COLOR_DATA_BLUE
            setCircleColor(COLOR_DATA_BLUE)
            lineWidth = 2.5f
            circleRadius = 5f
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }
        chart.data = LineData(dataSet)

        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(LimitLine(greenLow, "").apply {
            lineColor = COLOR_GREEN; lineWidth = 1.5f; textSize = 0f
        })
        yAxis.addLimitLine(LimitLine(greenHigh, "±1σ").apply {
            lineColor = COLOR_ORANGE; lineWidth = 1.5f
            textColor = COLOR_ORANGE; textSize  = 10f
        })
        yAxis.addLimitLine(LimitLine(warningThreshold, "±2σ").apply {
            lineColor = COLOR_ORANGE; lineWidth = 1.5f
            textColor = COLOR_ORANGE; textSize  = 10f
        })
        yAxis.addLimitLine(LimitLine(dangerThreshold,
            "Danger: ${String.format("%.3f", dangerThreshold)}").apply {
            lineColor = COLOR_RED; lineWidth = 3f
            enableDashedLine(12f, 8f, 0f)
            textColor = COLOR_RED; textSize = 11f
        })

        val yMin: Float
        val yMax: Float
        if (higherIsBad) {
            yMin = min(globalMin, greenLow)          - sigma * 0.5f
            yMax = max(globalMax, dangerThreshold)   + sigma * 0.5f
        } else {
            yMin = min(globalMin, dangerThreshold)   - sigma * 0.5f
            yMax = max(globalMax, greenHigh)         + sigma * 0.5f
        }
        yAxis.axisMinimum = yMin
        yAxis.axisMaximum = yMax

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

    /**
     * Génère tous les graphiques overview en itérant sur [Metrics].
     * Seules les métriques présentes dans [ReqStatsResult.extractors] sont incluses.
     */
    fun generateOverviewCharts(stats: List<ReqStatsResult>): List<Pair<String, Bitmap>> =
        Metrics.entries
            .mapNotNull { metric ->
                val extractor = ReqStatsResult.extractors[metric] ?: return@mapNotNull null
                try {
                    metric.csv_code to generateMetricChart(
                        stats, extractor, metric.csv_code, metric.higher_is_bad
                    )
                } catch (_: Exception) { null }
            }

    private fun configureChart(chart: LineChart, title: String) {
        chart.description.text     = title
        chart.description.textSize = 16f
        chart.setTouchEnabled(false)
        chart.isDragEnabled        = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled     = true
        chart.legend.textSize      = 11f

        val xAxis = chart.xAxis
        xAxis.position       = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize       = 12f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor      = 0xFFE0E0E0.toInt()
        xAxis.granularity    = 100f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) = "${value.toInt()} km"
        }

        val yAxisLeft = chart.axisLeft
        yAxisLeft.textSize = 12f
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.gridColor = 0xFFE0E0E0.toInt()
        yAxisLeft.setLabelCount(8, false)

        chart.axisRight.isEnabled = false
    }

    /** Écart-type corrigé de Bessel (ddof=1), iso numpy std(ddof=1). */
    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return kotlin.math.sqrt(variance)
    }
}
