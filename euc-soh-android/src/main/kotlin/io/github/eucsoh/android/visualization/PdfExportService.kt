package io.github.eucsoh.android.visualization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Environment
import io.github.eucsoh.android.data.model.ReqStatsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        private const val MARGIN = 50
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
        stats: List<ReqStatsResult>,
        wheelName: String,
        outputFileName: String? = null
    ): File = withContext(Dispatchers.IO) {
        if (stats.isEmpty()) {
            throw IllegalArgumentException("Cannot export empty stats")
        }

        val chartGenerator = SohChartGeneratorFixed(context)
        val document = PdfDocument()

        try {
            // Generate overview charts
            val overviewCharts = chartGenerator.generateOverviewCharts(stats)

            // Add each chart as a page
            overviewCharts.forEachIndexed { index, (metricName, bitmap) ->
                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH,
                    PAGE_HEIGHT,
                    index + 1
                ).create()

                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Title
                val paint = android.graphics.Paint()
                paint.textSize = 24f
                paint.color = android.graphics.Color.BLACK
                canvas.drawText(
                    "$wheelName - ",//${SohChartGeneratorFixed.METRIC_LABELS[metricName] ?: metricName}",
                    MARGIN.toFloat(),
                    50f,
                    paint
                )

                // Chart
                val scaledBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    PAGE_WIDTH - 2 * MARGIN,
                    PAGE_HEIGHT - 100,
                    true
                )
                canvas.drawBitmap(
                    scaledBitmap,
                    MARGIN.toFloat(),
                    80f,
                    null
                )

                scaledBitmap.recycle()
                document.finishPage(page)
            }

            // Save PDF
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = outputFileName ?: "${wheelName}_SoH_${timestamp}.pdf"
            val outputDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "EUC_SoH"
            )
            outputDir.mkdirs()

            val outputFile = File(outputDir, fileName)
            FileOutputStream(outputFile).use { fos ->
                document.writeTo(fos)
            }

            outputFile
        } finally {
            document.close()
        }
    }
}
