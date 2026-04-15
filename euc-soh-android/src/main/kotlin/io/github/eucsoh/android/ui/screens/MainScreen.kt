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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eucsoh.Constants.ANALYZING
import io.github.eucsoh.Constants.CALIBRATING
import io.github.eucsoh.Constants.DONE
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.ui.SohViewModel
import io.github.eucsoh.android.ui.onboarding.OnboardingManager
import io.github.eucsoh.android.ui.onboarding.OnboardingStep
import io.github.eucsoh.android.ui.onboarding.SpotlightOverlay
import io.github.eucsoh.android.visualization.ArchiveImportService
import io.github.eucsoh.android.visualization.ArchiveManifest
import io.github.eucsoh.android.visualization.ImportResult
import androidx.compose.ui.res.stringResource
import io.github.eucsoh.android.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.transition.Visibility
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: SohViewModel,
    onRequestPermissions: () -> Unit,
    onOpenInfo: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Onboarding state
    var showMainOnboarding by remember {
        mutableStateOf(!OnboardingManager.hasSeenMain(context))
    }
    var mainOnboardingStep by remember { mutableStateOf(0) }

    // Bounds collected via onGloballyPositioned for spotlight targets
    var refreshBounds by remember { mutableStateOf(Rect.Zero) }
    var darknessBotToggleBounds by remember { mutableStateOf(Rect.Zero) }
    var importArchiveBounds by remember { mutableStateOf(Rect.Zero) }
    var analyzeBounds by remember { mutableStateOf(Rect.Zero) }

    // Build onboarding steps — bounds are updated dynamically via onGloballyPositioned
    val mainOnboardingSteps =
        remember(
            refreshBounds,
            darknessBotToggleBounds,
            importArchiveBounds,
            analyzeBounds,
            state.detectedWheels,
            state.showResults
        ) {

            when {
                state.detectedWheels.isEmpty() ->
                    listOf(
                        OnboardingStep(
                            titleRes = R.string.onboarding_welcome_title,
                            bodyRes = R.string.onboarding_welcome_body
                        ),
                        OnboardingStep(
                            titleRes = R.string.onboarding_scan_title,
                            bodyRes = R.string.onboarding_scan_body,
                            targetBounds = refreshBounds
                        ),
                        OnboardingStep(
                            titleRes = R.string.onboarding_darknessbot_title,
                            bodyRes = R.string.onboarding_darknessbot_body,
                            targetBounds = darknessBotToggleBounds
                        ),

                        OnboardingStep(
                            titleRes = R.string.onboarding_import_title,
                            bodyRes = R.string.onboarding_import_body,
                            targetBounds = importArchiveBounds
                        ),

                        )

                else ->
                    when {
                        !state.showResults -> listOf(
                            OnboardingStep(
                                titleRes = R.string.onboarding_welcome_title,
                                bodyRes = R.string.onboarding_welcome_body
                            ),
                            OnboardingStep(
                                titleRes = R.string.onboarding_scan_title,
                                bodyRes = R.string.onboarding_scan_body,
                                targetBounds = refreshBounds
                            ),
                            OnboardingStep(
                                titleRes = R.string.onboarding_darknessbot_title,
                                bodyRes = R.string.onboarding_darknessbot_body,
                                targetBounds = darknessBotToggleBounds
                            ),

                            OnboardingStep(
                                titleRes = R.string.onboarding_import_title,
                                bodyRes = R.string.onboarding_import_body,
                                targetBounds = importArchiveBounds
                            ),

                            OnboardingStep(
                                titleRes = R.string.onboarding_analyze_title,
                                bodyRes = R.string.onboarding_analyze_body,
                                targetBounds = analyzeBounds
                            ),
                            OnboardingStep(
                                titleRes = R.string.onboarding_export_title,
                                bodyRes = R.string.onboarding_export_body
                            )
                        )

                        else -> emptyList()
                    }

            }

        }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val tileBitmap = ImageBitmap.imageResource(R.drawable.topbar_bg)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .clip(RectangleShape)
                        .drawBehind {
                            // Calcule la largeur de la tuile en gardant le ratio original
                            val originalW = tileBitmap.width.toFloat()
                            val originalH = tileBitmap.height.toFloat()
                            val tileH =
                                size.height                      // on fit à la hauteur du bandeau
                            val tileW = originalW * (tileH / originalH) // largeur proportionnelle

                            var x = 0f
                            while (x < size.width) {
                                drawImage(
                                    image = tileBitmap,
                                    dstOffset = IntOffset(x.toInt(), 0),
                                    dstSize = IntSize(tileW.toInt(), tileH.toInt())
                                )
                                x += tileW - 1f
                            }
                        }
                ) {
                    // Foreground : personnage à gauche
                    Image(
                        painter = painterResource(R.drawable.topbar_fg),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                            .fillMaxHeight()
                            .aspectRatio(1.75f),
                        contentScale = ContentScale.Fit
                    )

                    // Titre centré
                    Text(
                        stringResource(R.string.topbar_title),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1C1E)
                    )

                    // Boutons à droite : ? (onboarding) + Refresh
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        if (!state.showResults && ! state.isScanning && ! state.isAnalyzing) {
                            // Bouton ? pour relancer l'onboarding
                            IconButton(
                                onClick = {
                                    OnboardingManager.resetAll(context)
                                    mainOnboardingStep = 0
                                    showMainOnboarding = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Help,
                                    contentDescription = stringResource(R.string.onboarding_help_cd),
                                    tint = Color(0xFF1A1C1E)
                                )
                            }
                        }

                        // Refresh
                        IconButton(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                refreshBounds = coordinates.boundsInRoot()
                            },
                            onClick = { viewModel.scanWheels(forceRefresh = true) }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.topbar_refresh),
                                tint = Color(0xFF1A1C1E)
                            )
                        }
                    }
                    // Bouton Info (aide) à gauche
                    IconButton(
                        modifier = Modifier.align(Alignment.CenterStart),
                        onClick = onOpenInfo
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.info_title),
                            tint = Color(0xFF1A1C1E)
                        )
                    }
                }
            }
        ) { padding ->
            val topOffsetPx = with(LocalDensity.current) { padding.calculateTopPadding().toPx() }
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
                                state.selectedWheel?.effectiveName ?: "",
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
                            phase = stringResource(R.string.analyzing_phase)
                        )
                    }

                    state.progressState?.phase == CALIBRATING -> {
                        AnalysisProgressScreen(
                            currentFile = state.progressState?.current!!,
                            totalFiles = state.progressState?.total!!,
                            fileName = "",//state.currentFileName,
                            phase = stringResource(R.string.calibrating_phase)
                        )
                    }

                    state.progressState?.phase == DONE && state.analysisResult != null && state.showResults -> {
                        ResultsScreenEnhanced(
                            result = state.analysisResult!!,
                            selectedWheel = state.selectedWheel,
                            lastExportMime = state.lastExportMime,
                            lastExportPath = state.lastExportPath,
                            onMarkExport = viewModel::markLastExport,
                            onBack = viewModel::hideResults,
                            darknessBotEnabled = state.darknessBotEnabled,
                            topOffset = topOffsetPx
                        )
                    }

                    state.detectedWheels.isEmpty() -> {
                        EmptyStateScreen(
                            onRequestPermissions = onRequestPermissions,
                            onRetry = { viewModel.scanWheels(forceRefresh = true) },
                            scanPath = state.scanRootPath,
                            darknessBotEnabled = state.darknessBotEnabled,
                            onDarknessBotToggle = viewModel::requestDarknessBotToggle,
                            onImport = viewModel::requestImport,
                            onDarknessBotToggleBounds = { darknessBotToggleBounds = it },
                            onImportArchiveBounds = { importArchiveBounds = it }
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
                            onShowResults = viewModel::showResults,
                            darknessBotEnabled = state.darknessBotEnabled,
                            onDarknessBotToggle = viewModel::requestDarknessBotToggle,
                            onImport = viewModel::requestImport,
                            onDarknessBotToggleBounds = { darknessBotToggleBounds = it },
                            onImportArchiveBounds = { importArchiveBounds = it },
                            onAnalyzeBounds = { analyzeBounds = it },
                            onboarding = showMainOnboarding
                        )
                    }
                }

                // Import archive flow
                if (state.showImportDialog) {
                    ImportArchiveFlow(
                        onDismiss = viewModel::dismissImportDialog,
                        onPerformImport = viewModel::performImport,
                        importResult = state.importResult,
                        importProgress = state.importProgress
                    )
                }

                // DarknessBot warning dialog
                if (state.showDarknessBotWarningDialog) {
                    AlertDialog(
                        onDismissRequest = viewModel::dismissDarknessBotWarning,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = {
                            Text(stringResource(R.string.darknessbot_warning_title))
                        },
                        text = {
                            Text(stringResource(R.string.darknessbot_warning_body))
                        },
                        confirmButton = {
                            TextButton(onClick = viewModel::confirmDarknessBotEnable) {
                                Text(
                                    stringResource(R.string.darknessbot_warning_confirm),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = viewModel::dismissDarknessBotWarning) {
                                Text(stringResource(R.string.darknessbot_warning_cancel))
                            }
                        }
                    )
                }

                // MOSFET config dialog
                if (state.showMosfetDialog && state.configDialogWheel != null) {
                    val wheel = state.configDialogWheel!!
                    val currentParams = state.wheelConfigs[wheel.macAddress]?.mosfetParams

                    MosfetConfigDialog(
                        wheelName = wheel.displayName,
                        currentParams = currentParams,
                        aliasInput = state.aliasInput,
                        hasDataName = wheel.displayName != wheel.macAddress,
                        onAliasChange = viewModel::updateAliasInput,
                        onSaveAlias = { viewModel.renameWheel(wheel.macAddress, state.aliasInput) },
                        onSave = viewModel::saveMosfetConfig,
                        onClear = viewModel::clearMosfetConfig,
                        onDismiss = viewModel::dismissMosfetDialog
                    )
                }
            }
        }

        // Spotlight onboarding overlay — drawn on top of everything
        if (showMainOnboarding) {
            SpotlightOverlay(
                steps = mainOnboardingSteps,
                currentStep = mainOnboardingStep,
                onNext = {
                    if (mainOnboardingStep < mainOnboardingSteps.lastIndex) {
                        mainOnboardingStep++
                    } else {
                        OnboardingManager.markMainSeen(context)
                        showMainOnboarding = false
                    }
                },
                onPrev = {
                    if (mainOnboardingStep > 0) mainOnboardingStep--
                },
                onDismiss = {
                    OnboardingManager.markMainSeen(context)
                    showMainOnboarding = false
                }
            )
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
    onRetry: () -> Unit,
    scanPath: String,
    darknessBotEnabled: Boolean = false,
    onDarknessBotToggle: () -> Unit = {},
    onImport: () -> Unit = {},
    onDarknessBotToggleBounds: (Rect) -> Unit = {},
    onImportArchiveBounds: (Rect) -> Unit = {},
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
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequestPermissions) {
                Text(stringResource(R.string.check_permissions))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onImport,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onImportArchiveBounds(coordinates.boundsInRoot())
                }
            ) {
                Icon(
                    Icons.Default.Upload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.import_archive_button))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .onGloballyPositioned { coordinates ->
                        onDarknessBotToggleBounds(coordinates.boundsInRoot())
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.darknessbot_toggle_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = darknessBotEnabled,
                    onCheckedChange = { onDarknessBotToggle() }
                )
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
    darknessBotEnabled: Boolean = false,
    onDarknessBotToggle: () -> Unit = {},
    onImport: () -> Unit = {},
    onDarknessBotToggleBounds: (Rect) -> Unit = {},
    onImportArchiveBounds: (Rect) -> Unit = {},
    onAnalyzeBounds: (Rect) -> Unit = {},
    onboarding: Boolean = false,
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
        // DarknessBot toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .onGloballyPositioned { coordinates ->
                    onDarknessBotToggleBounds(coordinates.boundsInRoot())
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.darknessbot_toggle_label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = darknessBotEnabled,
                onCheckedChange = { onDarknessBotToggle() }
            )
        }

        // Import archive button
        OutlinedButton(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp)
                .onGloballyPositioned { coordinates ->
                    onImportArchiveBounds(coordinates.boundsInRoot())
                }
        ) {
            Icon(
                Icons.Default.Upload,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.import_archive_button))
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
        if (onboarding) {
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onGloballyPositioned { coordinates ->
                        onAnalyzeBounds(coordinates.boundsInWindow())
                    }
            ) {
                Text(stringResource(R.string.analyze_button_mock))
            }
        }
        if (selectedWheel != null) {
            Button(
                onClick = onAnalyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onGloballyPositioned { coordinates ->
                        onAnalyzeBounds(coordinates.boundsInRoot())
                    }
            ) {
                Text(stringResource(R.string.analyze_button, selectedWheel.effectiveName))
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
                    wheel.effectiveName,
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

/**
 * Import archive flow:
 * 1. User selects a ZIP file via ACTION_OPEN_DOCUMENT
 * 2. Manifest is read to show confirmation dialog (wheel MAC, file name)
 * 3. User confirms, then selects destination folder via ACTION_OPEN_DOCUMENT_TREE
 * 4. Import is performed, result displayed
 */
private enum class ImportStep {
    WARN,
    PICKING_ZIP,
    READING,
    DETAILS,
    PICKING_FOLDER,
    EXTRACTING,
    RESULT
}

@Composable
fun ImportArchiveFlow(
    onDismiss: () -> Unit,
    onPerformImport: (Uri, Uri) -> Unit,
    importResult: ImportResult?,
    importProgress: Float = 0f
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importService = remember { ArchiveImportService(context) }

    var step by remember { mutableStateOf(ImportStep.WARN) }
    var selectedZipUri by remember { mutableStateOf<Uri?>(null) }
    var selectedZipName by remember { mutableStateOf<String?>(null) }
    var manifest by remember { mutableStateOf<ArchiveManifest?>(null) }

    if (importResult != null && step != ImportStep.RESULT) {
        step = ImportStep.RESULT
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { destUri ->
        if (destUri != null && selectedZipUri != null) {
            step = ImportStep.EXTRACTING
            onPerformImport(selectedZipUri!!, destUri)
        } else {
            onDismiss()
        }
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedZipUri = uri
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            selectedZipName = cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) c.getString(nameIndex) else uri.lastPathSegment
                } else uri.lastPathSegment
            } ?: uri.lastPathSegment
            step = ImportStep.READING
            scope.launch {
                manifest = importService.readManifest(uri)
                step = ImportStep.DETAILS
            }
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(step) {
        when (step) {
            ImportStep.PICKING_ZIP -> zipPickerLauncher.launch(arrayOf("application/zip"))
            ImportStep.PICKING_FOLDER -> folderPickerLauncher.launch(null)
            else -> {}
        }
    }

    when (step) {
        ImportStep.WARN -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_warn_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.import_warn_body))
                        Text(
                            stringResource(R.string.import_warn_filename_example),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { step = ImportStep.PICKING_ZIP }) {
                        Text(stringResource(R.string.import_warn_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.mosfet_cancel))
                    }
                }
            )
        }

        ImportStep.PICKING_ZIP -> { /* SAF picker ouvert, pas de dialog */
        }

        ImportStep.READING -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.import_reading_manifest)) },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(selectedZipName ?: "")
                    }
                },
                confirmButton = {}
            )
        }

        ImportStep.DETAILS -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_confirm_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.import_confirm_file, selectedZipName ?: ""))
                        if (manifest != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(
                                    R.string.import_confirm_wheel_mac,
                                    manifest!!.wheelMac
                                )
                            )
                            Text(
                                stringResource(
                                    R.string.import_confirm_file_count,
                                    manifest!!.files.size
                                )
                            )
                        } else {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.import_confirm_no_manifest),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { step = ImportStep.PICKING_FOLDER },
                        enabled = manifest != null
                    ) {
                        Text(stringResource(R.string.import_confirm_extract))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.mosfet_cancel))
                    }
                }
            )
        }

        ImportStep.PICKING_FOLDER -> { /* SAF folder picker ouvert, pas de dialog */
        }

        ImportStep.EXTRACTING -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.import_extracting_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(selectedZipName ?: "")
                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${(importProgress * 100).toInt()} %",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {}
            )
        }

        ImportStep.RESULT -> {
            val result = importResult ?: return
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        when (result) {
                            is ImportResult.Success -> stringResource(R.string.import_success_title)
                            is ImportResult.Error -> stringResource(R.string.import_error_title)
                        }
                    )
                },
                text = {
                    Text(
                        when (result) {
                            is ImportResult.Success -> stringResource(
                                R.string.import_success_body,
                                result.wheelMac
                            )

                            is ImportResult.Error -> when (result.reason) {
                                "manifest_missing" -> stringResource(R.string.import_error_manifest_missing)
                                "hmac_invalid" -> stringResource(R.string.import_error_hmac_invalid)
                                "file_corrupted" -> stringResource(R.string.import_error_file_corrupted)
                                else -> result.reason
                            }
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ok))
                    }
                }
            )
        }
    }
}
