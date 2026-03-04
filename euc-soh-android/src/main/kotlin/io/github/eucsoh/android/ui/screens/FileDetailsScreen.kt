package io.github.eucsoh.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.eucsoh.android.data.model.CsvFileInfo

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
                title = { Text("Détails des fichiers (${files.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Retour")
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
                                "Fichiers valides",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "$validCount/${files.size}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (excludedCount > 0) {
                            Column {
                                Text(
                                    "Exclus",
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
                        Text("Analyser $validCount fichiers")
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
                        "❌ EXCLU (cliquer pour réactiver)",
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
