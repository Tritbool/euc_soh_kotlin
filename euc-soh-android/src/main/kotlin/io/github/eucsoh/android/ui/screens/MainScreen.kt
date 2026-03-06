package io.github.eucsoh.android.ui.screens

import androidx.compose.foundation.clickable
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
                        fileName = state.currentFileName
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
                        onDismissError = viewModel::clearError
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
    fileName: String
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
    onDismissError: () -> Unit
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            "Résultats d'analyse",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Arrhenius activation energy
        StatCard(
            title = "Énergie d'activation (Ea)",
            value = String.format("%.1f kJ/mol", result.eaJPerMol / 1000),
            description = "Paramètre d'Arrhenius pour la température"
        )
        
        Spacer(Modifier.height(12.dp))
        
        // Pack configuration
        result.nsGlobal?.let { ns ->
            StatCard(
                title = "Configuration pack",
                value = "${ns}S",
                description = result.vNominal?.let { 
                    "Tension nominale: ${String.format("%.1f V", it)}"
                }
            )
            Spacer(Modifier.height(12.dp))
        }
        
        // Pack resistance
        result.rPackNominal?.let { rPack ->
            StatCard(
                title = "Résistance pack nominale",
                value = String.format("%.1f mΩ", rPack * 1000),
                description = "Résistance interne du pack batterie"
            )
            Spacer(Modifier.height(12.dp))
        }
        
        // Alarms section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (result.alarms.isEmpty())
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Alarmes détectées",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.alarms.isEmpty())
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                
                Spacer(Modifier.height(8.dp))
                
                if (result.alarms.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "✓",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Aucune anomalie détectée",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    Text(
                        "${result.alarms.size} anomalie(s) trouvée(s)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    result.alarms.take(5).forEach { alarm ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    alarm.file,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    alarm.reasons,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    if (result.alarms.size > 5) {
                        Text(
                            "... et ${result.alarms.size - 5} autre(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retour")
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    description: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            description?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
