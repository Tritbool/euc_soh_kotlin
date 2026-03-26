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
import io.github.eucsoh.Constants.CUSUM_METRICS
import io.github.eucsoh.Constants.TREND_METRICS
import io.github.eucsoh.model.PlotData
import kotlin.math.max
import kotlin.math.min

/**
 * Génère les graphiques Trend, CUSUM et Slope Inflexion pour le rapport SoH.
 * Consomme [PlotData] pré-calculé par le core — aucun recalcul ici.
 */
class SohTrendCusumChartGenerator(private val context: Context) {

    companion object {
        const val CHART_WIDTH = 1200
        const val CHART_HEIGHT = 800

        const val COLOR_DATA_BLUE = 0xFF2196F3.toInt()
        const val COLOR_ALARM_RED = 0xFFE53935.toInt()
        const val COLOR_BLACK = 0xFF000000.toInt()
        const val COLOR_SLOW_BLUE = 0xFF1565C0.toInt()
        const val COLOR_INFLEXION_ORANGE = 0xFFFF6F00.toInt()
        const val COLOR_MU_REF = 0xFF4CAF50.toInt()
        const val COLOR_MU_SIGMA = 0xFFFF9800.toInt()
        const val COLOR_THRESHOLD_H = 0xFFFF0000.toInt()
        const val COLOR_DANGER = 0xFFFF0000.toInt()
        const val COLOR_REGRESSION = 0xFFFF6F00.toInt()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. TREND
    // ─────────────────────────────────────────────────────────────────────────────

    fun generateTrendChart(
        plotData: PlotData,
        metric: Metrics,
        wheelName: String = ""
    ): Bitmap {
        val pts = plotData.series[metric]
        require(!pts.isNullOrEmpty() && pts.size >= 5) { "Insufficient data for trend (need >= 5)" }

        val trend = plotData.trendResults[metric]
        require(trend != null) { "No trend result for $metric" }

        val xVals = pts.map { it.first.toFloat() }
        val yVals = pts.map { it.second.toFloat() }

        val label = metric.label ?: metric.csv_code
        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty()) "$wheelName - Trend $label" else "Trend $label"
        )

