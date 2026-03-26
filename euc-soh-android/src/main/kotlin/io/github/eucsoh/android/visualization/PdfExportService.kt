package io.github.eucsoh.android.visualization

import android.content.Context
import android.graphics.Bitmap
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import io.github.eucsoh.SohAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.properties.AreaBreakType

class PdfExportService(private val context: Context) {

    companion object {
        private const val TAG = "PdfExportService"
        // Police embarquée dans Android, toujours disponible
        private const val FONT = "fonts/NotoSans-Regular.ttf"
    }

    suspend fun exportToPdf(
        gaussCharts: List<Pair<String, Bitmap>>?,
        inflexionCharts: List<Pair<String, Bitmap>>?,
        cusumCharts: List<Pair<String, Bitmap>>?,
        trendCharts: List<Pair<String, Bitmap>>?,
        result: SohAnalyzer.AnalysisResult,
        wheelName: String,
        macAddress: String,
        outputFileName: String? = null
    ): File = withContext(Dispatchers.IO) {

        if (gaussCharts.isNullOrEmpty())
            throw IllegalArgumentException("No charts to export")

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = outputFileName ?: "${wheelName}-${macAddress}_SoH_${timestamp}.pdf"
        val outputDir = File(context.getExternalFilesDir(null), "EUC_SoH").also { it.mkdirs() }
        val outputFile = File(outputDir, fileName)

        val pdfDoc = PdfDocument(PdfWriter(outputFile))
        // A4 landscape
        val pageSize = PageSize.A4.rotate()
        val document = Document(pdfDoc, pageSize)
        document.setMargins(30f, 30f, 30f, 30f)

        // ── Page 1 : Titre ──────────────────────────────────────────────
        document.add(
            Paragraph("EUC State of Health Report")
                .setFontSize(28f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(100f)
        )
        document.add(
            Paragraph(wheelName)
                .setFontSize(20f)
                .setTextAlignment(TextAlignment.CENTER)
        )
        document.add(
            Paragraph("MAC: $macAddress")
                .setFontSize(12f)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
        )
        document.add(
            Paragraph("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
                .setFontSize(12f)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
        )
        if (result.alarms.isNotEmpty()) {
            document.add(
                Paragraph("⚠ ${result.alarms.size} alarm(s) detected")
                    .setFontSize(14f)
                    .setBold()
                    .setFontColor(DeviceRgb(0xE5, 0x39, 0x35))  // Material red
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20f)
            )
        }

        // ── Page 2 : Tableau des stats ───────────────────────────────────
        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        document.add(
            Paragraph("Analysis Statistics")
                .setFontSize(16f)
                .setBold()
                .setMarginBottom(12f)
        )
        addStatsTable(document, result, wheelName)

        // ── Pages charts ─────────────────────────────────────────────────
        addChartSection(document, pdfDoc, gaussCharts, "Gaussian Bands (±1σ / ±2σ)", pageSize)

        if (!trendCharts.isNullOrEmpty()) {
            addChartSection(document, pdfDoc, trendCharts, "Trend (linear regression)", pageSize)
        }
        if (!cusumCharts.isNullOrEmpty()) {
            addChartSection(document, pdfDoc, cusumCharts, "CUSUM (change detection)", pageSize)
        }
        if (!inflexionCharts.isNullOrEmpty()) {
            addChartSection(document, pdfDoc, inflexionCharts, "Inflexion (regime change)", pageSize)
        }

        document.close()
        outputFile
    }

    // ────────────────────────────────────────────────────────────────────
    // Tableau des statistiques
    // ────────────────────────────────────────────────────────────────────
    private fun addStatsTable(
        document: Document,
        result: SohAnalyzer.AnalysisResult,
        wheelName: String
    ) {
        val summary = buildSummary(result, wheelName)
        val logs = summary.logs
        if (logs.isEmpty()) return

        val headers = logs.first().keys.toList()
        val colCount = headers.size

        // Tableau full-width avec colonnes équilibrées
        val table = Table(UnitValue.createPercentArray(FloatArray(colCount) { 1f }))
            .useAllAvailableWidth()
            .setFontSize(7f)

        // En-têtes
        headers.forEach { header ->
            table.addHeaderCell(
                Cell().add(
                    Paragraph(header)
                        .setBold()
                        .setFontSize(7f)
                        .setTextAlignment(TextAlignment.CENTER)
                ).setBackgroundColor(DeviceRgb(0x42, 0x42, 0x42))
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(3f)
            )
        }

        // Lignes de données
        logs.forEachIndexed { rowIdx, row ->
            val bg = if (rowIdx % 2 == 0) DeviceRgb(0xFF, 0xFF, 0xFF)
            else DeviceRgb(0xF5, 0xF5, 0xF5)
            headers.forEach { col ->
                val value = formatValue(row[col])
                // Colorise les valeurs numériques extrêmes si souhaité
                table.addCell(
                    Cell().add(
                        Paragraph(value)
                            .setFontSize(7f)
                            .setTextAlignment(TextAlignment.RIGHT)
                    ).setBackgroundColor(bg)
                        .setPadding(2f)
                )
            }
        }

        document.add(table)

        // Ligne Ea en dessous du tableau
        document.add(
            Paragraph("Arrhenius activation energy: ${"%.2f".format(summary.arrhenius.eaKjPerMol)} kJ/mol")
                .setFontSize(9f)
                .setItalic()
                .setMarginTop(6f)
        )
    }

    // ────────────────────────────────────────────────────────────────────
    // Section de charts : page de titre de section + 1 chart par page
    // ────────────────────────────────────────────────────────────────────
    private fun addChartSection(
        document: Document,
        pdfDoc: PdfDocument,
        charts: List<Pair<String, Bitmap>>,
        sectionTitle: String,
        pageSize: PageSize
    ) {
        // Page de titre de section
        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))

