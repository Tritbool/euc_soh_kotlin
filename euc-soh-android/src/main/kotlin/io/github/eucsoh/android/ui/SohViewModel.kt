package io.github.eucsoh.android.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eucsoh.ProgressState
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.AndroidCsvSource
import io.github.eucsoh.android.AndroidLogger
import io.github.eucsoh.android.data.model.WheelConfig
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.repository.WheelConfigRepository
import io.github.eucsoh.android.data.repository.WheelRepository
import io.github.eucsoh.model.MOSFETParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var progressState: ProgressState? = null
)

/**
 * ViewModel for SoH analysis.
 */
class SohViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WheelRepository(application)
    private val configRepository = WheelConfigRepository(application)
    private val csvSource = AndroidCsvSource(application)
    private val logger = AndroidLogger()
    
    private val _state = MutableStateFlow(SohUiState())
    val state: StateFlow<SohUiState> = _state.asStateFlow()
    
    private val prefs = application.getSharedPreferences("euc_soh_prefs", Context.MODE_PRIVATE)

    private val _progressState = MutableStateFlow<ProgressState?>(null)
    val progressState: StateFlow<ProgressState?> = _progressState.asStateFlow()


    companion object {
        private const val TAG = "SohViewModel"
    }
    
    init {
        Log.d(TAG, "ViewModel initialized")

        
        updateScanPathDisplay()
        // Auto-scan on startup
        scanWheels(forceRefresh = false)
    }

    
    /**
     * Updates the scan path display in UI state.
     */
    private fun updateScanPathDisplay() {
        val uri = repository.getRootUri()
        val path = if (uri != null) {
            "URI: $uri"
        } else {
            repository.getRootPath().absolutePath
        }
        Log.d(TAG, "Current scan path: $path")
        _state.update { it.copy(scanRootPath = path) }
    }
    
    /**
     * Sets the root URI for scanning (from folder picker) and rescans.
     */
    fun setRootUri(uri: Uri) {
        Log.d(TAG, "Setting root URI: $uri")
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
                    Log.d(TAG, "  $mac: MOSFET configured (R_ds=${config.mosfetParams?.rDsOn25cTotal})")
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
                val wheels = repository.getWheels(forceRefresh)
                Log.d(TAG, "Scan complete: ${wheels.size} wheels found")
                wheels.forEach { (mac, wheel) ->
                    Log.d(TAG, "  - $mac: ${wheel.displayName} (${wheel.csvFiles.size} files)")
                }
                _state.update { it.copy(
                    detectedWheels = wheels,
                    isScanning = false
                )}
                
                // Load configs after scan
                loadWheelConfigs()
            } catch (e: Exception) {
                val error = "Erreur scan: ${e.message}"
                Log.e(TAG, error, e)
                _state.update { it.copy(
                    isScanning = false,
                    error = error
                )}
            }
        }
    }
    
    /**
     * Selects a wheel for analysis (auto-detect mode).
     */
    fun selectWheel(wheel: WheelIdentity) {
        Log.d(TAG, "Wheel selected: ${wheel.displayName}")
        _state.update { it.copy(
            selectedWheel = wheel,
            analysisMode = AnalysisMode.AUTO_DETECT,
            error = null
        )}
    }
    
    /**
     * Shows MOSFET config dialog for a wheel.
     */
    fun showMosfetConfig(wheel: WheelIdentity) {
        Log.d(TAG, "Showing MOSFET config for ${wheel.displayName}")
        _state.update { it.copy(
            showMosfetDialog = true,
            configDialogWheel = wheel
        )}
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
        
        Log.d(TAG, "Saving MOSFET config for ${wheel.displayName}: R_ds=${params.rDsOn25cTotal}")
        viewModelScope.launch {
            try {
                configRepository.saveMosfetParams(
                    macAddress = wheel.macAddress,
                    rDsOn25cTotal = params.rDsOn25cTotal,
                    tempCoeffRel = params.tempCoeffRel,
                    rWiring = params.rWiring
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
            val error = "Aucun fichier valide à analyser"
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
            Log.d(TAG, "Using MOSFET params: R_ds=${mosfetParams.rDsOn25cTotal}, coeff=${mosfetParams.tempCoeffRel}")
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
                _state.update { it.copy(
                    isAnalyzing = true,
                    currentFile = 0,
                    totalFiles = csvPaths.size,
                    currentFileName = "",
                    error = null
                )}
            }
            
            try {
                Log.d(TAG, "Calling analyzer.analyzeFolderForReq()...")
                
                // Create a custom logger that updates progress
                val progressLogger = object : io.github.eucsoh.Logger {
                    override fun d(tag: String, message: String) {
                        logger.d(tag, message)
                        
                        // Extract progress from log messages
                        if (message.contains("Processing [")) {
                            val match = "Processing \\[(\\d+)/(\\d+)\\] (.+)".toRegex().find(message)
                            if (match != null) {
                                val current = match.groupValues[1].toIntOrNull() ?: 0
                                val total = match.groupValues[2].toIntOrNull() ?: 0
                                val filename = match.groupValues[3]
                                
                                viewModelScope.launch(Dispatchers.Main) {
                                    _state.update { it.copy(
                                        currentFile = current + 1,
                                        totalFiles = total,
                                        currentFileName = filename
                                    )}
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
                        _state.update { it.copy(
                            progressState = ProgressState(
                                current = current,
                                total = total,
                                phase = phase
                            )
                        )}
                    }

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
                        val summary = analyzerWithConfig.buildSummary(result, selectedWheel?.displayName ?: "unknown")
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
                    _state.update { it.copy(
                        analysisResult = result,
                        isAnalyzing = false
                    )}
                }
            } catch (e: Exception) {
                val error = "Erreur analyse: ${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "Analysis failed", e)
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(
                        isAnalyzing = false,
                        error = error
                    )}
                }
            }
        }
    }
    
    /**
     * Clears analysis results.
     */
    fun clearResults() {
        Log.d(TAG, "Clearing results")
        _state.update { it.copy(
            analysisResult = null,
            error = null
        )}
    }
    
    /**
     * Clears error message.
     */
    fun clearError() {
        Log.d(TAG, "Clearing error")
        _state.update { it.copy(error = null) }
    }
}
