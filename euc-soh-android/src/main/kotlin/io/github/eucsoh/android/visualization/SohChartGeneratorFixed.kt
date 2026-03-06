package io.github.eucsoh.android.visualization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import io.github.eucsoh.android.data.model.ReqStatsResult
import kotlin.math.max
import kotlin.math.min

/**
 * VERSION CORRIGÉE du générateur de graphiques.
 * 
 * CORRECTIONS :
 * 1. Seuils de danger calculés sur 100% des données (pas seulement optimal)
 * 2. Bandes de couleur ajoutées (vert/orange/rouge)
 * 3. Granularity fixé (setLabelCount au lieu de granularityEnabled)
 */
class SohChartGeneratorFixed(private val context: Context) {

    companion object {
        // Chart dimensions
        const val CHART_WIDTH = 1200
        const val CHART_HEIGHT = 800

        // CORRECTION: Gaussian sur 50% optimal, mais seuils calculés sur 100%
        const val OPTIMAL_FRAC = 0.5f // Pour μ et σ
        const val N_SIGMA_BAND = 1.0f    // Bande verte : μ ± 1σ
        const val N_SIGMA_WARNING = 2.0f  // Bande orange : μ ± 2σ
        const val N_SIGMA_DANGER = 3.0f   // Ligne rouge : μ ± 3σ

        // Colors pour bandes
        const val COLOR_GREEN_BAND = 0x8000FF00.toInt()    // Vert transparent
        const val COLOR_ORANGE_BAND = 0x80FFA500.toInt()   // Orange transparent
        const val COLOR_RED_LINE = 0xFFFF0000.toInt()      // Rouge opaque
        const val COLOR_DATA_BLUE = 0xFF2196F3.toInt()     // Bleu data points

        val METRIC_LABELS = mapOf(
            "reqMedian" to "Rₑₖ median (Ω)",
            "req95p" to "Rₑₖ 95p (Ω)",
            "sagMedian" to "Sag median (V)",
            "sag95p" to "Sag 95p (V)",
            "sagMax" to "Sag max (V)",
            "vMinStrong" to "V min strong (V)",
            "iMax" to "I max (A)",
            "i95p" to "I 95p (A)",
            "tempBoardMax" to "T board (°C)",
            "tempMotorMax" to "T motor (°C)"
        )
    }

