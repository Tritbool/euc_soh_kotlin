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

        // Page alarmes (toujours présente, même si vide — ça rassure l'utilisateur)
        addAlarmsPage(document, result)

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

        val allHeaders = logs.first().keys.toList()

        // Colonnes meta : tout ce qui n'est pas purement numérique
        // On considère "meta" : file, source, datetime_first, wheel_km, wheel_km_source, Ns, soc_ref_ok, soc_ref_v_full
        val META_COLS = setOf(
            "file", "source", "datetime_first", "wheel_km", "wheel_km_source",
            "Ns", "soc_ref_ok", "soc_ref_v_full", "n_points"
        )
        val metaHeaders   = allHeaders.filter { it in META_COLS }
        val metricHeaders = allHeaders.filter { it !in META_COLS }

        // ── Tableau 1 : colonnes meta ───────────────────────────────────────
        document.add(
            Paragraph("File metadata")
                .setFontSize(11f).setBold().setMarginBottom(6f)
        )
        document.add(buildTable(logs, metaHeaders, colWidthPt = 80f))

        document.add(
            Paragraph("Arrhenius activation energy: ${"%.2f".format(summary.arrhenius.eaKjPerMol)} kJ/mol")
                .setFontSize(9f).setItalic().setMarginTop(6f).setMarginBottom(12f)
        )

        // ── Tableau 2 : métriques numériques (nouvelle page) ─────────────────
        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        document.add(
            Paragraph("Metrics per file")
                .setFontSize(11f).setBold().setMarginBottom(6f)
        )
        // Ajoute wheel_km en première colonne pour référence
        val metricHeadersWithRef = (listOf("file", "wheel_km") + metricHeaders).distinct()
        document.add(buildTable(logs, metricHeadersWithRef, colWidthPt = 55f))
    }

    private fun addAlarmsPage(
        document: Document,
        result: SohAnalyzer.AnalysisResult
    ) {
        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        document.add(
            Paragraph("Alarms (${result.alarms.size})")
                .setFontSize(16f).setBold().setMarginBottom(16f)
        )

        if (result.alarms.isEmpty()) {
            document.add(
                Paragraph("No alarms detected.")
                    .setFontSize(11f)
                    .setFontColor(DeviceRgb(0x2E, 0x7D, 0x32))
            )
            return
        }

        document.add(
            Paragraph(
                "The following anomalies were detected during analysis. " +
                        "This report is a decision-support tool — interpretation requires domain expertise."
            )
                .setFontSize(9f).setItalic()
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(12f)
        )

        result.alarms.forEachIndexed { idx, alarm ->
            // Ligne de titre : index + fichier + km
            val kmStr = alarm.wheelKm?.let { " — ${"%.1f".format(it)} km" } ?: ""
            val dateStr = alarm.datetimeFirst?.let { " ($it)" } ?: ""

            val wrapper = Table(floatArrayOf(510f))  // largeur fixe, pleine page A4 landscape - marges
                .setMarginBottom(8f)

            val cell = Cell()
                .setBackgroundColor(DeviceRgb(0xFF, 0xF3, 0xE0))  // orange très pâle
                .setPadding(8f)

            cell.add(
                Paragraph("#${idx + 1}  ${alarm.file}$kmStr$dateStr")
                    .setFontSize(10f).setBold()
                    .setFontColor(DeviceRgb(0xE6, 0x51, 0x00))   // orange foncé
            )
            cell.add(
                Paragraph(alarm.reasons)
                    .setFontSize(9f)
                    .setFontColor(ColorConstants.BLACK)
                    .setMarginTop(4f)
            )

            wrapper.addCell(cell)
            document.add(wrapper)
        }
    }


    /**
     * Construit un tableau iText7 à partir d'une liste de colonnes.
     * Largeur fixe par colonne en points (pas de % — plus prévisible sur pages larges).
     */
    private fun buildTable(
        logs: List<Map<String, Any?>>,
        headers: List<String>,
        colWidthPt: Float
    ): Table {
        val colCount = headers.size
        val table = Table(FloatArray(colCount) { colWidthPt })
            .setFontSize(7f)

        // En-têtes
        headers.forEach { header ->
            table.addHeaderCell(
                Cell().add(
                    Paragraph(header).setBold().setFontSize(7f)
                        .setTextAlignment(TextAlignment.CENTER)
                )
                    .setBackgroundColor(DeviceRgb(0x42, 0x42, 0x42))
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(3f)
            )
        }

        // Lignes
        logs.forEachIndexed { rowIdx, row ->
            val bg = if (rowIdx % 2 == 0) DeviceRgb(0xFF, 0xFF, 0xFF)
            else DeviceRgb(0xF5, 0xF5, 0xF5)
            headers.forEach { col ->
                val raw = row[col]
                val text = formatValue(raw)
                // Aligne à gauche les textes, à droite les nombres
                val align = if (raw is Number || raw is Double || raw is Float)
                    TextAlignment.RIGHT else TextAlignment.LEFT
                table.addCell(
                    Cell().add(
                        Paragraph(text).setFontSize(7f).setTextAlignment(align)
                    )
                        .setBackgroundColor(bg)
                        .setPadding(2f)
                )
            }
        }

        return table
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
        is Double -> if (value.isNaN() || value.isInfinite()) "N/A" else "%.2f".format(value)
        is Float  -> if (value.isNaN() || value.isInfinite()) "N/A" else "%.2f".format(value)
        is Number -> value.toString()
        is Boolean -> if (value) "OK" else "KO"
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
