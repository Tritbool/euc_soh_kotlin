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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Génère les graphiques Trend, CUSUM et Slope Inflexion pour le rapport SoH.
 * Iso soh_core_en.py : plot_trend_linear(), plot_cusum(), plot_slope_inflexion().
 *
 * Les métadonnées (label, higher_is_bad) viennent de [Metrics] (core).
 * Le pont métrique ↔ champ ReqStatsResult est fourni par
 * [ReqStatsResult.extractors].
 */
class SohTrendCusumChartGenerator(private val context: Context) {

    companion object {
        const val CHART_WIDTH  = 1200
        const val CHART_HEIGHT = 800

        const val COLOR_DATA_BLUE        = 0xFF2196F3.toInt()
        const val COLOR_ALARM_RED        = 0xFFE53935.toInt()
        const val COLOR_SLOW_BLUE        = 0xFF1565C0.toInt()
        const val COLOR_INFLEXION_ORANGE = 0xFFFF6F00.toInt()
        const val COLOR_MU_REF           = 0xFF4CAF50.toInt()
        const val COLOR_MU_SIGMA         = 0xFFFF9800.toInt()
        const val COLOR_THRESHOLD_H      = 0xFFFF0000.toInt()
        const val COLOR_DANGER           = 0xFFFF0000.toInt()
        const val COLOR_REGRESSION       = 0xFFFF6F00.toInt()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. TREND
    // ─────────────────────────────────────────────────────────────────────────────

    fun generateTrendChart(
        stats: List<ReqStatsResult>,
        metric: Metrics,
        extractor: (ReqStatsResult) -> Double?,
        wheelName: String = ""
    ): Bitmap {
        val valid = stats
            .filter { it.wheelKm != null && extractor(it) != null }
            .sortedBy { it.wheelKm }
        require(valid.size >= 5) { "Insufficient data for trend (need >= 5)" }

        val xVals = valid.map { it.wheelKm!!.toFloat() }
        val yVals = valid.map { extractor(it)!!.toFloat() }

        val n   = xVals.size
        val sx  = xVals.sumOf { it.toDouble() }
        val sy  = yVals.sumOf { it.toDouble() }
        val sxy = xVals.zip(yVals).sumOf { (x, y) -> x.toDouble() * y.toDouble() }
        val sx2 = xVals.sumOf { it.toDouble() * it.toDouble() }
        val denom = n * sx2 - sx * sx
        val slope     = if (denom != 0.0) (n * sxy - sx * sy) / denom else 0.0
        val intercept = (sy - slope * sx) / n

        val slopePerThousand = slope * 1000.0
        val sign = if (slopePerThousand >= 0) "+" else ""
        val label = metric.label ?: metric.csv_code

        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty()) "$wheelName - Trend $label" else "Trend $label"
        )

        val dataEntries = xVals.zip(yVals).map { (x, y) -> Entry(x, y) }
        val dataSet = LineDataSet(dataEntries, "Data").apply {
            color = COLOR_DATA_BLUE; setCircleColor(COLOR_DATA_BLUE)
            lineWidth = 2f; circleRadius = 4f; setDrawValues(false)
            mode = LineDataSet.Mode.LINEAR
        }

