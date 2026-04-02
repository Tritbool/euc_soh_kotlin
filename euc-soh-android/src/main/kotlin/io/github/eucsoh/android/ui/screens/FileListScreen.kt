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

package io.github.eucsoh.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.data.FileManager
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import io.github.eucsoh.android.R
import androidx.core.net.toUri

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
                stringResource(if (report.accepted) R.string.files_tap_hint_preview else R.string.files_tap_hint),
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
                title = { Text(stringResource(R.string.files_title, wheelName)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Toggle rejetés
                    FilterChip(
                        selected = showRejected,
                        onClick = { showRejected = !showRejected },
                        label = { Text(stringResource(R.string.files_show_rejected, rejected.size)) },
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
                        stringResource(R.string.files_accepted, accepted.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stringResource(R.string.files_rejected, rejected.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (displayed.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text(stringResource(R.string.files_none), modifier = Modifier.align(Alignment.Center))
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
                                    // TODO: Needs rework
                                    previewLines = fileManager.previewCsv(path.toUri())
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
                                previewLines = fileManager.previewCsv(report.path.toUri())
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
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}


