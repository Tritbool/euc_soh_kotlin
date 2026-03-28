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
import io.github.eucsoh.model.PlotData
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.createBitmap
import io.github.eucsoh.android.visualization.SohTrendCusumChartGenerator.Companion.addMosfetRefLine
import io.github.eucsoh.android.visualization.SohTrendCusumChartGenerator.Companion.addPackRefLine
import io.github.eucsoh.android.visualization.SohTrendCusumChartGenerator.Companion.expandAxisForRefLine

/**
 * Génère les graphiques gaussiens SoH.
 * Consomme [PlotData] pré-calculé par le core — aucun recalcul ici.
 */
class SohChartGeneratorFixed(private val context: Context) {

    companion object {
        const val CHART_WIDTH  = 1200
        const val CHART_HEIGHT = 800

        const val N_SIGMA_BAND    = 1.0f
        const val N_SIGMA_WARNING = 2.0f
        const val N_SIGMA_DANGER  = 2.0f

        const val COLOR_GREEN     = 0xFF4CAF50.toInt()
        const val COLOR_ORANGE    = 0xFFFF9800.toInt()
        const val COLOR_RED       = 0xFFFF0000.toInt()
        const val COLOR_DATA_BLUE = 0xFF2196F3.toInt()

        fun resolveLabel(csvCode: String): String =
            Metrics.entries.find { it.csv_code == csvCode }?.label ?: csvCode
    }

    fun generateMetricChart(
        plotData: PlotData,
        metric: Metrics
    ): Bitmap {
        val pts = plotData.series[metric]
        require(!pts.isNullOrEmpty() && pts.size >= 3) {
            "Insufficient data points (need >= 3)"
        }
        val gauss = plotData.gaussianResults[metric]
        require(gauss != null) { "No gaussian result for $metric" }

        val mu    = gauss.mu.toFloat()
        val sigma = gauss.sigma.toFloat()
        val higherIsBad = gauss.higherIsBad

        val yVals = pts.map { it.second.toFloat() }
        val globalMin = yVals.minOrNull()!!
        val globalMax = yVals.maxOrNull()!!

        val greenLow         = if (higherIsBad) mu - N_SIGMA_BAND * sigma    else mu + N_SIGMA_BAND * sigma
        val greenHigh        = if (higherIsBad) mu + N_SIGMA_BAND * sigma    else mu - N_SIGMA_BAND * sigma
        val warningThreshold = if (higherIsBad) mu + N_SIGMA_WARNING * sigma else mu - N_SIGMA_WARNING * sigma
        val dangerThreshold  = if (higherIsBad) mu + N_SIGMA_DANGER  * sigma else mu - N_SIGMA_DANGER  * sigma

        val chart = LineChart(context)
        configureChart(chart, resolveLabel(metric.csv_code))

        val entries = pts.map { (x, y) -> Entry(x.toFloat(), y.toFloat()) }
        val dataSet = LineDataSet(entries, resolveLabel(metric.csv_code)).apply {
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
            textColor = COLOR_ORANGE; textSize = 10f
        })
        yAxis.addLimitLine(LimitLine(warningThreshold, "±2σ").apply {
            lineColor = COLOR_ORANGE; lineWidth = 1.5f
            textColor = COLOR_ORANGE; textSize = 10f
        })
        yAxis.addLimitLine(
            LimitLine(dangerThreshold, "Danger: ${String.format(Locale.getDefault(),"%.3f", dangerThreshold)}").apply {
                lineColor = COLOR_RED; lineWidth = 3f
                enableDashedLine(12f, 8f, 0f)
                textColor = COLOR_RED; textSize = 11f
            })

        if (higherIsBad) {
            yAxis.axisMinimum = min(globalMin, greenLow)        - sigma * 0.5f
            yAxis.axisMaximum = max(globalMax, dangerThreshold) + sigma * 0.5f
        } else {
            yAxis.axisMinimum = min(globalMin, dangerThreshold) - sigma * 0.5f
            yAxis.axisMaximum = max(globalMax, greenHigh)       + sigma * 0.5f
        }

        when (metric) {
            Metrics.R_MOSFET_HOT -> plotData.mosfetRdsOn25cRef?.let {
                expandAxisForRefLine(chart.axisLeft, it.toFloat())
                addMosfetRefLine(chart.axisLeft, it)
            }
            Metrics.R_BATT_MEDIAN_25C, Metrics.R_BATT_MEDIAN -> plotData.battPackRNominal?.let {
                expandAxisForRefLine(chart.axisLeft, it.toFloat())
                addPackRefLine(chart.axisLeft, it)
            }
            else -> {}
        }

        return renderToBitmap(chart)
    }

    fun generateOverviewCharts(plotData: PlotData): List<Pair<String, Bitmap>> =
        Metrics.entries.mapNotNull { metric ->
            if (plotData.gaussianResults[metric] == null) return@mapNotNull null
            if (plotData.series[metric].isNullOrEmpty()) return@mapNotNull null
            try {
                metric.csv_code to generateMetricChart(plotData, metric)
            } catch (_: Exception) {
                null
            }
        }

    private fun configureChart(chart: LineChart, title: String) {
        chart.description.text = title
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
        //xAxis.granularity = 100f
        xAxis.isGranularityEnabled = false
        xAxis.setLabelCount(6, false)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float) = "${value.toInt()} km"
        }

        val yAxis = chart.axisLeft
        yAxis.textSize = 12f
        yAxis.setDrawGridLines(true)
        yAxis.gridColor = 0xFFE0E0E0.toInt()
        yAxis.setLabelCount(8, false)

        chart.axisRight.isEnabled = false
    }

    private fun renderToBitmap(chart: LineChart): Bitmap {
        chart.measure(
            View.MeasureSpec.makeMeasureSpec(CHART_WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(CHART_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        chart.layout(0, 0, CHART_WIDTH, CHART_HEIGHT)
        val bitmap = createBitmap(CHART_WIDTH, CHART_HEIGHT)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        chart.draw(canvas)
        return bitmap
    }
}