        val xMin = xVals.first(); val xMax = xVals.last()
        val regressionSet = LineDataSet(
            listOf(
                Entry(xMin, (slope * xMin + intercept).toFloat()),
                Entry(xMax, (slope * xMax + intercept).toFloat())
            ),
            "Trend: $sign${String.format("%.4f", slopePerThousand)} /1000 km"
        ).apply {
            color = COLOR_REGRESSION; lineWidth = 2.5f
            setDrawCircles(false); setDrawValues(false)
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
        stats: List<ReqStatsResult>,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        metricsWithExtractors().mapNotNull { (metric, extractor) ->
            try { metric.csv_code to generateTrendChart(stats, metric, extractor, wheelName) }
            catch (_: Exception) { null }
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. CUSUM
    // ─────────────────────────────────────────────────────────────────────────────

    fun generateCusumChart(
        stats: List<ReqStatsResult>,
        metric: Metrics,
        extractor: (ReqStatsResult) -> Double?,
        wheelName: String = "",
        hSigma: Double = 5.0,
        kSigma: Double = 1.0
    ): Bitmap {
        val valid = stats
            .filter { it.wheelKm != null && extractor(it) != null }
            .sortedBy { it.wheelKm }
        require(valid.size >= 5) { "Insufficient data for CUSUM (need >= 5)" }

        val xVals    = valid.map { it.wheelKm!!.toFloat() }
        val yVals    = valid.map { extractor(it)!!.toFloat() }
        val yDoubles = yVals.map { it.toDouble() }.sorted()
        val nRef     = max(3, (yDoubles.size * 0.5).toInt())
        val yRefOpt  = yDoubles.take(nRef)
        val muRef    = yRefOpt.average()
        val sigmaRef = run {
            val v = yRefOpt.sumOf { (it - muRef) * (it - muRef) } / (yRefOpt.size - 1)
            sqrt(v)
        }

        val alarmSet: Set<Int> = run {
            val k = kSigma * sigmaRef
            val hNormal = hSigma * sigmaRef
            var s = 0.0; var regimeMu = muRef
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

        val normalEntries = mutableListOf<Entry>()
        val alarmEntries  = mutableListOf<Entry>()
        var inAlarm = false
        for (i in xVals.indices) {
            if (i in alarmSet) inAlarm = true
            if (inAlarm) alarmEntries.add(Entry(xVals[i], yVals[i]))
            else         normalEntries.add(Entry(xVals[i], yVals[i]))
        }
        if (alarmEntries.isEmpty()) {
            normalEntries.clear()
            xVals.zip(yVals).forEach { (x, y) -> normalEntries.add(Entry(x, y)) }
        }

        val label = metric.label ?: metric.csv_code
        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty()) "$wheelName - CUSUM $label" else "CUSUM $label"
        )

        val datasets = mutableListOf<LineDataSet>()
        if (normalEntries.isNotEmpty())
            datasets.add(LineDataSet(normalEntries, "Normal").apply {
                color = COLOR_DATA_BLUE; setCircleColor(COLOR_DATA_BLUE)
                lineWidth = 2f; circleRadius = 4f; setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        if (alarmEntries.isNotEmpty())
            datasets.add(LineDataSet(alarmEntries, "Change detected (CUSUM)").apply {
                color = COLOR_ALARM_RED; setCircleColor(COLOR_ALARM_RED)
                lineWidth = 2f; circleRadius = 5f; setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })

        chart.data = LineData(datasets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)

        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(LimitLine(muRef.toFloat(),
            "µ_ref = ${String.format("%.4f", muRef)}").apply {
            lineColor = COLOR_MU_REF; lineWidth = 2f
            textColor = COLOR_MU_REF; textSize  = 10f
        })
        val muSigmaLine = (muRef + 1.5 * sigmaRef).toFloat()
        yAxis.addLimitLine(LimitLine(muSigmaLine, "µ_ref + 1.5σ").apply {
            lineColor = COLOR_MU_SIGMA; lineWidth = 1.5f
            textColor = COLOR_MU_SIGMA; textSize  = 10f
            enableDashedLine(10f, 6f, 0f)
        })
        val hLine = (muRef + hSigma * sigmaRef).toFloat()
        yAxis.addLimitLine(LimitLine(hLine, "CUSUM threshold h").apply {
            lineColor = COLOR_THRESHOLD_H; lineWidth = 1.5f
            textColor = COLOR_THRESHOLD_H; textSize  = 10f
            enableDashedLine(14f, 8f, 0f)
        })

        val yMin = min(yVals.minOrNull()!!, muRef.toFloat()) - sigmaRef.toFloat() * 0.3f
        val yMax = max(yVals.maxOrNull()!!, hLine)           + sigmaRef.toFloat() * 0.3f
        yAxis.axisMinimum = yMin
        yAxis.axisMaximum = yMax

        return renderToBitmap(chart)
    }

    fun generateAllCusumCharts(
        stats: List<ReqStatsResult>,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        metricsWithExtractors().mapNotNull { (metric, extractor) ->
            try { metric.csv_code to generateCusumChart(stats, metric, extractor, wheelName) }
            catch (_: Exception) { null }
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3. SLOPE INFLEXION
    // ─────────────────────────────────────────────────────────────────────────────

    fun generateInflexionChart(
        stats: List<ReqStatsResult>,
        metric: Metrics,
        extractor: (ReqStatsResult) -> Double?,
        wheelName: String = "",
        dangerLimit: Double? = null,
        windowKm: Double = 1500.0
    ): Bitmap {
        val highIsBad = metric.higher_is_bad
        val valid = stats
            .filter { it.wheelKm != null && extractor(it) != null }
            .sortedBy { it.wheelKm }
        require(valid.size >= 10) { "Insufficient data for inflexion (need >= 10)" }

        val xVals    = valid.map { it.wheelKm!!.toFloat() }
        val yVals    = valid.map { extractor(it)!!.toFloat() }
        val yDoubles = yVals.map { it.toDouble() }
        val mean     = yDoubles.average()
        val stdDev   = sqrt(yDoubles.sumOf { (it - mean) * (it - mean) } / (yDoubles.size - 1))
        val limit    = dangerLimit ?: (mean + 1.25 * stdDev)

        val spanKm  = xVals.last() - xVals.first()
        val kmCut   = xVals.first() + spanKm / 3f
        val baseX   = xVals.filter { it <= kmCut }.map { it.toDouble() }
        val baseY   = xVals.zip(yVals).filter { (x, _) -> x <= kmCut }.map { (_, y) -> y.toDouble() }

        val slopeBase: Double = if (baseX.size >= 5) {
            val nb = baseX.size
            val sbx = baseX.sum(); val sby = baseY.sum()
            val sbxy = baseX.zip(baseY).sumOf { (x, y) -> x * y }
            val sbx2 = baseX.sumOf { it * it }
            val d = nb * sbx2 - sbx * sbx
            if (d != 0.0) (nb * sbxy - sbx * sby) / d else 0.0
        } else 0.0
        val slopeThreshold = slopeBase * 1.5

        val halfW = (windowKm / 2.0).toFloat()
        val slowEntries      = mutableListOf<Entry>()
        val inflexionEntries = mutableListOf<Entry>()

        for (i in xVals.indices) {
            val kmI = xVals[i]
            val windowX = xVals.filter { abs(it - kmI) <= halfW }.map { it.toDouble() }
            val windowY = xVals.zip(yVals).filter { (x, _) -> abs(x - kmI) <= halfW }.map { (_, y) -> y.toDouble() }

            val isInflexion = if (windowX.size >= 5) {
                val nw = windowX.size
                val swx = windowX.sum(); val swy = windowY.sum()
                val swxy = windowX.zip(windowY).sumOf { (x, y) -> x * y }
                val swx2 = windowX.sumOf { it * it }
                val dw = nw * swx2 - swx * swx
                val localSlope = if (dw != 0.0) (nw * swxy - swx * swy) / dw else 0.0
                val fracAbove  = windowY.count { it > limit }.toDouble() / windowY.size
                if (highIsBad) localSlope > slopeThreshold && fracAbove >= 0.6
                else           localSlope <= slopeThreshold && fracAbove < 0.6
            } else {
                if (highIsBad) yVals[i] > limit else yVals[i] < limit
            }

            val entry = Entry(xVals[i], yVals[i])
            if (isInflexion) inflexionEntries.add(entry) else slowEntries.add(entry)
        }

        val label = metric.label ?: metric.csv_code
        val chart = LineChart(context)
        configureChart(
            chart,
            title = if (wheelName.isNotEmpty()) "$wheelName - $label" else label
        )

        val datasets = mutableListOf<LineDataSet>()
        if (slowEntries.isNotEmpty())
            datasets.add(LineDataSet(slowEntries, "Slow regime").apply {
                color = COLOR_SLOW_BLUE; setCircleColor(COLOR_SLOW_BLUE)
                lineWidth = 0f; circleRadius = 5f; setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })
        if (inflexionEntries.isNotEmpty())
            datasets.add(LineDataSet(inflexionEntries, "Sustained inflexion").apply {
                color = COLOR_INFLEXION_ORANGE; setCircleColor(COLOR_INFLEXION_ORANGE)
                lineWidth = 0f; circleRadius = 5f; setDrawValues(false)
                mode = LineDataSet.Mode.LINEAR
            })

        chart.data = LineData(datasets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)

        val yAxis = chart.axisLeft
        yAxis.removeAllLimitLines()
        yAxis.addLimitLine(LimitLine(limit.toFloat(),
            "danger threshold ≈ ${String.format("%.2f", limit)}").apply {
            lineColor = COLOR_DANGER; lineWidth = 2f
            textColor = COLOR_DANGER; textSize  = 11f
            enableDashedLine(12f, 8f, 0f)
        })

        val margin = stdDev.toFloat() * 0.5f
        val yMin: Float; val yMax: Float
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

    fun generateAllInflexionCharts(
        stats: List<ReqStatsResult>,
        wheelName: String = ""
    ): List<Pair<String, Bitmap>> =
        metricsWithExtractors().mapNotNull { (metric, extractor) ->
            try { metric.csv_code to generateInflexionChart(stats, metric, extractor, wheelName) }
            catch (_: Exception) { null }
        }

    // ─────────────────────────────────────────────────────────────────────────────
    // UTILITAIRES PRIVÉS
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Retourne les métriques disponibles avec leur extracteur, en itérant sur
     * [Metrics.entries] et en utilisant [ReqStatsResult.extractors] comme pont.
     * Seules les métriques ayant un extracteur sont incluses.
     */
    private fun metricsWithExtractors(): List<Pair<Metrics, (ReqStatsResult) -> Double?>> =
        Metrics.entries.mapNotNull { metric ->
            val ext = ReqStatsResult.extractors[metric] ?: return@mapNotNull null
            metric to ext
        }

    private fun configureChart(chart: LineChart, title: String) {
        chart.description.text     = title
        chart.description.textSize = 14f
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
        xAxis.granularity    = 1000f
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
