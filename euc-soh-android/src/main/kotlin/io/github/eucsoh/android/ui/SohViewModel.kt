package io.github.eucsoh.android.ui

import android.app.Application
import android.net.Uri
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
    
    init {
        // Load configured root path
        _state.update { it.copy(scanRootPath = repository.getRootPath().absolutePath) }
        // Auto-scan on startup
        scanWheels(forceRefresh = false)
    }
    
    /**
     * Sets the root path for scanning and rescans.
     * 
     * @param path Absolute path to scan (from folder picker)
     */
    fun setScanRootPath(path: String) {
        val file = File(path)
        if (file.exists() && file.isDirectory) {
            repository.setRootPath(file)
            _state.update { it.copy(scanRootPath = path, error = null) }
            scanWheels(forceRefresh = true)
        } else {
            _state.update { it.copy(error = "Chemin invalide: $path") }
        }
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
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, error = null) }
            
            try {
                val wheels = repository.getWheels(forceRefresh)
                _state.update { it.copy(
                    detectedWheels = wheels,
                    isScanning = false
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    isScanning = false,
                    error = "Erreur scan: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * Selects a wheel for analysis (auto-detect mode).
     */
    fun selectWheel(wheel: WheelIdentity) {
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
            _state.update { it.copy(error = "Aucun fichier à analyser") }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true, error = null) }
            
            try {
                val result = analyzer.analyzeFolderForReq(
                    csvPaths = csvPaths,
                    optimalFrac = 0.3,
                    parallel = false
                )
                
                _state.update { it.copy(
                    analysisResult = result,
                    isAnalyzing = false
                )}
            } catch (e: Exception) {
                _state.update { it.copy(
                    isAnalyzing = false,
                    error = "Erreur analyse: ${e.message}"
                )}
            }
        }
    }
    
    /**
     * Clears analysis results.
     */
    fun clearResults() {
        _state.update { it.copy(
            analysisResult = null,
            error = null
        )}
    }
    
    /**
     * Clears error message.
     */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
