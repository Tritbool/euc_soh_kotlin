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

import android.content.Intent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.R
import io.github.eucsoh.android.data.FileManager
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FileReportItem(
    report: SohAnalyzer.FileReport,
    onClickPath: () -> Unit
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
                    File(report.path).name,
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    report.wheelKm?.let {
                        Text(
                            "${it.toInt()} km",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    report.nPoints?.let {
                        Text(
                            "$it pts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    report.reqMedian?.let {
                        Text(
                            "Req = ${"%.4f".format(it)} Ω",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    "❌ ${report.rejectionReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Hint : tap pour le chemin
            Text(
                stringResource(R.string.files_tap_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    wheelName: String,
    fileReports: List<SohAnalyzer.FileReport>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fileManager = remember { FileManager(context) }
    val scope = rememberCoroutineScope()

    var showRejected by remember { mutableStateOf(true) }
    var selectedReport by remember { mutableStateOf<SohAnalyzer.FileReport?>(null) }

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
            androidx.compose.material3.Surface(
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
                    Text(
                        stringResource(R.string.files_none),
                        modifier = Modifier.align(Alignment.Center)
                    )
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
                            onClickPath = { selectedReport = report }
                        )
                    }
                }
            }
        }

        // Dialog : chemin lisible + bouton "Ouvrir avec"
        selectedReport?.let { report ->
            // Uri.decode remplace tous les %20 (et autres séquences percent-encoded) par leurs
            // caractères réels. C'est purement cosmétique : l'Uri d'origine est conservée pour
            // l'Intent, seul l'affichage est décodé.
            val decodedPath = Uri.decode(report.path)

            AlertDialog(
                onDismissRequest = { selectedReport = null },
                title = { Text(File(report.path).name) },
                text = {
                    Column {
                        SelectionContainer {
                            Text(
                                decodedPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    // Ouvrir avec une app tierce : l'OS affiche le sélecteur d'apps
                    TextButton(
                        onClick = {
                            // On copie le CSV dans le cache puis on expose via FileProvider.
                            // Sans ça, une app tierce ne peut pas lire une URI SAF directement.
                            scope.launch {
                                val shareUri = fileManager.copyToCache(
                                    sourceUri = report.path.toUri(),
                                    fileName =File(report.path).name
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(shareUri, "text/csv")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        context.getString(R.string.files_open_with)
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.files_open_with))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedReport = null }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}
