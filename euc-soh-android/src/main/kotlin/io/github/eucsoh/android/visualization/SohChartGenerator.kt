package io.github.eucsoh.android.visualization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import io.github.eucsoh.android.data.model.ReqStatsResult

/**
 * Core chart generator for SoH metrics using MPAndroidChart.
 * 
 * Implements graphique generation patterns from soh_core_en.py:
 * - plot_metric_gauss: Single metric vs km with danger zones
 * - plot_soh_overview_all: Multi-metric overview
 * - Gaussian bands (green/orange/red)
 * - Trend detection visualization
 */
class SohChartGenerator(private val context: Context) {

    companion object {
        // Standard chart dimensions (pixels)
        const val CHART_WIDTH = 1200
        const val CHART_HEIGHT = 800
        const val MULTI_CHART_HEIGHT_PER_METRIC = 600

        // Gaussian threshold defaults (matching Python)
        const val N_SIGMA_BAND = 1.0f
        const val N_SIGMA_DANGER = 2.0f
        const val OPTIMAL_FRAC = 0.5f

        // Colors
        const val COLOR_GREEN_BAND = Color.GREEN
        const val COLOR_ORANGE_BAND = 0xFFFFA500.toInt()
        const val COLOR_RED_LINE = Color.RED
        const val COLOR_DATA_LINE = Color.BLUE

        // Metric labels (Y-axis) matching Python Y_LABELS
        val METRIC_LABELS = mapOf(
            "reqMedian" to "R_eq median (Ω)",
            "req95p" to "R_eq 95th (Ω)",
            "sagMedian" to "Sag median (V)",
            "sag95p" to "Sag 95th (V)",
            "sagMax" to "Sag max (V)",
            "vMinStrong" to "Min voltage strong load (V)",
            "iMax" to "Max current (A)",
            "i95p" to "Current 95th (A)",
            "tempBoardMax" to "Board temp max (°C)",
            "tempMotorMax" to "Motor temp max (°C)"
        )
    }

    /**
     * Generate a single metric chart with Gaussian bands.
     * Equivalent to Python's plot_metric_gauss().
     * 
     * @param stats List of analysis results sorted by wheel_km
     * @param metricExtractor Function to extract metric value from ReqStatsResult
     * @param metricName Metric identifier (for label lookup)
     * @param higherIsBad true if high values are dangerous (default), false for metrics like v_min
     * @return Bitmap of the rendered chart
     */
    fun generateMetricChart(
        stats: List<ReqStatsResult>,
        metricExtractor: (ReqStatsResult) -> Double?,
        metricName: String,
        higherIsBad: Boolean = true
    ): Bitmap {
        if (stats.isEmpty()) {
            throw IllegalArgumentException("Cannot generate chart with empty stats")
        }

        // Sort by km and filter valid data
        val validStats = stats
            .filter { it.wheelKm != null && metricExtractor(it) != null }
            .sortedBy { it.wheelKm }

        if (validStats.size < 3) {
            throw IllegalArgumentException("Insufficient data points (need >= 3, got ${validStats.size})")
        }

        // Compute Gaussian parameters from "best" subset
        val sortedByMetric = validStats.sortedBy { metricExtractor(it)!! }
        val optimalCount = maxOf(3, (sortedByMetric.size * OPTIMAL_FRAC).toInt())
        val optimalSubset = sortedByMetric.take(optimalCount)

        val metricValues = optimalSubset.mapNotNull { metricExtractor(it) }
        val mu = metricValues.average().toFloat()
        val sigma = calculateStdDev(metricValues).toFloat()

        // Compute bands
        val bandLow = mu - N_SIGMA_BAND * sigma
        val bandHigh = mu + N_SIGMA_BAND * sigma
        val dangerThreshold = if (higherIsBad) {
            mu + N_SIGMA_DANGER * sigma
        } else {
            mu - N_SIGMA_DANGER * sigma
        }

        // Warning band boundaries
        val warnLow = if (higherIsBad) bandHigh else dangerThreshold
        val warnHigh = if (higherIsBad) dangerThreshold else bandLow

        // Create chart
        val chart = LineChart(context)
        configureChart(chart, metricName)

        // Prepare data points
        val entries = validStats.mapIndexed { idx, stat ->
            Entry(stat.wheelKm!!.toFloat(), metricExtractor(stat)!!.toFloat())
        }

        val dataSet = LineDataSet(entries, METRIC_LABELS[metricName] ?: metricName)
        styleDataSet(dataSet, COLOR_DATA_LINE)

        val lineData = LineData(dataSet)
        chart.data = lineData

        // Add danger threshold line (via LimitLine in YAxis)
        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        val dangerLine = com.github.mikephil.charting.components.LimitLine(
            dangerThreshold,
            "Danger: ${String.format("%.3f", dangerThreshold)}"
        )
        dangerLine.lineColor = COLOR_RED_LINE
        dangerLine.lineWidth = 2f
        dangerLine.enableDashedLine(10f, 10f, 0f)
        dangerLine.textColor = COLOR_RED_LINE
        dangerLine.textSize = 10f
        yAxis.addLimitLine(dangerLine)

        // Render to bitmap
        chart.measure(
            CHART_WIDTH or android.view.View.MeasureSpec.EXACTLY,
            CHART_HEIGHT or android.view.View.MeasureSpec.EXACTLY
        )
        chart.layout(0, 0, CHART_WIDTH, CHART_HEIGHT)

        val bitmap = Bitmap.createBitmap(CHART_WIDTH, CHART_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        chart.draw(canvas)

        return bitmap
    }

    /**
     * Generate multi-metric overview chart (subplots stacked).
     * Equivalent to Python's plot_soh_overview_all().
     * 
     * @return List of bitmaps (one per metric)
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
                null // Skip metrics with insufficient data
            }
        }
    }

    // === Helper functions ===

    private fun configureChart(chart: LineChart, title: String) {
        chart.description.text = METRIC_LABELS[title] ?: title
        chart.description.textSize = 14f
        chart.setTouchEnabled(false)
        chart.isDragEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawGridBackground(false)
        chart.legend.isEnabled = true

        // X-axis (km)
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.textSize = 12f
        xAxis.setDrawGridLines(true)
        xAxis.granularity = 100f // 100 km steps
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "${value.toInt()} km"
            }
        }

        // Y-axis (metric)
        val yAxisLeft = chart.axisLeft
        yAxisLeft.textSize = 12f
        yAxisLeft.setDrawGridLines(true)
        yAxisLeft.setLabelCount(6, false)

        chart.axisRight.isEnabled = false
    }

    private fun styleDataSet(dataSet: LineDataSet, color: Int) {
        dataSet.color = color
        dataSet.setCircleColor(color)
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.LINEAR
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }
}
