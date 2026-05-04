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

package io.github.eucsoh.android.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eucsoh.ProgressState
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.R
import io.github.eucsoh.android.AndroidCsvSource
import io.github.eucsoh.android.AndroidLogger
import io.github.eucsoh.android.data.model.WheelConfig
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.repository.WheelConfigRepository
import io.github.eucsoh.android.data.repository.WheelRepository
import io.github.eucsoh.android.visualization.ArchiveImportService
import io.github.eucsoh.android.visualization.ImportResult
import io.github.eucsoh.model.MOSFETParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Analysis mode selection.
 */
enum class AnalysisMode {
    AUTO_DETECT,   // Detect wheels from WheelLog/EUC World
    MANUAL_FOLDER  // User selects a single folder
}

/**
 * Main UI state.
 */
data class SohUiState(
    val detectedWheels: Map<String, WheelIdentity> = emptyMap(),
    val selectedWheel: WheelIdentity? = null,
    val scanRootPath: String = "",
    val manualFolderUri: Uri? = null,
    val analysisMode: AnalysisMode = AnalysisMode.AUTO_DETECT,
    val isScanning: Boolean = false,
    val isAnalyzing: Boolean = false,
    val currentFile: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val analysisResult: SohAnalyzer.AnalysisResult? = null,
    val error: String? = null,
    // MOSFET config support
    val wheelConfigs: Map<String, WheelConfig> = emptyMap(),
    val showMosfetDialog: Boolean = false,
    val configDialogWheel: WheelIdentity? = null,
    var progressState: ProgressState? = null,
    val showResults: Boolean = false,
    val aliasInput: String = "",
    val lastExportMime: String? = null,    // "application/pdf", "text/csv", "application/zip"
    val lastExportPath: String? = null,    // juste pour debug / logs (optionnel)
    val darknessBotEnabled: Boolean = false,
    val showDarknessBotWarningDialog: Boolean = false,
    val showImportDialog: Boolean = false,
    val importResult: ImportResult? = null,
    val importProgress: Float = 0f
)

/**
 * ViewModel for SoH analysis.
 */
class SohViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WheelRepository(application)
    private val configRepository = WheelConfigRepository(application)
    private val csvSource = AndroidCsvSource(application)
    private val logger = AndroidLogger()
    private val importService = ArchiveImportService(application)

    private val _state = MutableStateFlow(SohUiState())
    val state: StateFlow<SohUiState> = _state.asStateFlow()

    companion object {
        private const val TAG = "SohViewModel"
        private const val PREFS_NAME = "soh_settings"
        private const val PREF_DARKNESSBOT_ENABLED = "darknessbot_enabled"
    }

    init {
        clearExportCache(application)
        Log.d(TAG, "ViewModel initialized")
        updateScanPathDisplay()
        // Restore persisted DarknessBot setting
        val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dbEnabled = prefs.getBoolean(PREF_DARKNESSBOT_ENABLED, false)
        _state.update { it.copy(darknessBotEnabled = dbEnabled) }
        // Auto-scan on startup
        scanWheels(forceRefresh = false)
    }

    private fun clearExportCache(context: Context) {
        val pdfCsv = File(context.getExternalFilesDir(null), "EUC_SoH") ?: return
        pdfCsv.listFiles()
            ?.filter { it.extension in listOf("pdf", "csv") }
            ?.forEach { it.delete() }

        val archives = File(context.getExternalFilesDir(null), "EUC_SoH_Archives") ?: return
        archives.listFiles()
            ?.filter { it.extension in listOf("zip") }
            ?.forEach { it.delete() }

        val csvSourceShare = File(context.cacheDir, "csv_share") ?: return
        csvSourceShare.listFiles()
            ?.filter { it.extension in listOf("csv") }
            ?.forEach { it.delete() }

        File(context.cacheDir, "dbb_repack_tmp")
            .listFiles()
            ?.filter { it.extension == "dbb" }
            ?.forEach { it.delete() }
    }
    /**
     * Called when the user taps the DarknessBot toggle.
     * - If currently disabled → show warning dialog (activation requires confirmation).
     * - If currently enabled  → disable immediately, no confirmation needed.
     */
    fun requestDarknessBotToggle() {
        val current = _state.value.darknessBotEnabled
        if (current) {
            // Disable immediately
            setDarknessBotEnabled(false)
        } else {
            // Show warning before enabling
            _state.update { it.copy(showDarknessBotWarningDialog = true) }
        }
    }

    /** User confirmed activation in the warning dialog. */
    fun confirmDarknessBotEnable() {
        _state.update { it.copy(showDarknessBotWarningDialog = false) }
        setDarknessBotEnabled(true)
    }

    /** User dismissed (cancelled) the warning dialog. */
    fun dismissDarknessBotWarning() {
        _state.update { it.copy(showDarknessBotWarningDialog = false) }
    }

    private fun setDarknessBotEnabled(enabled: Boolean) {
        Log.d(TAG, "DarknessBot enabled: $enabled")
        getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_DARKNESSBOT_ENABLED, enabled).apply()
        _state.update { it.copy(darknessBotEnabled = enabled) }
        // Re-scan so the wheel list reflects the new setting
        scanWheels(forceRefresh = true)
    }

    fun markLastExport(mime: String, name: String?) {
        _state.update { it.copy(lastExportMime = mime, lastExportPath = name) }
    }

    /**
     * Updates the scan path display in UI state.
     */
    private fun updateScanPathDisplay() {
        val uri = repository.getRootUri()
        val path = if (uri != null) {
            "URI: $uri"
        } else {
            getApplication<Application>().getString(R.string.scan_folder_not_selected)
        }
        Log.d(TAG, "Current scan path: $path")
        _state.update { it.copy(scanRootPath = path) }
    }

    fun selectScanRoot(uri: Uri) {
        val context = getApplication<Application>()
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.w(
                TAG,
                "Unable to persist URI permission for $uri. This can happen if the picker did not grant persistable read access.",
                e
            )
        }
        repository.setRootUri(uri)
        updateScanPathDisplay()
        scanWheels(forceRefresh = true)
    }


    /**
     * Loads wheel configs from repository.
     */
    private fun loadWheelConfigs() {
        Log.d(TAG, "Loading wheel configs...")
        viewModelScope.launch {
            val wheels = _state.value.detectedWheels
            val configs = wheels.keys.associateWith { mac ->
                configRepository.getConfig(mac)
            }
            Log.d(TAG, "Loaded ${configs.size} wheel configs")
            configs.forEach { (mac, config) ->
                if (config.hasMosfetConfig()) {
                    Log.d(
                        TAG,
                        "  $mac: MOSFET configured (R_ds=${config.mosfetParams?.rDsOn25cTotal})"
                    )
                }
            }
            _state.update { it.copy(wheelConfigs = configs) }
        }
    }

    /**
     * Scans for wheels (with optional force refresh).
     */
    fun scanWheels(forceRefresh: Boolean = false) {
        Log.d(TAG, "Starting wheel scan (forceRefresh=$forceRefresh)")
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, error = null) }

            try {
                val wheels = repository.getWheels(forceRefresh, _state.value.darknessBotEnabled)
                Log.d(TAG, "Scan complete: ${wheels.size} wheels found")
                wheels.forEach { (mac, wheel) ->
                    Log.d(TAG, "  - $mac: ${wheel.displayName} (${wheel.csvFiles.size} files)")
                }
                _state.update {
                    it.copy(
                        detectedWheels = wheels,
                        isScanning = false
                    )
                }

                // Load configs after scan
                loadWheelConfigs()
            } catch (e: Exception) {
                val error = "Erreur scan: ${e.message}"
                Log.e(TAG, error, e)
                _state.update {
                    it.copy(
                        isScanning = false,
                        error = error
                    )
                }
            }
        }
    }

    /**
     * Selects a wheel for analysis (auto-detect mode).
     */
    fun selectWheel(wheel: WheelIdentity) {
        Log.d(TAG, "Wheel selected: ${wheel.displayName}")
        _state.update {
            it.copy(
                selectedWheel = wheel,
                analysisMode = AnalysisMode.AUTO_DETECT,
                error = null
            )
        }
    }

    /**
     * Shows MOSFET config dialog for a wheel.
     */
    fun showMosfetConfig(wheel: WheelIdentity) {
        Log.d(TAG, "Showing MOSFET config for ${wheel.displayName}")
        _state.update {
            it.copy(
                showMosfetDialog = true,
                configDialogWheel = wheel,
                aliasInput = wheel.userAlias ?: ""
            )
        }
    }

    /**
     * Saves MOSFET config for current dialog wheel.
     */
    fun saveMosfetConfig(params: MOSFETParams) {
        val wheel = _state.value.configDialogWheel
        if (wheel == null) {
            Log.w(TAG, "Cannot save MOSFET config: no wheel selected")
            return
        }

        Log.d(TAG, "Saving MOSFET config for ${wheel.displayName}: R_ds=${params.rDsOn25cTotal?:-1}")
        viewModelScope.launch {
            try {
                configRepository.saveMosfetParams(
                    macAddress = wheel.macAddress,
                    rDsOn25cTotal = params.rDsOn25cTotal,
                    tempCoeffRel = params.tempCoeffRel,
                    rWiring = params.rWiring,
                    nParallel=params.nParallel
                )
                Log.d(TAG, "MOSFET config saved successfully")

                // Reload configs
                loadWheelConfigs()

                // Close dialog
                _state.update { it.copy(showMosfetDialog = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save MOSFET config", e)
                _state.update { it.copy(error = "Erreur sauvegarde config: ${e.message}") }
            }
        }
    }

    /**
     * Clears MOSFET config for current dialog wheel.
     */
    fun clearMosfetConfig() {
        val wheel = _state.value.configDialogWheel
        if (wheel == null) {
            Log.w(TAG, "Cannot clear MOSFET config: no wheel selected")
            return
        }

        Log.d(TAG, "Clearing MOSFET config for ${wheel.displayName}")
        viewModelScope.launch {
            try {
                configRepository.clearMosfetParams(wheel.macAddress)
                Log.d(TAG, "MOSFET config cleared successfully")

                // Reload configs
                loadWheelConfigs()

                // Close dialog
                _state.update { it.copy(showMosfetDialog = false) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear MOSFET config", e)
                _state.update { it.copy(error = "Erreur suppression config: ${e.message}") }
            }
        }
    }

    /**
     * Dismisses MOSFET config dialog.
     */
    fun dismissMosfetDialog() {
        Log.d(TAG, "Dismissing MOSFET config dialog")
        _state.update { it.copy(showMosfetDialog = false) }
    }

    fun updateAliasInput(value: String) {
        _state.update { it.copy(aliasInput = value) }
    }

    fun renameWheel(macAddress: String, alias: String) {
        viewModelScope.launch {
            val wheel = _state.value.detectedWheels[macAddress] ?: return@launch
            val updated = wheel.copy(userAlias = alias.trim().takeIf { it.isNotEmpty() })
            repository.saveWheel(updated)
            _state.update { state ->
                state.copy(
                    detectedWheels = state.detectedWheels + (macAddress to updated),
                    configDialogWheel = if (state.configDialogWheel?.macAddress == macAddress) updated else state.configDialogWheel,
                    showMosfetDialog = false
                )
            }
            Log.d(TAG, "Renamed wheel $macAddress → alias='${updated.userAlias}'")
        }
    }

    /**
     * Starts SoH analysis with progress tracking.
     */
    fun startAnalysis() {
        Log.d(TAG, "Starting analysis")
        val currentState = _state.value

        val csvPaths = when (currentState.analysisMode) {
            AnalysisMode.AUTO_DETECT -> {
                currentState.selectedWheel?.csvFiles?.map { it.toString() }
            }

            AnalysisMode.MANUAL_FOLDER -> {
                currentState.manualFolderUri?.let { uri ->
                    listOf(uri.toString())
                }
            }
        }

        if (csvPaths.isNullOrEmpty()) {
            val error = "No valid file to analyze"
            Log.w(TAG, error)
            _state.update { it.copy(error = error) }
            return
        }

        // Get MOSFET config if available
        val selectedWheel = currentState.selectedWheel
        val mosfetParams = selectedWheel?.let { wheel ->
            currentState.wheelConfigs[wheel.macAddress]?.mosfetParams
        }

        if (mosfetParams != null) {
            Log.d(
                TAG,
                "Using MOSFET params: R_ds=${mosfetParams.rDsOn25cTotal}, coeff=${mosfetParams.tempCoeffRel}"
            )
        } else {
            Log.d(TAG, "No MOSFET params configured, using Req mode")
        }

        Log.d(TAG, "Analyzing ${csvPaths.size} files:")
        csvPaths.forEachIndexed { idx, path ->
            Log.d(TAG, "  [$idx] $path")
        }

        viewModelScope.launch(Dispatchers.IO) {
            // Update UI on Main thread
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        isAnalyzing = true,
                        currentFile = 0,
                        totalFiles = csvPaths.size,
                        currentFileName = "",
                        error = null
                    )
                }
            }

            try {
                Log.d(TAG, "Calling analyzer.analyzeFolderForReq()...")

                // Create a custom logger that updates progress
                val progressLogger = object : io.github.eucsoh.Logger {
                    override fun d(tag: String, message: String) {
                        logger.d(tag, message)

                        // Extract progress from log messages
                        if (message.contains("Processing [")) {
                            val match =
                                "Processing \\[(\\d+)/(\\d+)\\] (.+)".toRegex().find(message)
                            if (match != null) {
                                val current = match.groupValues[1].toIntOrNull() ?: 0
                                val total = match.groupValues[2].toIntOrNull() ?: 0
                                val filename = match.groupValues[3]

                                viewModelScope.launch(Dispatchers.Main) {
                                    _state.update {
                                        it.copy(
                                            currentFile = current + 1,
                                            totalFiles = total,
                                            currentFileName = filename
                                        )
                                    }
                                }
                            }
                        }
                    }

                    override fun e(tag: String, message: String, throwable: Throwable?) {
                        logger.e(tag, message, throwable)
                    }
                }

                // Run analysis with progress tracking AND mosfet params
                val analyzerWithConfig = SohAnalyzer(
                    csvSource = csvSource,
                    mosfetParams = mosfetParams,  // Pass MOSFET config here
                    logger = progressLogger
                )

                val result = analyzerWithConfig.analyzeFolderForReq(
                    csvPaths = csvPaths,
                    optimalFrac = 1.0,
                    onProgress = { current, total, phase ->
                        _state.update {
                            it.copy(
                                progressState = ProgressState(
                                    current = current,
                                    total = total,
                                    phase = phase
                                )
                            )
                        }
                    },
                    macAddress = selectedWheel?.macAddress

                )

                Log.d(TAG, "Analysis completed successfully")
                Log.d(TAG, "  - Ns: ${result.nsGlobal}")
                Log.d(TAG, "  - V nominal: ${result.vNominal}")
                Log.d(TAG, "  - R pack nominal: ${result.rPackNominal}")
                Log.d(TAG, "  - Ea: ${result.eaJPerMol / 1000} kJ/mol")
                Log.d(TAG, "  - Alarms: ${result.alarms.size}")
                Log.d(TAG, "  - MOSFET used: ${mosfetParams != null}")

                // Check if R_batt separation worked by looking at summary
                if (mosfetParams != null) {
                    try {
                        val summary = analyzerWithConfig.buildSummary(
                            result,
                            selectedWheel?.displayName ?: "unknown"
                        )
                        val hasBattData = summary.globalStats.rBattMedianMin != null
                        if (hasBattData) {
                            Log.d(TAG, "  ✓ R_batt separation successful")
                        } else {
                            Log.d(TAG, "  - R_batt not computed (missing temperature or SoC data)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "  - Could not verify R_batt status: ${e.message}")
                    }
                }

                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            analysisResult = result,
                            isAnalyzing = false,
                            showResults = true
                        )
                    }
                }
                // APRÈS
            } catch (e: Exception) {
                val error = "Erreur analyse: ${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "Analysis failed", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            isAnalyzing = false,
                            progressState = null,    // ← débloque le when
                            error = error
                        )
                    }
                }
            }

        }
    }

    // Cache l'écran résultats SANS effacer les données → appelé par onBack
    fun hideResults() {
        Log.d(TAG, "Hiding results (data preserved)")
        _state.update { it.copy(showResults = false) }
    }

    // Réaffiche les résultats existants → appelé par "View last results"
    fun showResults() {
        Log.d(TAG, "Showing existing results")
        _state.update { it.copy(showResults = true) }
    }


    fun requestImport() {
        _state.update { it.copy(showImportDialog = true) }
    }

    fun dismissImportDialog() {
        _state.update { it.copy(showImportDialog = false, importResult = null) }
    }

    fun performImport(zipUri: Uri, destUri: Uri) {
        viewModelScope.launch {
            val result = importService.import(zipUri, destUri) { progress ->
                _state.update { it.copy(importProgress = progress) }
            }
            _state.update { it.copy(importResult = result, importProgress = 0f) }
            if (result is ImportResult.Success) {
                Log.d(TAG, "Import successful: wheelMac=${result.wheelMac}, triggering re-scan")
                scanWheels(forceRefresh = true)
            }
        }
    }

    /**
     * Clears error message.
     */
    fun clearError() {
        Log.d(TAG, "Clearing error")
        _state.update { it.copy(error = null) }
    }
}