        val dataEntries = pts.map { (x, y) -> Entry(x.toFloat(), y.toFloat()) }
        val dataSet = LineDataSet(dataEntries, "Data").apply {
            color = COLOR_DATA_BLUE
            setCircleColor(COLOR_DATA_BLUE)
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        val xMin = xVals.first()
        val xMax = xVals.last()
        val slopePerThousand = trend.slope * 1000.0
        val sign = if (slopePerThousand >= 0) "+" else ""
        val regressionSet = LineDataSet(
            listOf(
                Entry(xMin, (trend.slope * xMin + trend.intercept).toFloat()),
                Entry(xMax, (trend.slope * xMax + trend.intercept).toFloat())
            ),
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

        val range = (yVals.maxOrNull()!! - yVals.minOrNull()!!) * 0.05f
        chart.axisLeft.axisMinimum = yVals.minOrNull()!! - range
        chart.axisLeft.axisMaximum = yVals.maxOrNull()!! + range

        return renderToBitmap(chart)
    }

    fun generateAllTrendCharts(
        plotData: PlotData,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        TREND_METRICS.mapNotNull { metric ->
            if (plotData.trendResults[metric] == null || !plotData.trendResults[metric]?.isSignificant!!) return@mapNotNull null
            try {
                metric.csv_code to generateTrendChart(plotData, metric, wheelName)
            } catch (_: Exception) {
                null
            }
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. CUSUM
    // ─────────────────────────────────────────────────────────────────────────────

    fun generateCusumChart(
        plotData: PlotData,
        metric: Metrics,
        wheelName: String = ""
    ): Bitmap {
        val pts = plotData.series[metric]
        require(!pts.isNullOrEmpty() && pts.size >= 5) { "Insufficient data for CUSUM (need >= 5)" }

        val cusum = plotData.cusumResults[metric]
        require(cusum != null) { "No CUSUM result for $metric" }

        val xVals = pts.map { it.first.toFloat() }
        val yVals = pts.map { it.second.toFloat() }

        // Scatter : uniquement les points d'alarme en rouge, tous les autres en bleu
        val normalEntries = mutableListOf<Entry>()
        val alarmEntries = mutableListOf<Entry>()
        for (i in pts.indices) {
            val e = Entry(xVals[i], yVals[i])
            if (i in cusum.alarmIndices) alarmEntries.add(e) else normalEntries.add(e)
        }

        val label = metric.label ?: metric.csv_code
        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty()) "$wheelName - CUSUM $label" else "CUSUM $label"
        )

        val datasets = mutableListOf<LineDataSet>()
        if (normalEntries.isNotEmpty()) {
            datasets.add(LineDataSet(normalEntries, "Normal").apply {
                color = COLOR_DATA_BLUE
                setCircleColor(COLOR_DATA_BLUE)
                circleRadius = 4f
                lineWidth = 0f          // scatter : pas de ligne
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        }
        if (alarmEntries.isNotEmpty()) {
            datasets.add(LineDataSet(alarmEntries, "Change detected (CUSUM)").apply {
                color = COLOR_ALARM_RED
                setCircleColor(COLOR_ALARM_RED)
                setCircleHoleColor(COLOR_BLACK)
                circleRadius = 5f
                lineWidth = 0f          // scatter : pas de ligne
                setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        }

        chart.data = LineData(
            datasets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>
        )

        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(
            LimitLine(
                cusum.muRef.toFloat(),
                "µ_ref = ${String.format("%.4f", cusum.muRef)}"
            ).apply {
                lineColor = COLOR_MU_REF; lineWidth = 2f
                textColor = COLOR_MU_REF; textSize = 10f
            })
        yAxis.addLimitLine(
            LimitLine(
                (cusum.muRef + 1.5 * cusum.sigmaRef).toFloat(),
                "µ_ref + 1.5σ"
            ).apply {
                lineColor = COLOR_MU_SIGMA; lineWidth = 1.5f
                textColor = COLOR_MU_SIGMA; textSize = 10f
                enableDashedLine(10f, 6f, 0f)
            })
        val hLine = (cusum.muRef + cusum.hSigma * cusum.sigmaRef).toFloat()
        yAxis.addLimitLine(
            LimitLine(hLine, "CUSUM threshold h").apply {
                lineColor = COLOR_THRESHOLD_H; lineWidth = 1.5f
                textColor = COLOR_THRESHOLD_H; textSize = 10f
                enableDashedLine(14f, 8f, 0f)
            })

        yAxis.axisMinimum =
            min(yVals.minOrNull()!!, cusum.muRef.toFloat()) - cusum.sigmaRef.toFloat() * 0.3f
        yAxis.axisMaximum =
            max(yVals.maxOrNull()!!, hLine) + cusum.sigmaRef.toFloat() * 0.3f

        return renderToBitmap(chart)
    }

    fun generateAllCusumCharts(
        plotData: PlotData,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        CUSUM_METRICS.mapNotNull { metric ->
            if (plotData.cusumResults[metric] == null || plotData.cusumResults[metric]?.alarmIndices!!.isEmpty()) return@mapNotNull null
            try {
                metric.csv_code to generateCusumChart(plotData, metric, wheelName)
            } catch (_: Exception) {
                null
            }
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. SLOPE INFLEXION
    // ─────────────────────────────────────────────────────────────────────────────

    fun generateInflexionChart(
        plotData: PlotData,
        metric: Metrics,
        wheelName: String = ""
    ): Bitmap {
        val pts = plotData.series[metric]
        require(!pts.isNullOrEmpty() && pts.size >= 10) { "Insufficient data for inflexion (need >= 10)" }

        val inflexion = plotData.inflexionResults[metric]
        require(inflexion != null) { "No inflexion result for $metric" }

        val xVals = pts.map { it.first.toFloat() }
        val yVals = pts.map { it.second.toFloat() }

        val slowEntries = inflexion.slowIndices.map { Entry(xVals[it], yVals[it]) }
        val inflexionEntries = inflexion.inflexionIndices.map { Entry(xVals[it], yVals[it]) }

        val label = metric.label ?: metric.csv_code
        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty()) "$wheelName - $label" else label
        )

        val datasets = mutableListOf<LineDataSet>()
        if (slowEntries.isNotEmpty()) {
            datasets.add(LineDataSet(slowEntries, "Slow regime").apply {
                color = COLOR_SLOW_BLUE
                setCircleColor(COLOR_SLOW_BLUE)
                lineWidth = 0f
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

        chart.data = LineData(
            datasets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>
        )

        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(
            LimitLine(
                inflexion.dangerLimit.toFloat(),
                "danger threshold ≈ ${String.format("%.2f", inflexion.dangerLimit)}"
            ).apply {
                lineColor = COLOR_DANGER; lineWidth = 2f
                textColor = COLOR_DANGER; textSize = 11f
                enableDashedLine(12f, 8f, 0f)
            })

        val stdDev = run {
            val m = yVals.average()
            kotlin.math.sqrt(yVals.sumOf { (it - m) * (it - m) } / (yVals.size - 1))
        }
        val margin = (stdDev * 0.5).toFloat()
        if (metric.higher_is_bad) {
            yAxis.axisMinimum = yVals.minOrNull()!! - margin
            yAxis.axisMaximum = max(yVals.maxOrNull()!!, inflexion.dangerLimit.toFloat()) + margin
        } else {
            yAxis.axisMinimum = min(yVals.minOrNull()!!, inflexion.dangerLimit.toFloat()) - margin
            yAxis.axisMaximum = yVals.maxOrNull()!! + margin
        }

        return renderToBitmap(chart)
    }

    fun generateAllInflexionCharts(
        plotData: PlotData,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        TREND_METRICS.mapNotNull { metric ->
            if (plotData.inflexionResults[metric] == null || plotData.inflexionResults[metric]?.inflexionIndices!!.isEmpty()) return@mapNotNull null
            try {
                metric.csv_code to generateInflexionChart(plotData, metric, wheelName)
            } catch (_: Exception) {
                null
            }
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // UTILITAIRES PRIVÉS
    // ─────────────────────────────────────────────────────────────────────────────

    private fun configureChart(chart: LineChart, title: String) {
        chart.description.text = title
        chart.description.textSize = 14f
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
        xAxis.granularity = 1000f
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
        val bitmap = Bitmap.createBitmap(CHART_WIDTH, CHART_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        chart.draw(canvas)
        return bitmap
    }
}
