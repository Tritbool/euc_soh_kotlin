package io.github.eucsoh.android.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.AndroidCsvSource
import io.github.eucsoh.android.AndroidLogger
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.repository.WheelRepository
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
    val useParallelProcessing: Boolean = false
)

/**
 * ViewModel for SoH analysis.
 */
class SohViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WheelRepository(application)
    private val csvSource = AndroidCsvSource(application)
    private val logger = AndroidLogger()
    private val analyzer = SohAnalyzer(
        csvSource = csvSource,
        logger = logger
    )
    
    private val _state = MutableStateFlow(SohUiState())
    val state: StateFlow<SohUiState> = _state.asStateFlow()
    
    private val prefs = application.getSharedPreferences("euc_soh_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "SohViewModel"
        private const val PREF_PARALLEL_PROCESSING = "parallel_processing"
    }
    
    init {
        Log.d(TAG, "ViewModel initialized")
        
        // Load parallel processing preference
        val useParallel = prefs.getBoolean(PREF_PARALLEL_PROCESSING, false)
        _state.update { it.copy(useParallelProcessing = useParallel) }
        
        updateScanPathDisplay()
        // Auto-scan on startup
        scanWheels(forceRefresh = false)
    }
    
    /**
     * Toggles parallel processing on/off.
     */
    fun toggleParallelProcessing() {
        val newValue = !_state.value.useParallelProcessing
        Log.d(TAG, "Toggling parallel processing: $newValue")
        
        prefs.edit().putBoolean(PREF_PARALLEL_PROCESSING, newValue).apply()
        _state.update { it.copy(useParallelProcessing = newValue) }
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
        
        Log.d(TAG, "Analyzing ${csvPaths.size} files:")
        Log.d(TAG, "  Parallel processing: ${currentState.useParallelProcessing}")
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
                
                // Run analysis with progress tracking
                val analyzerWithProgress = SohAnalyzer(
                    csvSource = csvSource,
                    logger = progressLogger
                )
                
                val result = analyzerWithProgress.analyzeFolderForReq(
                    csvPaths = csvPaths,
                    optimalFrac = 0.3,
                    parallel = currentState.useParallelProcessing
                )
                
                Log.d(TAG, "Analysis completed successfully")
                Log.d(TAG, "  - Ns: ${result.nsGlobal}")
                Log.d(TAG, "  - V nominal: ${result.vNominal}")
                Log.d(TAG, "  - R pack nominal: ${result.rPackNominal}")
                Log.d(TAG, "  - Ea: ${result.eaJPerMol / 1000} kJ/mol")
                Log.d(TAG, "  - Alarms: ${result.alarms.size}")
                
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
