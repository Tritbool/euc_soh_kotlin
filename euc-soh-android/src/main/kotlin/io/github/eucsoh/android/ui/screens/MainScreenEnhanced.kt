package io.github.eucsoh.android.ui.screens

import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.Constants.MetaColumns.CSV_FILE
import io.github.eucsoh.Constants.MetaColumns.WHEEL_KM
import io.github.eucsoh.Constants.Metrics.I_95P
import io.github.eucsoh.Constants.Metrics.I_MAX
import io.github.eucsoh.Constants.Metrics.I_PHASE2_INT
import io.github.eucsoh.Constants.Metrics.I_PHASE_95P
import io.github.eucsoh.Constants.Metrics.I_PHASE_MAX
import io.github.eucsoh.Constants.Metrics.PWM_95P
import io.github.eucsoh.Constants.Metrics.PWM_MAX
import io.github.eucsoh.Constants.Metrics.REQ_95P
import io.github.eucsoh.Constants.Metrics.REQ_MEDIAN
import io.github.eucsoh.Constants.Metrics.REQ_MEDIAN_25C
import io.github.eucsoh.Constants.Metrics.R_BATT_MEDIAN
import io.github.eucsoh.Constants.Metrics.R_BATT_MEDIAN_25C
import io.github.eucsoh.Constants.Metrics.R_MOSFET_HOT
import io.github.eucsoh.Constants.Metrics.SAG_95P
import io.github.eucsoh.Constants.Metrics.SAG_MAX
import io.github.eucsoh.Constants.Metrics.SAG_MEDIAN
import io.github.eucsoh.Constants.Metrics.TEMP_BOARD_MAX
import io.github.eucsoh.Constants.Metrics.TEMP_MOTOR_MAX
import io.github.eucsoh.Constants.Metrics.V_MIN_STRONG
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.data.model.ReqStatsResult
import io.github.eucsoh.android.data.model.WheelIdentity

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
                    fileName = row[CSV_FILE.csv_code] as? String ?: "",
                    wheelKm = (row[WHEEL_KM.csv_code] as? Number)?.toDouble(),
                    reqMedian = (row[REQ_MEDIAN.csv_code] as? Number)?.toDouble(),
                    reqMedian25C = (row[REQ_MEDIAN_25C.csv_code] as? Number)?.toDouble(),
                    req95p = (row[REQ_95P.csv_code] as? Number)?.toDouble(),
                    rBattMedian = (row[R_BATT_MEDIAN.csv_code] as? Number)?.toDouble(),
                    rBattMedian25C = (row[R_BATT_MEDIAN_25C.csv_code] as? Number)?.toDouble(),
                    sagMedian = (row[SAG_MEDIAN.csv_code] as? Number)?.toDouble(),
                    sag95p = (row[SAG_95P.csv_code] as? Number)?.toDouble(),
                    sagMax = (row[SAG_MAX.csv_code] as? Number)?.toDouble(),
                    vMinStrong = (row[V_MIN_STRONG.csv_code] as? Number)?.toDouble(),
                    iMax = (row[I_MAX.csv_code] as? Number)?.toDouble(),
                    iPhaseMax = (row[I_PHASE_MAX.csv_code] as? Number)?.toDouble(),
                    i95p = (row[I_95P.csv_code] as? Number)?.toDouble(),
                    iPhase95p = (row[I_PHASE_95P.csv_code] as? Number)?.toDouble(),
                    iPhase2Int = (row[I_PHASE2_INT.csv_code] as? Number)?.toDouble(),
                    pwm95p = (row[PWM_95P.csv_code] as? Number)?.toDouble(),
                    pwmMax = (row[PWM_MAX.csv_code] as? Number)?.toDouble(),
                    rMosfetHot = (row[R_MOSFET_HOT.csv_code] as? Number)?.toDouble(),
                    tempBoardMax = (row[TEMP_BOARD_MAX.csv_code] as? Number)?.toDouble(),
                    tempMotorMax = (row[TEMP_MOTOR_MAX.csv_code] as? Number)?.toDouble()
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
        is Double -> if (value.isNaN() || value.isInfinite()) "N/A" else String.format(
            "%.4f",
            value
        )

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
