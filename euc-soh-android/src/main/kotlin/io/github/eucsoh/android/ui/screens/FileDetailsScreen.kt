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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eucsoh.android.data.model.CsvFileInfo
import androidx.compose.ui.res.stringResource
import io.github.eucsoh.android.R

@Suppress("unused so far")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsScreen(
    files: List<CsvFileInfo>,
    onBack: () -> Unit,
    onToggleExclusion: (CsvFileInfo) -> Unit,
    onAnalyze: () -> Unit
) {
    val validCount = files.count { it.isValid && !it.isExcluded }
    val excludedCount = files.count { it.isExcluded }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.file_details_title, files.size)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                stringResource(R.string.file_details_valid_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.file_details_valid_count, validCount, files.size),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (excludedCount > 0) {
                            Column {
                                Text(
                                    stringResource(R.string.file_details_excluded_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "$excludedCount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Button(
                        onClick = onAnalyze,
                        enabled = validCount > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.file_details_analyze_button, validCount))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(files) { file ->
                FileInfoCard(
                    file = file,
                    onToggleExclusion = { onToggleExclusion(file) }
                )
            }
        }
    }
}

@Composable
fun FileInfoCard(
    file: CsvFileInfo,
    onToggleExclusion: () -> Unit
) {
    val bgColor = when {
        file.isExcluded -> MaterialTheme.colorScheme.errorContainer
        file.isValid -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = when {
        file.isExcluded -> MaterialTheme.colorScheme.onErrorContainer
        file.isValid -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExclusion),
        colors = CardDefaults.cardColors(
            containerColor = bgColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    file.validationMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
                
                // Additional details
                if (file.nPoints != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append("${file.nPoints} points")
                            if (file.reqMedian != null) {
                                append(" | Req: ${"%.3f".format(file.reqMedian)}Ω")
                            }
                            if (file.wheelKm != null) {
                                append(" | ${"%.0f".format(file.wheelKm)} km")
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                }
                
                // File size
                if (file.sizeBytes > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatFileSize(file.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
                
                // Exclusion status
                if (file.isExcluded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.file_details_excluded_hint),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Icon(
                imageVector = if (file.isValid && !file.isExcluded) 
                    Icons.Default.CheckCircle 
                else 
                    Icons.Default.Warning,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