        document.add(
            Paragraph(sectionTitle)
                .setFontSize(22f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(120f)
        )

        // 1 chart par page
        charts.forEach { (metricCode, bitmap) ->
            document.add(AreaBreak(AreaBreakType.NEXT_PAGE))


            // Label de la métrique en titre de page
            val label = io.github.eucsoh.Constants.Metrics.entries
                .find { it.csv_code == metricCode }?.label ?: metricCode
            document.add(
                Paragraph(label)
                    .setFontSize(13f)
                    .setBold()
                    .setMarginBottom(6f)
            )

            // Bitmap → PNG bytes → iText Image
            val pngBytes = bitmapToPng(bitmap)
            val imgData = ImageDataFactory.create(pngBytes)
            val img = Image(imgData)

            // Calcule la taille max disponible sur la page (page - marges - titre)
            val availW = pageSize.width - 60f   // 2 × marge 30pt
            val availH = pageSize.height - 90f  // marges + titre
            img.scaleToFit(availW, availH)
            img.setHorizontalAlignment(HorizontalAlignment.CENTER)

            document.add(img)
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Bitmap → ByteArray PNG sans perte
    // ────────────────────────────────────────────────────────────────────
    private fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val baos = ByteArrayOutputStream()
        // PNG = lossless, qualité maximale
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    private fun formatValue(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value.isNaN() || value.isInfinite()) "N/A" else "%.4f".format(value)
        is Float  -> if (value.isNaN() || value.isInfinite()) "N/A" else "%.4f".format(value)
        is Number -> value.toString()
        is Boolean -> if (value) "✓" else "✗"
        else -> value.toString()
    }

    private fun buildSummary(
        result: SohAnalyzer.AnalysisResult,
        wheelName: String
    ): SohAnalyzer.SummaryData {
        val analyzer = io.github.eucsoh.SohAnalyzer(
            csvSource = null, mosfetParams = null,
            logger = object : io.github.eucsoh.Logger {
                override fun d(tag: String, message: String) {}
                override fun e(tag: String, message: String, throwable: Throwable?) {}
            }
        )
        return analyzer.buildSummary(result, wheelName)
    }
}
