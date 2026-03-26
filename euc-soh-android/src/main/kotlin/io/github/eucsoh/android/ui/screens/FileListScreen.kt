package io.github.eucsoh.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.android.data.FileManager
import io.github.eucsoh.android.data.model.CsvFileInfo
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import io.github.eucsoh.SohAnalyzer

@Composable
fun FileReportItem(
    report: SohAnalyzer.FileReport,
    onClickPath: () -> Unit,
    onPreview: (String) -> Unit
) {
    val containerColor = if (report.accepted)
        MaterialTheme.colorScheme.surface
    else
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickPath),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (report.accepted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (report.accepted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    report.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Badge source
                report.source?.let { src ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(src, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (report.accepted) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    report.wheelKm?.let {
                        Text("${it.toInt()} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    report.nPoints?.let {
                        Text("$it pts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    report.reqMedian?.let {
                        Text("Req = ${"%.4f".format(it)} Ω",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text(
                    "❌ ${report.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // "Tap to see full path" hint
            Text(
                "Tap to see full path${if (report.accepted) " · Preview" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}


/**
 * File list screen for a wheel.
 * Features:
 * - Display all CSV files
 * - Checkbox to exclude files from analysis
 * - Preview file (show first lines)
 * - Open with external app
 * - Delete file
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    wheelName: String,
    fileReports: List<SohAnalyzer.FileReport>,   // ← direct depuis AnalysisResult
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fileManager = remember { FileManager(context) }

    var showRejected by remember { mutableStateOf(true) }
    var selectedReport by remember { mutableStateOf<SohAnalyzer.FileReport?>(null) }
    var previewLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingPreview by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val accepted = remember(fileReports) { fileReports.filter { it.accepted } }
    val rejected = remember(fileReports) { fileReports.filter { !it.accepted } }
    val displayed = if (showRejected) fileReports else accepted

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files — $wheelName") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Toggle rejetés
                    FilterChip(
                        selected = showRejected,
                        onClick = { showRejected = !showRejected },
                        label = { Text("Show rejected (${rejected.size})") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Résumé header
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "✅ ${accepted.size} accepted",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "❌ ${rejected.size} rejected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text("No files", modifier = Modifier.align(Alignment.Center))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayed) { report ->
                        FileReportItem(
                            report = report,
                            onClickPath = { selectedReport = report },
                            onPreview = { path ->
                                scope.launch {
                                    isLoadingPreview = true
                                    previewLines = fileManager.previewCsv(Uri.parse(path))
                                    selectedReport = report
                                    isLoadingPreview = false
                                }
                            }
                        )
                    }
                }
            }
        }

        // Dialog : chemin complet + preview
        selectedReport?.let { report ->
            AlertDialog(
                onDismissRequest = { selectedReport = null; previewLines = emptyList() },
                title = { Text(report.fileName) },
                text = {
                    Column {
                        // Chemin complet
                        SelectionContainer {
                            Text(
                                report.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (report.accepted && previewLines.isEmpty() && !isLoadingPreview) {
                            LaunchedEffect(report) {
                                isLoadingPreview = true
                                previewLines = fileManager.previewCsv(Uri.parse(report.path))
                                isLoadingPreview = false
                            }
                        }
                        if (isLoadingPreview) {
                            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                        } else if (previewLines.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(4.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(previewLines) { line ->
                                    Text(line, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedReport = null; previewLines = emptyList() }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}


@Composable
fun FileListItem(
    fileInfo: CsvFileInfo,
    onPreview: (CsvFileInfo) -> Unit,
    onOpenWith: (String) -> Unit
) {
    val sizeFormatter = DecimalFormat("#,##0.0")
    val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPreview(fileInfo) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // File name
            Text(
                text = fileInfo.fileName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${sizeFormatter.format(fileInfo.sizeBytes / 1024.0)} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = dateFormatter.format((fileInfo.isValid)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onPreview(fileInfo) }) {
                    Icon(Icons.Default.Visibility, "Preview", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Preview")
                }

                TextButton(onClick = { onOpenWith(fileInfo.uri.toString()) }) {
                    Icon(Icons.Default.OpenInNew, "Open", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open")
                }
            }
        }
    }
}
