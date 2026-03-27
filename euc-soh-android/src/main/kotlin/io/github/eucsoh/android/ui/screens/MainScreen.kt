package io.github.eucsoh.android.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.Constants.ANALYZING
import io.github.eucsoh.Constants.CALIBRATING
import io.github.eucsoh.Constants.DONE
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.ui.SohViewModel
import androidx.compose.ui.res.stringResource
import io.github.eucsoh.android.R

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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { viewModel.scanWheels(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.topbar_refresh))
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
                            stringResource(R.string.selected_euc_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            state.selectedWheel?.displayName ?: state.selectedWheel?.macAddress
                            ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            when {
                state.isScanning -> {
                    LoadingScreen(stringResource(R.string.scanning_eucs))
                }

                state.progressState?.phase == ANALYZING -> {
                    AnalysisProgressScreen(
                        currentFile = state.progressState?.current!!,
                        totalFiles = state.progressState?.total!!,
                        fileName = "",//state.currentFileName,
                        phase = ANALYZING
                    )
                }

                state.progressState?.phase == CALIBRATING -> {
                    AnalysisProgressScreen(
                        currentFile = state.progressState?.current!!,
                        totalFiles = state.progressState?.total!!,
                        fileName = "",//state.currentFileName,
                        phase = CALIBRATING
                    )
                }

                state.progressState?.phase == DONE && state.analysisResult != null && state.showResults -> {
                    ResultsScreenEnhanced(
                        result = state.analysisResult!!,
                        selectedWheel = state.selectedWheel,
                        onBack = viewModel::hideResults
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
                        wheelConfigs = state.wheelConfigs,
                        onSelectWheel = viewModel::selectWheel,
                        onAnalyze = viewModel::startAnalysis,
                        onConfigMosfet = viewModel::showMosfetConfig,
                        error = state.error,
                        onDismissError = viewModel::clearError,
                        hasResults = state.analysisResult != null &&
                                state.analysisResult!!.macAddress == state.selectedWheel?.macAddress,
                        onShowResults = viewModel::showResults
                    )
                }
            }

            // MOSFET config dialog
            if (state.showMosfetDialog && state.configDialogWheel != null) {
                val wheel = state.configDialogWheel!!
                val currentParams = state.wheelConfigs[wheel.macAddress]?.mosfetParams

                MosfetConfigDialog(
                    wheelName = wheel.displayName,
                    currentParams = currentParams,
                    onSave = viewModel::saveMosfetConfig,
                    onClear = viewModel::clearMosfetConfig,
                    onDismiss = viewModel::dismissMosfetDialog
                )
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
    phase: String,
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
                phase,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            if (totalFiles > 0) {
                Text(
                    "Log $currentFile / $totalFiles",
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
                stringResource(R.string.no_wheel_detected),
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.no_wheel_hint, scanPath),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequestFolderPicker) {
                Text(stringResource(R.string.choose_folder))
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text(stringResource(R.string.check_permissions))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
fun WheelListContent(
    wheels: List<WheelIdentity>,
    selectedWheel: WheelIdentity?,
    wheelConfigs: Map<String, io.github.eucsoh.android.data.model.WheelConfig>,
    onSelectWheel: (WheelIdentity) -> Unit,
    onAnalyze: () -> Unit,
    onConfigMosfet: (WheelIdentity) -> Unit,
    error: String?,
    onDismissError: () -> Unit,
    hasResults: Boolean,
    onShowResults: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.detected_eucs, wheels.size),
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
                    onClick = { onSelectWheel(wheel) },
                    hasMosfetConfig = wheelConfigs[wheel.macAddress]?.hasMosfetConfig() == true,
                    onConfigClick = { onConfigMosfet(wheel) }
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
        if (hasResults && selectedWheel != null) {
            OutlinedButton(
                onClick = onShowResults,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp)
            ) {
                Text(stringResource(R.string.view_last_results))
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
                Text(stringResource(R.string.analyze_button, selectedWheel.displayName))
            }
        }
    }
}

@Composable
fun WheelCard(
    wheel: WheelIdentity,
    isSelected: Boolean,
    onClick: () -> Unit,
    hasMosfetConfig: Boolean,
    onConfigClick: () -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: wheel info
            Column(modifier = Modifier.weight(1f)) {
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
                    stringResource(R.string.csv_logs_count, wheel.csvFiles.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    stringResource(R.string.mac_label, wheel.macAddress),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Right: MOSFET badge + config button
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // MOSFET badge
                if (hasMosfetConfig) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.mosfet_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Config button
                IconButton(
                    onClick = onConfigClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.mosfet_config_cd),
                        tint = if (hasMosfetConfig)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
