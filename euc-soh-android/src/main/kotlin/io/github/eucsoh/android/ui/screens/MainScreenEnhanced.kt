package io.github.eucsoh.android.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.util.ShareUtils
import io.github.eucsoh.android.visualization.CsvExportService
import io.github.eucsoh.android.visualization.PdfExportService
import io.github.eucsoh.android.visualization.SohArchiveExportService
import io.github.eucsoh.android.visualization.SohChartGeneratorFixed
import io.github.eucsoh.android.visualization.SohTrendCusumChartGenerator
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.BackHandler

/**
 * Enhanced ResultsScreen avec accès aux fichiers et graphiques.
 */
@Composable
fun ResultsScreenEnhanced(
    result: SohAnalyzer.AnalysisResult,
    selectedWheel: WheelIdentity?,
    onBack: () -> Unit
) {
    val summary = result.buildSummary(selectedWheel?.displayName ?: "Wheel")
    val columnNames = summary.logs.firstOrNull()?.keys?.toList() ?: emptyList()
    val rows = summary.logs

    var showFiles by remember { mutableStateOf(false) }
    var showCharts by remember { mutableStateOf(false) }

    // En haut du composable, avec les autres remember :
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var csvFile by remember { mutableStateOf<File?>(null) }
    var zipFile by remember { mutableStateOf<File?>(null) }
    var lastSavedType by remember { mutableStateOf<String?>(null) } // "pdf" | "csv" | "zip"

    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pdfExporter = remember { PdfExportService(context) }
    val csvExporter = remember { CsvExportService(context) }
    val archiveService = remember { SohArchiveExportService(context) }
    val gaussGenerator = remember { SohChartGeneratorFixed(context) }
    val trendGenerator = remember { SohTrendCusumChartGenerator(context) }

    val timestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
    val wheelDisplayName = selectedWheel?.displayName ?: "Wheel"
    val mac = selectedWheel?.macAddress ?: "unknown"

    val createPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)
                ?.use { pdfFile!!.inputStream().copyTo(it) }
            lastSavedType = "pdf"
            exportMessage = "PDF saved — tap SHARE to send it"
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)
                ?.use { csvFile!!.inputStream().copyTo(it) }
            lastSavedType = "csv"
            exportMessage = "CSV saved — tap SHARE to send it"
        }
    }
    val createZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)
                ?.use { zipFile!!.inputStream().copyTo(it) }
            lastSavedType = "zip"
            exportMessage = "Archive saved — tap SHARE to send it"
        }
    }

    BackHandler(enabled = !showFiles && !showCharts) {
        onBack()
    }

    when {
        showFiles && selectedWheel != null -> {
            FileListScreen(
                wheelName = selectedWheel.displayName,
                fileReports = result.fileReports,
                onBack = { showFiles = false }
            )
        }

        showCharts -> {
            ChartGalleryScreen(
                wheelName = selectedWheel?.displayName ?: "Wheel",
                macAddress = selectedWheel?.macAddress!!,
                result.fileReports,
                plotData = result.plotData,
                result = result,
                alarms = result.alarms.size,
                onBack = { showCharts = false }
            )
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with summary
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Analysis results",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${rows.size} Analyzed files, ${columnNames.size} metrics",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Ea = ${String.format("%.1f", summary.arrhenius.eaKjPerMol)} kJ/mol",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (result.alarms.isNotEmpty()) {
                            Text(
                                "⚠️ ${result.alarms.size} alarm(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // ACTION BUTTONS (NEW)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Button: Manage Files
                        if (selectedWheel != null) {
                            OutlinedButton(
                                onClick = { showFiles = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "Files",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                // Text("Files")
                            }
                        }

                        // Button: View Charts
                        Button(
                            onClick = { showCharts = true },
                            modifier = Modifier.weight(1f),
                            enabled = result.plotData.gaussianResults.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = "Charts",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            // Text("Charts")
                        }
                        // Bouton PDF
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        val gauss =
                                            gaussGenerator.generateOverviewCharts(result.plotData)
                                        val trend = if (result.alarms.isNotEmpty())
                                            trendGenerator.generateAllTrendCharts(
                                                result.plotData,
                                                wheelDisplayName
                                            ) else null
                                        val cusum = if (result.alarms.isNotEmpty())
                                            trendGenerator.generateAllCusumCharts(
                                                result.plotData,
                                                wheelDisplayName
                                            ) else null
                                        val inflex = if (result.alarms.isNotEmpty())
                                            trendGenerator.generateAllInflexionCharts(
                                                result.plotData,
                                                wheelDisplayName
                                            ) else null
                                        pdfFile = pdfExporter.exportToPdf(
                                            gauss, inflex, cusum, trend,
                                            result, wheelDisplayName, mac
                                        )
                                        createPdfLauncher.launch("${wheelDisplayName}_SoH_${timestamp}.pdf")
                                    } catch (e: Exception) {
                                        exportMessage = "PDF failed: ${e.message}"
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            enabled = !isExporting && result.plotData.gaussianResults.isNotEmpty()
                        ) {
                            if (isExporting) CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            else Icon(Icons.Default.PictureAsPdf, "Export PDF")
                        }

// Bouton CSV
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        csvFile =
                                            csvExporter.exportToCsv(result, wheelDisplayName, mac)
                                        createCsvLauncher.launch("${wheelDisplayName}_SoH_${timestamp}.csv")
                                    } catch (e: Exception) {
                                        exportMessage = "CSV failed: ${e.message}"
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            enabled = !isExporting
                        ) {
                            Icon(Icons.Default.TableChart, "Export CSV")
                        }

// Bouton Archive
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        if (pdfFile == null) {
                                            val gauss =
                                                gaussGenerator.generateOverviewCharts(result.plotData)
                                            pdfFile = pdfExporter.exportToPdf(
                                                gauss, null, null, null,
                                                result, wheelDisplayName, mac
                                            )
                                        }
                                        zipFile = archiveService.exportArchive(
                                            wheelName = wheelDisplayName,
                                            macAddress = mac,
                                            fileReports = result.fileReports,
                                            pdfFile = pdfFile!!,
                                            csvFile = csvFile  // null si pas encore exporté = pas inclus
                                        )
                                        createZipLauncher.launch("${wheelDisplayName}_SoH_${timestamp}.zip")
                                    } catch (e: Exception) {
                                        exportMessage = "Archive failed: ${e.message}"
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            enabled = !isExporting
                        ) {
                            Icon(Icons.Default.FolderZip, "Export Archive")
                        }

                    }
                }

                // Data table
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .verticalScroll(rememberScrollState())
                ) {
                    Column {
                        // Header row
                        Row(modifier = Modifier.padding(8.dp)) {
                            columnNames.forEach { colName ->
                                Text(
                                    colName,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider()

                        // Data rows
                        rows.forEachIndexed { rowIdx, row ->
                            Surface(
                                color = if (rowIdx % 2 == 0)
                                    MaterialTheme.colorScheme.surface
                                else
                                    MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(8.dp)) {
                                    columnNames.forEach { colName ->
                                        val value = row[colName]
                                        Text(
                                            formatValue(value),
                                            modifier = Modifier
                                                .width(150.dp)
                                                .padding(horizontal = 4.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                exportMessage?.let { msg ->
                    Snackbar(
                        action = {
                            if (lastSavedType != null) {
                                TextButton(onClick = {
                                    val fileToShare = when (lastSavedType) {
                                        "pdf" -> pdfFile
                                        "csv" -> csvFile
                                        "zip" -> zipFile
                                        else -> null
                                    }
                                    if (fileToShare != null) {
                                        val mime = when (lastSavedType) {
                                            "pdf" -> "application/pdf"
                                            "csv" -> "text/csv"
                                            "zip" -> "application/zip"
                                            else -> "*/*"
                                        }
                                        ShareUtils.shareFile(
                                            context = context,
                                            file = fileToShare,
                                            mimeType = mime,
                                            chooserTitle = "Share SoH export"
                                        )
                                    }
                                    exportMessage = null
                                }) { Text("SHARE") }
                            } else {
                                TextButton(onClick = { exportMessage = null }) { Text("Dismiss") }
                            }
                        }
                    ) { Text(msg) }
                }


                // Back button
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Back")
                }
            }
        }
    }
}

private fun formatValue(value: Any?): String {
    return when (value) {
        null -> ""
        is Double -> if (value.isNaN() || value.isInfinite()) "N/A" else String.format(
            "%.2f",
            value
        )

        is Float -> if (value.isNaN() || value.isInfinite()) "N/A" else String.format("%.2f", value)
        is Number -> value.toString()
        is Boolean -> if (value) "✓" else "✗"
        else -> value.toString()
    }
}

private fun SohAnalyzer.AnalysisResult.buildSummary(wheelName: String): SohAnalyzer.SummaryData {
    val analyzer = io.github.eucsoh.SohAnalyzer(
        csvSource = null,
        mosfetParams = null,
        logger = object : io.github.eucsoh.Logger {
            override fun d(tag: String, message: String) {}
            override fun e(tag: String, message: String, throwable: Throwable?) {}
        }
    )
    return analyzer.buildSummary(this, wheelName)
}
