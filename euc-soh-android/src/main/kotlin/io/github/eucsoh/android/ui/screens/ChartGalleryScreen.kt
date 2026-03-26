package io.github.eucsoh.android.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.eucsoh.Constants.Metrics
import io.github.eucsoh.SohAnalyzer
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

/** Onglets disponibles dans la galerie. */
private enum class ChartTab(val label: String) {
    GAUSSIAN("Gaussian"),
    TREND("Trend"),
    CUSUM("CUSUM"),
    INFLEXION("Inflexion")
}

/**
 * Chart gallery screen showing all SoH metrics visualizations.
 *
 * 4 tabs :
 * - Gaussian  : bandes gaussiennes ±1σ / ±2σ (SohChartGeneratorFixed)
 * - Trend     : régression linéaire + droite (SohTrendCusumChartGenerator)
 * - CUSUM     : Normal / Change detected + µ_ref
 * - Inflexion : Slow regime / Sustained inflexion + danger threshold
 *
 * Chaque tab génère ses charts au premier affichage (lazy).
 *
 * Les labels affichés proviennent uniquement de [Metrics] (core).
 * Fallback sur le csv_code brut si la métrique n'est pas dans l'enum.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartGalleryScreen(
    wheelName: String,
    macAddress: String,
    fileReports: List<SohAnalyzer.FileReport>,
    plotData: io.github.eucsoh.model.PlotData,   // ← remplace stats: List<ReqStatsResult>
    result: SohAnalyzer.AnalysisResult,
    alarms: Int = 0,
    onBack: () -> Unit
){
    val context = LocalContext.current
    val gaussGenerator = remember { SohChartGeneratorFixed(context) }
    val trendGenerator = remember { SohTrendCusumChartGenerator(context) }
    val pdfExporter = remember { PdfExportService(context) }
    val csvExporter = remember { CsvExportService(context) }
    val scope = rememberCoroutineScope()

    var gaussCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }
    var trendCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }
    var cusumCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }
    var inflexCharts by remember { mutableStateOf<List<Pair<String, Bitmap>>?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var selectedChart by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(ChartTab.GAUSSIAN) }

    var pdfFile by remember { mutableStateOf<File?>(null) }
    var csvFile by remember { mutableStateOf<File?>(null) }
    var zipFile by remember { mutableStateOf<File?>(null) }


    /**
     * Résout le label d'une métrique à partir de son csv_code.
     * Source unique : [Metrics] (core). Fallback : csv_code brut.
     */
    fun resolveLabel(csvCode: String): String =
        Metrics.entries.find { it.csv_code == csvCode }?.label ?: csvCode

    LaunchedEffect(selectedTab, plotData) {
        isLoading = true
        when (selectedTab) {
            ChartTab.GAUSSIAN  -> if (gaussCharts == null)
                gaussCharts = gaussGenerator.generateOverviewCharts(plotData)

            ChartTab.TREND     -> if (trendCharts == null)
                trendCharts = trendGenerator.generateAllTrendCharts(plotData, wheelName)

            ChartTab.CUSUM     -> if (cusumCharts == null)
                cusumCharts = trendGenerator.generateAllCusumCharts(plotData, wheelName)

            ChartTab.INFLEXION -> if (inflexCharts == null)
                inflexCharts = trendGenerator.generateAllInflexionCharts(plotData, wheelName)
        }
        isLoading = false
    }


    val currentCharts: List<Pair<String, Bitmap>>? = when (selectedTab) {
        ChartTab.GAUSSIAN -> gaussCharts
        ChartTab.TREND -> if(alarms > 0) trendCharts  else null
        ChartTab.CUSUM ->if(alarms > 0)  cusumCharts else null
        ChartTab.INFLEXION -> if(alarms > 0) inflexCharts else null
    }

    val timestamp = remember {
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        // destUri = l'URI choisi par l'user dans le picker
        // null si l'user a annulé
        if (destUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    pdfFile!!.inputStream().copyTo(out)
                }
                exportMessage = "PDF saved"
            } catch (e: Exception) {
                exportMessage = "PDF copy failed: ${e.message}"
            }
        }
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { destUri ->
        if (destUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    zipFile!!.inputStream().copyTo(out)
                }
                exportMessage = "Archive saved"
            } catch (e: Exception) {
                exportMessage = "Archive copy failed: ${e.message}"
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charts — $wheelName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (!currentCharts.isNullOrEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExportingPdf = true
                                    try {
                                        // Génère les charts manquants
                                        val gauss = gaussCharts ?: gaussGenerator.generateOverviewCharts(plotData)
                                            .also { gaussCharts = it }
                                        val inflex = if (alarms > 0) inflexCharts
                                            ?: trendGenerator.generateAllInflexionCharts(plotData, wheelName)
                                                .also { inflexCharts = it }
                                        else null
                                        val cusum = if (alarms > 0) cusumCharts
                                            ?: trendGenerator.generateAllCusumCharts(plotData, wheelName)
                                                .also { cusumCharts = it }
                                        else null
                                        val trend = if (alarms > 0) trendCharts
                                            ?: trendGenerator.generateAllTrendCharts(plotData, wheelName)
                                                .also { trendCharts = it }
                                        else null

                                        // Étape 1 : génère dans le dossier privé (toujours permis)
                                        pdfFile = pdfExporter.exportToPdf(gauss, inflex, cusum, trend, result,wheelName, macAddress)

                                        // Étape 2 : ouvre le picker — le lambda createPdfLauncher s'occupe de la copie
                                        createPdfLauncher.launch("${wheelName}_SoH_${timestamp}.pdf")

                                    } catch (e: Exception) {
                                        exportMessage = "Export failed: ${e.message}"
                                    } finally {
                                        isExportingPdf = false
                                    }
                                }
                            }
                        ) {
                            if (isExportingPdf)
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            else
                                Icon(Icons.Default.PictureAsPdf, "Export PDF")
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExportingPdf = true
                                    try {
                                        // Génère le PDF temp si pas déjà fait
                                        if (pdfFile == null) {
                                            val gauss = gaussCharts ?: gaussGenerator.generateOverviewCharts(plotData)
                                                .also { gaussCharts = it }
                                            pdfFile = pdfExporter.exportToPdf(
                                                gauss, inflexCharts, cusumCharts, trendCharts, result,wheelName, macAddress
                                            )
                                        }
                                        if (csvFile == null){
                                            csvFile = csvExporter.exportToCsv(result,wheelName,macAddress)
                                        }
                                        // Génère le ZIP dans le dossier privé
                                        val archiveService = SohArchiveExportService(context)
                                        zipFile = archiveService.exportArchive(
                                            wheelName = wheelName,
                                            macAddress = macAddress,
                                            fileReports = fileReports,
                                            pdfFile = pdfFile!!,
                                            csvFile = csvFile!!

                                        )

                                        // Ouvre le picker
                                        createZipLauncher.launch("${wheelName}_SoH_${timestamp}.zip")

                                    } catch (e: Exception) {
                                        exportMessage = "Archive failed: ${e.message}"
                                    } finally {
                                        isExportingPdf = false
                                    }
                                }
                            }

                        ) {
                            Icon(Icons.Default.FolderZip, "Export Archive")
                        }
                    }
                }
            )
        },
        snackbarHost = {
            exportMessage?.let { msg ->
                Snackbar(
                    action = { TextButton(onClick = { exportMessage = null }) { Text("Dismiss") } }
                ) { Text(msg) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                ChartTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    currentCharts.isNullOrEmpty() -> Text(
                        "No charts available (insufficient data)",
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(currentCharts) { (key, bitmap) ->
                            ChartCard(
                                metricName = key,
                                metricLabel = resolveLabel(key),
                                bitmap = bitmap,
                                onClick = { selectedChart = key to bitmap }
                            )
                        }
                    }
                }
            }
        }

        selectedChart?.let { (key, bitmap) ->
            Dialog(
                onDismissRequest = { selectedChart = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopAppBar(
                            title = { Text(resolveLabel(key)) },
                            navigationIcon = {
                                IconButton(onClick = { selectedChart = null }) {
                                    Icon(Icons.Default.Close, "Close")
                                }
                            }
                        )
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = key,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChartCard(
    metricName: String,
    metricLabel: String,
    bitmap: Bitmap,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = metricLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = metricName,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Tap to enlarge",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
