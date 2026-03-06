package io.github.eucsoh.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.ui.SohViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SohViewModel,
    onRequestPermissions: () -> Unit,
    onRequestFolderPicker: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EUC SoH Analyzer") },
                actions = {
                    IconButton(onClick = onRequestFolderPicker) {
                        Icon(Icons.Default.Folder, "Choisir un dossier")
                    }
                    IconButton(onClick = { viewModel.scanWheels(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, "Rafraîchir")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Show current scan path
            if (state.scanRootPath.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Dossier de scan:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            state.scanRootPath,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            when {
                state.isScanning -> {
                    LoadingScreen("Recherche des roues...")
                }
                state.isAnalyzing -> {
                    AnalysisProgressScreen(
                        currentFile = state.currentFile,
                        totalFiles = state.totalFiles,
                        fileName = state.currentFileName,
                        isParallel = state.useParallelProcessing
                    )
                }
                state.analysisResult != null -> {
                    ResultsScreen(
                        result = state.analysisResult!!,
                        onBack = viewModel::clearResults
                    )
                }
                state.detectedWheels.isEmpty() -> {
                    EmptyStateScreen(
                        onRequestPermissions = onRequestPermissions,
                        onRequestFolderPicker = onRequestFolderPicker,
                        onRetry = { viewModel.scanWheels(forceRefresh = true) },
                        scanPath = state.scanRootPath
                    )
                }
                else -> {
                    WheelListContent(
                        wheels = state.detectedWheels.values.toList(),
                        selectedWheel = state.selectedWheel,
                        onSelectWheel = viewModel::selectWheel,
                        onAnalyze = viewModel::startAnalysis,
                        error = state.error,
                        onDismissError = viewModel::clearError,
                        useParallelProcessing = state.useParallelProcessing,
                        onToggleParallel = viewModel::toggleParallelProcessing
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message)
        }
    }
}

@Composable
fun AnalysisProgressScreen(
    currentFile: Int,
    totalFiles: Int,
    fileName: String,
    isParallel: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                "Analyse en cours...",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(Modifier.height(16.dp))
            
            if (isParallel) {
                // Parallel mode: no detailed progress, just file count
                Text(
                    "Traitement parallèle de $totalFiles fichiers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Sequential mode: show detailed progress
                if (totalFiles > 0) {
                    Text(
                        "Fichier $currentFile / $totalFiles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    LinearProgressIndicator(
                        progress = { currentFile.toFloat() / totalFiles.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(8.dp),
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    if (fileName.isNotEmpty()) {
                        Text(
                            fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateScreen(
    onRequestPermissions: () -> Unit,
    onRequestFolderPicker: () -> Unit,
    onRetry: () -> Unit,
    scanPath: String
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Aucune roue détectée",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Le scanner cherche récursivement les dossiers:\n" +
                        "- WheelLog/\n" +
                        "- EUC World/\n\n" +
                        "dans: $scanPath\n\n" +
                        "Assurez-vous que vos logs sont bien dans ce dossier.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequestFolderPicker) {
                Text("📁 Choisir un autre dossier")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text("Vérifier les permissions")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text("Réessayer")
            }
        }
    }
}

@Composable
fun WheelListContent(
    wheels: List<WheelIdentity>,
    selectedWheel: WheelIdentity?,
    onSelectWheel: (WheelIdentity) -> Unit,
    onAnalyze: () -> Unit,
    error: String?,
    onDismissError: () -> Unit,
    useParallelProcessing: Boolean,
    onToggleParallel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Roues détectées (${wheels.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(wheels) { wheel ->
                WheelCard(
                    wheel = wheel,
                    isSelected = wheel == selectedWheel,
                    onClick = { onSelectWheel(wheel) }
                )
            }
        }

        // Error snackbar
        error?.let {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) {
                        Text("OK")
                    }
                }
            }
        }
        
        // Parallel processing toggle
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Traitement parallèle",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (useParallelProcessing) "Plus rapide, pas de suivi détaillé" else "Suivi fichier par fichier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useParallelProcessing,
                    onCheckedChange = { onToggleParallel() }
                )
            }
        }

        // Analyze button
        if (selectedWheel != null) {
            Button(
                onClick = onAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Analyser ${selectedWheel.displayName}")
            }
        }
    }
}

@Composable
fun WheelCard(
    wheel: WheelIdentity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                wheel.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            wheel.manufacturer?.let { make ->
                Text(
                    "${make}${wheel.model?.let { " $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                "${wheel.csvFiles.size} fichiers CSV",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "MAC: ${wheel.macAddress}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ResultsScreen(
    result: SohAnalyzer.AnalysisResult,
    onBack: () -> Unit
) {
    // Convert DataFrame to simple structure for UI
    val summary = result.buildSummary("Current Wheel")
    val columnNames = summary.logs.firstOrNull()?.keys?.toList() ?: emptyList()
    val rows = summary.logs
    
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
                    "${rows.size} fichiers analysés, ${columnNames.size} métriques calculées",
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
        
        // Data table with horizontal and vertical scroll
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
            Text("Retour")
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
    val analyzer = SohAnalyzer(
        csvSource = null,
        mosfetParams = null,
        logger = object : io.github.eucsoh.Logger {
            override fun d(tag: String, message: String) {}
            override fun e(tag: String, message: String, throwable: Throwable?) {}
        }
    )
    return analyzer.buildSummary(this, wheelName)
}
