package io.github.eucsoh.android.ui.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.R
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
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable

/**
 * Enhanced ResultsScreen avec accès aux fichiers et graphiques.
 */
@Composable
fun ResultsScreenEnhanced(
    result: SohAnalyzer.AnalysisResult,
    selectedWheel: WheelIdentity?,
    lastExportMime: String?,
    lastExportPath: String?,
    onMarkExport: (String, String?) -> Unit,
    onBack: () -> Unit
) {
    val TAG = "ResultsScreenEnhanced"
    val summary = remember(result) { result.buildSummary(selectedWheel?.displayName ?: "Wheel") }
    val columnNames = summary.logs.firstOrNull()?.keys?.toList() ?: emptyList()
    val rows = summary.logs

    var showFiles by remember { mutableStateOf(false) }
    var showCharts by remember { mutableStateOf(false) }
    var showAlarmsDialog by remember { mutableStateOf(false) }


    // Fichiers exportés
    var pdfFile by rememberSaveable { mutableStateOf<File?>(null) }
    var csvFile by rememberSaveable { mutableStateOf<File?>(null) }
    var zipFile by rememberSaveable { mutableStateOf<File?>(null) }

    var isExporting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pdfExporter = remember { PdfExportService(context) }
    val csvExporter = remember { CsvExportService(context) }
    val archiveService = remember { SohArchiveExportService(context) }
    val gaussGenerator = remember { SohChartGeneratorFixed(context) }
    val trendGenerator = remember { SohTrendCusumChartGenerator(context) }

    var gaussCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }
    var trendCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }
    var cusumCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }
    var inflexCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }

    val timestamp = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
    val wheelDisplayNameRaw = selectedWheel?.displayName ?: "Wheel"
    val mac = selectedWheel?.macAddress ?: "unknown"
    val macSafe = mac.replace(":", "_")

    val wheelDisplayName = if (mac == wheelDisplayNameRaw) {
        macSafe
    } else {
        wheelDisplayNameRaw
    }

    fun clearCharts() {
        gaussCharts?.forEach { it.second.recycle() }
        trendCharts?.forEach { it.second.recycle() }
        cusumCharts?.forEach { it.second.recycle() }
        inflexCharts?.forEach { it.second.recycle() }

        gaussCharts = null
        trendCharts = null
        cusumCharts = null
        inflexCharts = null
    }

    fun generateCharts() {
        gaussCharts =
            gaussGenerator.generateOverviewCharts(result.plotData)
        trendCharts = if (result.alarms.isNotEmpty())
            trendGenerator.generateAllTrendCharts(
                result.plotData,
                wheelDisplayName
            ) else null
        cusumCharts = if (result.alarms.isNotEmpty())
            trendGenerator.generateAllCusumCharts(
                result.plotData,
                wheelDisplayName
            ) else null
        inflexCharts = if (result.alarms.isNotEmpty())
            trendGenerator.generateAllInflexionCharts(
                result.plotData,
                wheelDisplayName
            ) else null
    }

    Log.d(TAG, "MAC : $macSafe")
    // Launchers "Enregistrer sous..." : ne font que copier + Toast
    val createPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)
                ?.use { pdfFile!!.inputStream().copyTo(it) }
            Toast.makeText(
                context,
                context.getString(R.string.pdf_saved),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)
                ?.use { csvFile!!.inputStream().copyTo(it) }
            Toast.makeText(
                context,
                context.getString(R.string.csv_saved),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)
                ?.use { zipFile!!.inputStream().copyTo(it) }
            Toast.makeText(
                context,
                context.getString(R.string.zip_saved),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    if (showAlarmsDialog) {
        AlarmsDialog(
            alarms = result.alarms,
            onDismiss = { showAlarmsDialog = false }
        )
    }

    BackHandler(enabled = !showFiles && !showCharts) {
        clearCharts()
        context.getExternalFilesDir(null)
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
                            stringResource(R.string.results_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.results_subtitle,
                                rows.size,
                                columnNames.size
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(
                                R.string.results_ea,
                                String.format(
                                    Locale.getDefault(),
                                    "%.1f",
                                    summary.arrhenius.eaKjPerMol
                                )
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (result.alarms.isNotEmpty()) {
                            Text(
                                stringResource(R.string.results_alarms, result.alarms.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { showAlarmsDialog = true }
                                    .padding(vertical = 2.dp)
                            )
                        }
                    }
                }

                // ACTION BUTTONS
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
                                    contentDescription = stringResource(R.string.files_button_cd),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
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
                                contentDescription = stringResource(R.string.charts_button_cd),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }

                        // Bouton PDF
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        generateCharts()
                                        pdfFile = pdfExporter.exportToPdf(
                                            gaussCharts!!,
                                            inflexCharts!!,
                                            cusumCharts!!,
                                            trendCharts!!,
                                            result,
                                            wheelDisplayName,
                                            macSafe
                                        )
                                        onMarkExport("application/pdf", pdfFile!!.absolutePath)
                                        createPdfLauncher.launch("${wheelDisplayName}-${macSafe}_SoH_${timestamp}.pdf")

                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.export_failed,
                                                "PDF",
                                                e.message ?: ""
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                            else Icon(
                                Icons.Default.PictureAsPdf,
                                stringResource(R.string.export_pdf_cd)
                            )
                        }

                        // Bouton CSV
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        csvFile =
                                            csvExporter.exportToCsv(
                                                result,
                                                wheelDisplayName,
                                                macSafe
                                            )

                                        onMarkExport("text/csv", csvFile!!.absolutePath)
                                        createCsvLauncher.launch("${wheelDisplayName}-${macSafe}_SoH_${timestamp}.csv")

                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.export_failed,
                                                "CSV",
                                                e.message ?: ""
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            enabled = !isExporting
                        ) {
                            Icon(
                                Icons.Default.TableChart,
                                stringResource(R.string.export_csv_cd)
                            )
                        }

                        // Bouton Archive
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExporting = true
                                    try {
                                        if (pdfFile == null) {
                                            generateCharts()
                                            pdfFile = pdfExporter.exportToPdf(
                                                gaussCharts!!,
                                                inflexCharts!!,
                                                cusumCharts!!,
                                                trendCharts!!,
                                                result,
                                                wheelDisplayName,
                                                macSafe
                                            )
                                        }
                                        if (csvFile == null) {
                                            csvFile =
                                                csvExporter.exportToCsv(
                                                    result,
                                                    wheelDisplayName,
                                                    macSafe
                                                )
                                        }
                                        zipFile = archiveService.exportArchive(
                                            wheelName = wheelDisplayName,
                                            macAddress = macSafe,
                                            fileReports = result.fileReports,
                                            pdfFile = pdfFile!!,
                                            csvFile = csvFile!!  // null si pas encore exporté = pas inclus
                                        )

                                        onMarkExport("application/zip", zipFile!!.absolutePath)
                                        createZipLauncher.launch("${wheelDisplayName}-${macSafe}_SoH_${timestamp}.zip")
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.export_failed,
                                                "Archive",
                                                e.message ?: ""
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            },
                            enabled = !isExporting
                        ) {
                            Icon(
                                Icons.Default.FolderZip,
                                stringResource(R.string.export_archive_cd)
                            )
                        }

                        IconButton(
                            onClick = {
                                try {
                                    if (lastExportPath != null && lastExportMime != null) {
                                        Log.d(
                                            TAG,
                                            "last file: $lastExportPath, last MIME: $lastExportMime"
                                        )
                                        ShareUtils.shareFile(
                                            context = context,
                                            file = File(lastExportPath),
                                            mimeType = lastExportMime,
                                            chooserTitle = context.getString(R.string.share_chooser_title)
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Aucun fichier exporté (ou recréation après clear cache)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } catch (e: Exception) {
                                    Log.d(TAG, e.message ?: "")
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.export_failed,
                                            "SHARE",
                                            e.message ?: ""
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            enabled = lastExportMime != null
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = stringResource(R.string.share)
                            )
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

                // Back button
                Button(
                    onClick = {
                        clearCharts()
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.back))
                }
            }
        }
    }
}

private fun formatValue(value: Any?): String {
    return when (value) {
        null -> ""
        is Double -> if (value.isNaN() || value.isInfinite()) "N/A" else String.format(
            Locale.getDefault(),
            "%.2f",
            value
        )

        is Float -> if (value.isNaN() || value.isInfinite()) "N/A" else String.format(
            Locale.getDefault(),
            "%.2f",
            value
        )

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