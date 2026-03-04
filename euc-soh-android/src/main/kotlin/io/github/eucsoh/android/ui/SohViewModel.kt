package io.github.eucsoh.android.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.AndroidCsvSource
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.repository.WheelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val analysisResult: SohAnalyzer.AnalysisResult? = null,
    val error: String? = null
)

/**
 * ViewModel for SoH analysis.
 */
class SohViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository = WheelRepository(application)
    private val analyzer = SohAnalyzer()
    private val csvSource = AndroidCsvSource(application)
    
    private val _state = MutableStateFlow(SohUiState())
    val state: StateFlow<SohUiState> = _state.asStateFlow()
    
    companion object {
        private const val TAG = "SohViewModel"
    }
    
    init {
        Log.d(TAG, "ViewModel initialized")
        // Load configured root path
        val rootPath = repository.getRootPath().absolutePath
        Log.d(TAG, "Initial root path: $rootPath")
        _state.update { it.copy(scanRootPath = rootPath) }
        // Auto-scan on startup
        scanWheels(forceRefresh = false)
    }
    
    /**
     * Sets the root path for scanning and rescans.
     * 
     * @param path Absolute path to scan (from folder picker)
     */
    fun setScanRootPath(path: String) {
        Log.d(TAG, "Setting scan root path: $path")
        val file = File(path)
        
        if (!file.exists()) {
            val error = "Chemin inexistant: $path"
            Log.e(TAG, error)
            _state.update { it.copy(error = error) }
            return
        }
        
        if (!file.isDirectory) {
            val error = "Pas un dossier: $path"
            Log.e(TAG, error)
            _state.update { it.copy(error = error) }
            return
        }
        
        if (!file.canRead()) {
            val error = "Dossier non lisible: $path"
            Log.e(TAG, error)
            _state.update { it.copy(error = error) }
            return
        }
        
        Log.d(TAG, "Path is valid, saving and rescanning")
        repository.setRootPath(file)
        _state.update { it.copy(scanRootPath = path, error = null) }
        scanWheels(forceRefresh = true)
    }
    
    /**
     * Gets the current configured scan root path.
     */
    fun getScanRootPath(): String {
        return repository.getRootPath().absolutePath
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
     * Switches to manual folder selection mode.
     */
    fun switchToManualMode() {
        Log.d(TAG, "Switched to manual mode")
        _state.update { it.copy(
            analysisMode = AnalysisMode.MANUAL_FOLDER,
            selectedWheel = null,
            error = null
        )}
    }
    
    /**
     * Selects a manual folder.
     */
    fun selectManualFolder(folderUri: Uri) {
        Log.d(TAG, "Manual folder selected: $folderUri")
        _state.update { it.copy(
            manualFolderUri = folderUri,
            analysisMode = AnalysisMode.MANUAL_FOLDER,
            error = null
        )}
    }
    
    /**
     * Starts SoH analysis.
     */
    fun startAnalysis() {
        Log.d(TAG, "Starting analysis")
        val currentState = _state.value
        
        val csvPaths = when (currentState.analysisMode) {
            AnalysisMode.AUTO_DETECT -> {
                currentState.selectedWheel?.csvFiles?.map { it.toString() }
            }
            AnalysisMode.MANUAL_FOLDER -> {
                // TODO: List CSV files in manual folder
                currentState.manualFolderUri?.let { uri ->
                    // For now, just use the URI as single CSV
                    // In practice, need to enumerate folder contents
                    listOf(uri.toString())
                }
            }
        }
        
        if (csvPaths.isNullOrEmpty()) {
            val error = "Aucun fichier à analyser"
            Log.w(TAG, error)
            _state.update { it.copy(error = error) }
            return
        }
        
        Log.d(TAG, "Analyzing ${csvPaths.size} files")
        
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true, error = null) }
            
            try {
                val result = analyzer.analyzeFolderForReq(
                    csvPaths = csvPaths,
                    optimalFrac = 0.3,
                    parallel = false
                )
                
                Log.d(TAG, "Analysis complete")
                _state.update { it.copy(
                    analysisResult = result,
                    isAnalyzing = false
                )}
            } catch (e: Exception) {
                val error = "Erreur analyse: ${e.message}"
                Log.e(TAG, error, e)
                _state.update { it.copy(
                    isAnalyzing = false,
                    error = error
                )}
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
