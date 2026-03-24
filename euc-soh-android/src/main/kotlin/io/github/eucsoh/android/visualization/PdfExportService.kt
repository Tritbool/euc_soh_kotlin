package io.github.eucsoh.android.visualization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.util.Log
import io.github.eucsoh.android.data.model.ReqStatsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.scale

/**
 * PDF export service matching Python soh_core_en.py export_soh_pdf().
 * 
 * Generates multi-page PDF with:
 * - Overview metrics
 * - Individual metric charts with Gaussian bands
 * - Summary statistics table
 */
class PdfExportService(private val context: Context) {

    companion object {
        private const val PAGE_WIDTH = 842 // A4 landscape
        private const val PAGE_HEIGHT = 595
        private const val MARGIN = 20

        private const val TAG = "PdfExportService"
    }

    private fun createChartsPages(
        document: PdfDocument,
        idx: Int,
        charts: List<Pair<String, Bitmap>>,
        title: String
    ): Int {
        var last_page = idx
        charts.forEachIndexed { index, (metricName, bitmap) ->
            Log.d(
                TAG,
                "Adding page $index, metric=$metricName, bmp=${bitmap.width}x${bitmap.height}, recycled=${bitmap.isRecycled}"
            )
            val pageInfo = PdfDocument.PageInfo.Builder(
                PAGE_WIDTH,
                PAGE_HEIGHT,
                idx + index + 1
            ).create()
            last_page = idx + index + 1
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // Title
            val paint = android.graphics.Paint()
            paint.textSize = 24f
            paint.color = android.graphics.Color.BLACK
            canvas.drawText(
                title,//${SohChartGeneratorFixed.METRIC_LABELS[metricName] ?: metricName}",
                MARGIN.toFloat(),
                50f,
                paint
            )

            // Chart
            val scaledBitmap = bitmap.scale(PAGE_WIDTH - 2 * MARGIN, PAGE_HEIGHT - 100)
            canvas.drawBitmap(
                scaledBitmap,
                MARGIN.toFloat(),
                80f,
                null
            )

            scaledBitmap.recycle()
            document.finishPage(page)
        }
        return last_page
    }


    private fun titlePage(document: PdfDocument, idx: Int, title: String): Int {

        val pageInfo = PdfDocument.PageInfo.Builder(
            PAGE_WIDTH,
            PAGE_HEIGHT,
            idx
        ).create()

        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paint = android.graphics.Paint()
        paint.textSize = 48f
        paint.color = android.graphics.Color.BLACK
        canvas.drawText(
            title,//${SohChartGeneratorFixed.METRIC_LABELS[metricName] ?: metricName}",
            (PAGE_WIDTH / 2).toFloat(),
            50f,
            paint
        )
        document.finishPage(page)
        return idx + 1
    }

    /**
     * Export SoH analysis to PDF.
     * 
     * @param stats List of analysis results
     * @param wheelName Name of the wheel (for title)
     * @param outputFileName Custom filename (optional)
     * @return File path of generated PDF
     */
    suspend fun exportToPdf(
        gauss_charts: List<Pair<String, Bitmap>>,
        inflexion_charts: List<Pair<String, Bitmap>>,
        cusum_charts: List<Pair<String, Bitmap>>,
        trend_charts: List<Pair<String, Bitmap>>,
        wheelName: String,
        outputFileName: String? = null
    ): File = withContext(Dispatchers.IO) {
        Log.d(TAG, "Exporting to PDF")
        if (gauss_charts.isEmpty()) {
            Log.e(TAG, "Cannot export empty charts")
            throw IllegalArgumentException("Cannot export empty charts")
        }

        val document = PdfDocument()
        var idx: Int = 1
        try {
            Log.d(TAG, "Number of charts : ${gauss_charts.size}")
            // Add each chart as a page

            idx = titlePage(document, idx, "${wheelName} Gaussian charts")

            idx = createChartsPages(document, idx, gauss_charts, "Gaussian charts")

            idx = titlePage(document, idx, "${wheelName} Inflexion charts")

            idx = createChartsPages(document, idx, inflexion_charts, "Inflexion charts")

            idx = titlePage(document, idx, "${wheelName} CUSUM charts")

            idx = createChartsPages(document, idx, cusum_charts, "CUSUM charts")

            idx = titlePage(document, idx, "${wheelName} Trend charts")

            idx = createChartsPages(document, idx, trend_charts, "Trend charts")

            // Save PDF
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = outputFileName ?: "${wheelName}_SoH_${timestamp}.pdf"
            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "EUC_SoH"
            )
            val created = outputDir.mkdirs()
            Log.d(
                TAG,
                "Output dir = ${outputDir.absolutePath}, exists=${outputDir.exists()}, created=$created, canWrite=${outputDir.canWrite()}"
            )


            val outputFile = File(outputDir, fileName)
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
                fos.flush()
                Log.d(
                    TAG,
                    "PDF size = ${outputFile.length()} bytes, path=${outputFile.absolutePath}"
                )

            }

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting PDF", e)
            throw e
        } finally {
            document.close()
        }
    }
}
