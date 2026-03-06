package io.github.eucsoh.android.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import io.github.eucsoh.android.data.model.ReqStatsResult
import io.github.eucsoh.android.visualization.PdfExportService
import io.github.eucsoh.android.visualization.SohChartGenerator
import kotlinx.coroutines.launch

/**
 * Chart gallery screen showing all SoH metrics visualizations.
 * 
 * Features:
 * - Display all generated charts
 * - Tap to view full-screen
 * - Export all to PDF
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartGalleryScreen(
    wheelName: String,
    stats: List<ReqStatsResult>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val chartGenerator = remember { SohChartGenerator(context) }
    val pdfExporter = remember { PdfExportService(context) }
    val scope = rememberCoroutineScope()

    var charts by remember { mutableStateOf<List<Pair<String, Bitmap>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedChart by remember { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    // Generate charts on composition
    LaunchedEffect(stats) {
        isLoading = true
        charts = chartGenerator.generateOverviewCharts(stats)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charts - $wheelName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (charts.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isExportingPdf = true
                                    try {
                                        val file = pdfExporter.exportToPdf(stats, wheelName)
                                        exportMessage = "PDF saved: ${file.absolutePath}"
                                    } catch (e: Exception) {
                                        exportMessage = "Export failed: ${e.message}"
                                    } finally {
                                        isExportingPdf = false
                                    }
                                }
                            }
                        ) {
                            if (isExportingPdf) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.PictureAsPdf, "Export PDF")
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = {
            exportMessage?.let { msg ->
                Snackbar(
                    action = {
                        TextButton(onClick = { exportMessage = null }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(msg)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                charts.isEmpty() -> {
                    Text(
                        "No charts available (insufficient data)",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(charts) { (name, bitmap) ->
                            ChartCard(
                                metricName = name,
                                bitmap = bitmap,
                                onClick = { selectedChart = name to bitmap }
                            )
                        }
                    }
                }
            }
        }

        // Full-screen chart dialog
        selectedChart?.let { (name, bitmap) ->
            Dialog(
                onDismissRequest = { selectedChart = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        TopAppBar(
                            title = { Text(SohChartGenerator.METRIC_LABELS[name] ?: name) },
                            navigationIcon = {
                                IconButton(onClick = { selectedChart = null }) {
                                    Icon(Icons.Default.Close, "Close")
                                }
                            }
                        )
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = name,
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
                text = SohChartGenerator.METRIC_LABELS[metricName] ?: metricName,
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
