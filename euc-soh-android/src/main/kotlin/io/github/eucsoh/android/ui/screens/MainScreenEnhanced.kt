package io.github.eucsoh.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.data.model.ReqStatsResult
import io.github.eucsoh.android.data.model.WheelIdentity
import kotlinx.coroutines.launch

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
    
    // Convert analysis result to ReqStatsResult list for charts
    val stats = remember(rows) {
        rows.mapNotNull { row ->
            try {
                ReqStatsResult(
                    fileName = row["csv_file"] as? String ?: "",
                    wheelKm = (row["wheel_km"] as? Number)?.toDouble(),
                    reqMedian = (row["Req_median"] as? Number)?.toDouble(),
                    req95p = (row["Req_95p"] as? Number)?.toDouble(),
                    sagMedian = (row["sag_median"] as? Number)?.toDouble(),
                    sag95p = (row["sag_95p"] as? Number)?.toDouble(),
                    sagMax = (row["sag_max"] as? Number)?.toDouble(),
                    vMinStrong = (row["v_min_strong"] as? Number)?.toDouble(),
                    iMax = (row["i_max"] as? Number)?.toDouble(),
                    i95p = (row["i_95p"] as? Number)?.toDouble(),
                    tempBoardMax = (row["temp_board_max"] as? Number)?.toDouble(),
                    tempMotorMax = (row["temp_motor_max"] as? Number)?.toDouble()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    when {
        showFiles && selectedWheel != null -> {
            FileListScreen(
                wheelName = selectedWheel.displayName,
                wheelDirUri = Uri.parse(selectedWheel.csvFiles.first().toString()).let {
                    // Extract parent directory
                    val path = it.path ?: ""
                    val parentPath = path.substringBeforeLast('/', "")
                    Uri.parse("content://com.android.externalstorage.documents/tree/$parentPath")
                },
                onBack = { showFiles = false }
            )
        }
        showCharts -> {
            ChartGalleryScreen(
                wheelName = selectedWheel?.displayName ?: "Wheel",
                stats = stats,
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
                            "Résultats d'analyse",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${rows.size} fichiers analysés, ${columnNames.size} métriques",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Ea = ${String.format("%.1f", summary.arrhenius.eaKjPerMol)} kJ/mol",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (result.alarms.isNotEmpty()) {
                            Text(
                                "⚠️ ${result.alarms.size} alarme(s)",
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
                                Text("Files")
                            }
                        }
                        
                        // Button: View Charts
                        Button(
                            onClick = { showCharts = true },
                            modifier = Modifier.weight(1f),
                            enabled = stats.size >= 3 // Need min 3 points
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = "Charts",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Charts")
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
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retour")
                }
            }
        }
    }
}

private fun formatValue(value: Any?): String {
    return when (value) {
        null -> ""
        is Double -> if (value.isNaN() || value.isInfinite()) "N/A" else String.format("%.4f", value)
        is Float -> if (value.isNaN() || value.isInfinite()) "N/A" else String.format("%.4f", value)
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
