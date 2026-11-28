/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.android.visualization

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEvent
import com.itextpdf.kernel.pdf.event.AbstractPdfDocumentEventHandler
import com.itextpdf.kernel.pdf.event.PdfDocumentEvent
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfExportService(private val context: Context) {

    companion object {
        private const val TAG = "PdfExportService"

        // Police embarquée dans Android, toujours disponible
        @Suppress("unused so far")
        private const val FONT = "fonts/NotoSans-Regular.ttf"

        // Marges de page (pt) et bande réservée en bas de chaque page pour la note R_eq
        private const val MARGIN = 30f
        private const val FOOTER_BAND = 46f

        // Note « R_eq = proxy de charge » imprimée en pied de toutes les pages
        private const val FOOTER_FONT_SIZE = 6.5f
        private const val FOOTER_LEADING = 8.2f
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
        Log.d(TAG,"Exporting pdf")
        if (gaussCharts.isNullOrEmpty()) {
            Log.d(TAG, "NO PDF")
            throw IllegalArgumentException("No charts to export")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = outputFileName ?: "${wheelName}-${macAddress}_SoH_${timestamp}.pdf"
        val outputDir = File(context.getExternalFilesDir(null), "EUC_SoH").also { it.mkdirs() }
        val outputFile = File(outputDir, fileName)

        val pdfDoc = PdfDocument(PdfWriter(outputFile))
        // A4 landscape
        val pageSize = PageSize.A4.rotate()
        val document = Document(pdfDoc, pageSize)
        // Marge basse élargie d'une bande réservée : c'est dans cette bande qu'est dessiné
        // le pied de page, il ne peut donc jamais chevaucher le contenu de la page.
        document.setMargins(MARGIN, MARGIN, MARGIN, MARGIN + FOOTER_BAND)

        // Note canonique « R_eq est un proxy de charge, pas une résistance mesurée » :
        // même ressource que dans l'app (info_req_proxy_note), donc le texte ne peut pas
        // diverger d'une surface à l'autre. Imprimée sur la page de titre ET en pied de
        // chaque page, elle voyage avec le rapport là où il est envoyé.
        val proxyNote = context.getString(R.string.info_req_proxy_note)
        pdfDoc.addEventHandler(
            PdfDocumentEvent.END_PAGE,
            ReqProxyNoteHandler(document, proxyNote, MARGIN)
        )

        // ── Page 1 : Titre ──────────────────────────────────────────────
        document.add(
            Paragraph("EUC State of Health Report")
                .setFontSize(28f)
                .simulateBold()
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
            Paragraph(
                "Generated: ${
                    SimpleDateFormat(
                        "yyyy-MM-dd HH:mm",
                        Locale.US
                    ).format(Date())
                }"
            )
                .setFontSize(12f)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER)
        )
        if (result.alarms.isNotEmpty()) {
            document.add(
                Paragraph("⚠ ${result.alarms.size} alarm(s) detected")
                    .setFontSize(14f)
                    .simulateBold()
                    .setFontColor(DeviceRgb(0xE5, 0x39, 0x35))  // Material red
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20f)
            )
        }

        // Rappel de la note sur la page de titre, en clair cette fois
        document.add(
            Paragraph(proxyNote)
                .setFontSize(9f)
                .setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.JUSTIFIED)
                .setMarginTop(28f)
        )

        // ── Page 2 : Tableau des stats ───────────────────────────────────
        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        document.add(
            Paragraph("Analysis Statistics")
                .setFontSize(16f)
                .simulateBold()
                .setMarginBottom(12f)
        )
        addStatsTable(document, result, wheelName)

        // Page alarmes (toujours présente, même si vide — ça rassure l'utilisateur)
        addAlarmsPage(document, result)

        // ── Pages charts ─────────────────────────────────────────────────
        addChartSection(document, gaussCharts, "Gaussian Bands (±1σ / ±2σ)", pageSize)

        if (!trendCharts.isNullOrEmpty()) {
            addChartSection(document,  trendCharts, "Trend (linear regression)", pageSize)
        }
        if (!cusumCharts.isNullOrEmpty()) {
            addChartSection(document, cusumCharts, "CUSUM (change detection)", pageSize)
        }
        if (!inflexionCharts.isNullOrEmpty()) {
            addChartSection(
                document,
                inflexionCharts,
                "Inflexion (regime change)",
                pageSize
            )
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

        // On force "file" en première colonne de CHAQUE sous-table si elle existe
        val baseHeaders = if ("file" in allHeaders) {
            listOf("file") + allHeaders.filterNot { it == "file" }
        } else {
            allHeaders
        }

        // Taille max d'un bloc
        val chunkSize = 10

        // Découpe en tranches
        val chunks: List<List<String>> = baseHeaders
            .chunked(chunkSize)
            .mapIndexed { index, chunk ->
                // Sauf pour le premier chunk, garantis que "file" est présent et en tête
                if (index == 0) chunk
                else {
                    val withoutFile = chunk.filterNot { it == "file" }
                    if ("file" in baseHeaders) listOf("file") + withoutFile
                    else chunk
                }
            }

        chunks.forEachIndexed { idx, headers ->
            if (idx == 0) {
                // Première page : titre stats
                document.add(
                    Paragraph("Analysis Statistics (${idx + 1}/${chunks.size})")
                        .setFontSize(14f).simulateBold().setMarginBottom(8f)
                )
            } else {
                // Nouvelle page pour chaque sous-table suivante
                document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
                document.add(
                    Paragraph("Analysis Statistics (${idx + 1}/${chunks.size})")
                        .setFontSize(14f).simulateBold().setMarginBottom(8f)
                )
            }

            val table = buildTable(logs, headers)
            document.add(table)
        }

        // Ligne Ea en bas de la dernière page
        document.add(
            Paragraph(
                "Arrhenius activation energy: " +
                        "${"%.2f".format(summary.arrhenius.eaKjPerMol)} kJ/mol"
            )
                .setFontSize(9f)
                .simulateItalic()
                .setMarginTop(6f)
        )
    }


    private fun addAlarmsPage(
        document: Document,
        result: SohAnalyzer.AnalysisResult
    ) {
        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))
        document.add(
            Paragraph("Alarms (${result.alarms.size})")
                .setFontSize(16f).simulateBold().setMarginBottom(16f)
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
                .setFontSize(9f).simulateItalic()
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(12f)
        )

        result.alarms.forEachIndexed { idx, alarm ->
            // Ligne de titre : index + fichier + km
            val kmStr = alarm.wheelKm?.let { " — ${"%.1f".format(it)} km" } ?: ""
            val dateStr = alarm.datetimeFirst?.let { " ($it)" } ?: ""

            val wrapper =
                Table(floatArrayOf(510f))  // largeur fixe, pleine page A4 landscape - marges
                    .setMarginBottom(8f)

            val cell = Cell()
                .setBackgroundColor(DeviceRgb(0xFF, 0xF3, 0xE0))  // orange très pâle
                .setPadding(8f)

            cell.add(
                Paragraph("#${idx + 1}  ${alarm.file}$kmStr$dateStr")
                    .setFontSize(10f).simulateBold()
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
        headers: List<String>
    ): Table {
        val colCount = headers.size

        // Largeur fixe par colonne, ajustée : plus de colonnes → colonnes plus étroites
        val baseWidth = when {
            colCount <= 6 -> 90f
            colCount <= 10 -> 70f
            else -> 55f
        }
        val widths = FloatArray(colCount) { baseWidth }

        val table = Table(widths).setFontSize(7f)

        // En-têtes
        headers.forEach { header ->
            table.addHeaderCell(
                Cell().add(
                    Paragraph(header)
                        .simulateBold()
                        .setFontSize(7f)
                        .setTextAlignment(TextAlignment.CENTER)
                )
                    .setBackgroundColor(DeviceRgb(0x42, 0x42, 0x42))
                    .setFontColor(ColorConstants.WHITE)
                    .setPadding(3f)
            )
        }

        // Lignes
        logs.forEachIndexed { rowIdx, row ->
            val bg = if (rowIdx % 2 == 0)
                DeviceRgb(0xFF, 0xFF, 0xFF)
            else
                DeviceRgb(0xF5, 0xF5, 0xF5)

            headers.forEach { col ->
                val raw = row[col]
                val text = formatValue(raw)
                val align = if (raw is Number)
                    TextAlignment.RIGHT
                else
                    TextAlignment.LEFT

                table.addCell(
                    Cell().add(
                        Paragraph(text)
                            .setFontSize(7f)
                            .setTextAlignment(align)
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
        charts: List<Pair<String, Bitmap>>,
        sectionTitle: String,
        pageSize: PageSize
    ) {
        // Page de titre de section
        document.add(AreaBreak(AreaBreakType.NEXT_PAGE))

        document.add(
            Paragraph(sectionTitle)
                .setFontSize(22f)
                .simulateBold()
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
                    .simulateBold()
                    .setMarginBottom(6f)
            )

            // Bitmap → PNG bytes → iText Image
            val pngBytes = bitmapToPng(bitmap)
            val imgData = ImageDataFactory.create(pngBytes)
            val img = Image(imgData)

            // Calcule la taille max disponible sur la page
            // (page - marges - bande de la note en pied de page - titre)
            val availW = pageSize.width - 2 * MARGIN
            val availH = pageSize.height - 2 * MARGIN - FOOTER_BAND - 30f  // 30f : titre
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
        is Float -> if (value.isNaN() || value.isInfinite()) "N/A" else "%.2f".format(value)
        is Number -> value.toString()
        is Boolean -> if (value) "OK" else "KO"
        else -> value.toString()
    }

    private fun buildSummary(
        result: SohAnalyzer.AnalysisResult,
        wheelName: String
    ): SohAnalyzer.SummaryData {
        val analyzer = SohAnalyzer(
            csvSource = null, mosfetParams = null,
            logger = object : io.github.eucsoh.Logger {
                override fun d(tag: String, message: String) {}
                override fun e(tag: String, message: String, throwable: Throwable?) {}
            }
        )
        return analyzer.buildSummary(result, wheelName)
    }

    /**
     * Imprime la note « R_eq = proxy de charge, pas une résistance mesurée » en pied de
     * CHAQUE page du rapport.
     *
     * Le PDF est la surface où la confusion coûte le plus cher : imprimé, le rapport est lu
     * comme une mesure d'atelier. La note est donc gravée dans le fichier lui-même — et pas
     * ajoutée par l'app au moment de l'affichage — elle voyage avec le PDF, même quand il
     * est envoyé à quelqu'un qui n'a jamais ouvert l'application.
     *
     * Le texte est dessiné directement sur le canvas à l'événement END_PAGE, dans la bande
     * de marge basse réservée par [Document.setMargins] : quelle que soit la longueur de la
     * note (les traductions sont plus ou moins longues), elle ne chevauche jamais le
     * contenu, et elle est coupée en lignes à la largeur réelle des glyphes.
     */
    private class ReqProxyNoteHandler(
        private val document: Document,
        private val note: String,
        private val bottomBase: Float
    ) : AbstractPdfDocumentEventHandler() {

        init {
            addType(PdfDocumentEvent.END_PAGE)
        }

        override fun onAcceptedEvent(event: AbstractPdfDocumentEvent) {
            val documentEvent = event as? PdfDocumentEvent ?: return
            val page = documentEvent.page ?: return
            // La page de titre porte déjà la note en clair et en plus gros : la répéter en
            // pied de cette page-là ne ferait que doubler le texte au même endroit.
            if (documentEvent.document.getPageNumber(page) == 1) return
            try {
                val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
                val maxWidth = page.pageSizeWithRotation.width -
                        document.leftMargin - document.rightMargin
                val canvas = PdfCanvas(
                    page.newContentStreamAfter(),
                    page.resources,
                    documentEvent.document
                )
                canvas.saveState()
                val lines = lines(note, font, maxWidth)
                // Dessin du bas vers le haut : la dernière ligne repose sur la marge
                // extérieure, les précédentes remontent dans la bande réservée.
                var y = bottomBase + (lines.size - 1) * FOOTER_LEADING
                for (line in lines) {
                    canvas.beginText()
                        .setFontAndSize(font, FOOTER_FONT_SIZE)
                        .setFillColorGray(0.42f)
                        .moveText(document.leftMargin.toDouble(), y.toDouble())
                        .showText(line)
                        .endText()
                    y -= FOOTER_LEADING
                }
                canvas.restoreState()
            } catch (e: IOException) {
                // Une note qui ne s'imprime pas ne doit pas faire échouer tout le rapport
                Log.w(TAG, "Proxy note footer skipped", e)
            }
        }

        /** Coupe [note] en lignes tenant dans [maxWidth], à la largeur réelle des glyphes. */
        private fun lines(text: String, font: PdfFont, maxWidth: Float): List<String> {
            val result = mutableListOf<String>()
            var current = ""
            for (word in text.split(" ")) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (current.isNotEmpty() && font.getWidth(candidate, FOOTER_FONT_SIZE) > maxWidth) {
                    result += current
                    current = word
                } else {
                    current = candidate
                }
            }
            if (current.isNotEmpty()) result += current
            return result
        }
    }
}