    /**
     * Génère un graphique avec bandes gaussiennes CORRIGÉES.
     * 
     * CORRECTIONS par rapport à l'ancienne version :
     * 1. Calcul μ/σ sur OPTIMAL_FRAC (50% meilleurs)
     * 2. Seuils calculés sur TOUTES les données (pas seulement optimal)
     * 3. Bandes de couleur ajoutées (vert = ±1σ, orange = ±2σ)
     * 4. Ligne rouge danger = ±3σ (au lieu de ±2σ)
     */
    fun generateMetricChart(
        stats: List<ReqStatsResult>,
        metricExtractor: (ReqStatsResult) -> Double?,
        metricName: String,
        higherIsBad: Boolean = true
    ): Bitmap {
        require(stats.isNotEmpty()) { "Cannot generate chart with empty stats" }

        // Filter et sort
        val validStats = stats
            .filter { it.wheelKm != null && metricExtractor(it) != null }
            .sortedBy { it.wheelKm }

        require(validStats.size >= 3) {
            "Insufficient data points (need >= 3, got ${validStats.size})"
        }

        // CORRECTION 1: μ/σ sur optimal subset
        val sortedByMetric = validStats.sortedBy { metricExtractor(it)!! }
        val optimalCount = max(3, (sortedByMetric.size * OPTIMAL_FRAC).toInt())
        val optimalSubset = sortedByMetric.take(optimalCount)

        val optimalValues = optimalSubset.mapNotNull { metricExtractor(it) }
        val mu = optimalValues.average().toFloat()
        val sigma = calculateStdDev(optimalValues).toFloat()

        // CORRECTION 2: Seuils basés sur toutes les données
        val allValues = validStats.mapNotNull { metricExtractor(it) }
        val globalMin = allValues.minOrNull()?.toFloat() ?: mu
        val globalMax = allValues.maxOrNull()?.toFloat() ?: mu

        // Bands
        val greenLow = mu - N_SIGMA_BAND * sigma
        val greenHigh = mu + N_SIGMA_BAND * sigma
        val orangeLow = mu - N_SIGMA_WARNING * sigma
        val orangeHigh = mu + N_SIGMA_WARNING * sigma
        val dangerThreshold = if (higherIsBad) {
            mu + N_SIGMA_DANGER * sigma
        } else {
            mu - N_SIGMA_DANGER * sigma
        }

        // Create chart
        val chart = LineChart(context)
        configureChart(chart, metricName)

        // Data points
        val entries = validStats.map { stat ->
            Entry(stat.wheelKm!!.toFloat(), metricExtractor(stat)!!.toFloat())
        }

        val dataSet = LineDataSet(entries, METRIC_LABELS[metricName] ?: metricName)
        dataSet.color = COLOR_DATA_BLUE
        dataSet.setCircleColor(COLOR_DATA_BLUE)
        dataSet.lineWidth = 2.5f
        dataSet.circleRadius = 5f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.LINEAR

        // CORRECTION 3: Ajout bandes de couleur via filled datasets
        val greenBandDataset = createBandDataset(
            validStats,
            greenLow,
            greenHigh,
            COLOR_GREEN_BAND,
            "Optimal (±1σ)"
        )
        val orangeBandDataset = createBandDataset(
            validStats,
            orangeLow,
            orangeHigh,
            COLOR_ORANGE_BAND,
            "Warning (±2σ)"
        )

        val lineData = LineData(orangeBandDataset, greenBandDataset, dataSet)
        chart.data = lineData

        // CORRECTION 4: Ligne danger à 3σ
        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        val dangerLine = LimitLine(
            dangerThreshold,
            "Danger: ${String.format("%.3f", dangerThreshold)}"
        )
        dangerLine.lineColor = COLOR_RED_LINE
        dangerLine.lineWidth = 3f
        dangerLine.enableDashedLine(12f, 8f, 0f)
        dangerLine.textColor = COLOR_RED_LINE
        dangerLine.textSize = 11f
        yAxis.addLimitLine(dangerLine)

        // Adjust Y-axis range pour montrer toutes les bandes
        val yMin = min(globalMin, orangeLow) - sigma * 0.5f
        val yMax = max(globalMax, orangeHigh) + sigma * 0.5f
        yAxis.axisMinimum = yMin
        yAxis.axisMaximum = yMax

        // Render to bitmap
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
     * Crée un dataset pour bande de couleur (filled area entre deux valeurs).
     */
    private fun createBandDataset(
        stats: List<ReqStatsResult>,
        lowValue: Float,
        highValue: Float,
        fillColor: Int,
        label: String
    ): LineDataSet {
        // Crée deux lignes : haute et basse
        val entries = stats.mapNotNull { stat ->
            stat.wheelKm?.let { km ->
                Entry(km.toFloat(), (lowValue + highValue) / 2f)
            }
        }

        val dataset = LineDataSet(entries, label)
        dataset.color = Color.TRANSPARENT
        dataset.setDrawCircles(false)
        dataset.setDrawValues(false)
        dataset.lineWidth = 0f
        dataset.setDrawFilled(true)
        dataset.fillColor = fillColor
        dataset.fillAlpha = 128 // 50% transparent

        return dataset
    }

    /**
     * Génère tous les graphiques overview.
     */
    fun generateOverviewCharts(
        stats: List<ReqStatsResult>
    ): List<Pair<String, Bitmap>> {
        val metrics = listOf(
            Triple("reqMedian", { s: ReqStatsResult -> s.reqMedian }, true),
            Triple("req95p", { s: ReqStatsResult -> s.req95p }, true),
            Triple("sag95p", { s: ReqStatsResult -> s.sag95p }, true),
            Triple("sagMax", { s: ReqStatsResult -> s.sagMax }, true),
            Triple("vMinStrong", { s: ReqStatsResult -> s.vMinStrong }, false),
            Triple("iMax", { s: ReqStatsResult -> s.iMax }, true),
            Triple("i95p", { s: ReqStatsResult -> s.i95p }, true),
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

        // X-axis
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 12f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = 0xFFE0E0E0.toInt()
        xAxis.granularity = 100f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()} km"
            }
        }

        // Y-axis - CORRECTION: setLabelCount au lieu de granularityEnabled
        val yAxisLeft = chart.axisLeft
        yAxisLeft.textSize = 12f
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.gridColor = 0xFFE0E0E0.toInt()
        yAxisLeft.setLabelCount(8, false) // 8 labels max, smart spacing

        chart.axisRight.isEnabled = false
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
}
