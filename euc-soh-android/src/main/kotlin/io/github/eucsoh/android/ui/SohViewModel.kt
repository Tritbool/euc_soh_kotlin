package io.github.eucsoh.android.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.android.AndroidCsvSource
import io.github.eucsoh.android.AndroidLogger
import io.github.eucsoh.android.data.model.CsvFileInfo
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.repository.WheelRepository
import io.github.eucsoh.analysis.ReqStatsComputer
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
    val isValidating: Boolean = false,
    val isAnalyzing: Boolean = false,
    val csvFileDetails: List<CsvFileInfo> = emptyList(),
    val showFileDetails: Boolean = false,
    val analysisResult: SohAnalyzer.AnalysisResult? = null,
    val error: String? = null
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
     * Sets the root path for scanning and rescans.
     * 
     * @param path Absolute path to scan
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
        updateScanPathDisplay()
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
            error = null,
            showFileDetails = false,
            csvFileDetails = emptyList()
        )}
    }
    
    /**
     * Validates all CSV files and shows details.
     */
    fun validateFiles() {
        Log.d(TAG, "Validating files")
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
            val error = "Aucun fichier à valider"
            Log.w(TAG, error)
            _state.update { it.copy(error = error) }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isValidating = true, error = null) }
            
            try {
                val fileInfos = withContext(Dispatchers.IO) {
                    csvPaths.map { path ->
                        validateCsvFile(path)
                    }
                }
                
                val validCount = fileInfos.count { it.isValid && !it.isExcluded }
                Log.d(TAG, "Validation complete: $validCount/${fileInfos.size} files valid")
                
                _state.update { it.copy(
                    csvFileDetails = fileInfos,
                    showFileDetails = true,
                    isValidating = false
                )}
            } catch (e: Exception) {
                val error = "Erreur validation: ${e.message}"
                Log.e(TAG, error, e)
                _state.update { it.copy(
                    isValidating = false,
                    error = error
                )}
            }
        }
    }
    
    /**
     * Validates a single CSV file and returns info.
     */
    private suspend fun validateCsvFile(csvPath: String): CsvFileInfo {
        val uri = Uri.parse(csvPath)
        val fileName = csvPath.substringAfterLast('/')
        
        // Try to get file size
        val sizeBytes = try {
            val cursor = getApplication<Application>().contentResolver.query(
                uri, null, null, null, null
            )
            cursor?.use {
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (it.moveToFirst() && sizeIndex >= 0) {
                    it.getLong(sizeIndex)
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
        
        // Try to compute stats
        val stats = try {
            ReqStatsComputer.computeReqStatsForFile(
                csvPath = csvPath,
                csvSource = csvSource
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to process $fileName: ${e.message}")
            null
        }
        
        return if (stats == null) {
            CsvFileInfo(
                uri = uri,
                fileName = fileName,
                sizeBytes = sizeBytes,
                isValid = false,
                validationMessage = "❌ Illisible ou colonnes manquantes",
                nPoints = null,
                hasTemperature = false,
                reqMedian = null,
                wheelKm = null
            )
        } else {
            val isValid = stats.nPoints >= 50 && stats.reqMedian > 0.0 && stats.tempBoardMax != null
            val message = when {
                stats.nPoints < 50 -> "❌ Trop court (${stats.nPoints} points)"
                stats.tempBoardMax == null -> "❌ Pas de données température"
                stats.reqMedian <= 0.0 -> "❌ Req invalide"
                else -> "✓ Valide (${stats.nPoints} points)"
            }
            
            CsvFileInfo(
                uri = uri,
                fileName = fileName,
                sizeBytes = sizeBytes,
                isValid = isValid,
                validationMessage = message,
                nPoints = stats.nPoints,
                hasTemperature = stats.tempBoardMax != null,
                reqMedian = stats.reqMedian,
                wheelKm = stats.wheelKm
            )
        }
    }
    
    /**
     * Toggles exclusion of a file.
     */
    fun toggleFileExclusion(fileInfo: CsvFileInfo) {
        Log.d(TAG, "Toggling exclusion for ${fileInfo.fileName}")
        _state.update { state ->
            state.copy(
                csvFileDetails = state.csvFileDetails.map { info ->
                    if (info.uri == fileInfo.uri) {
                        info.copy(isExcluded = !info.isExcluded)
                    } else info
                }
            )
        }
    }
    
    /**
     * Hides file details view.
     */
    fun hideFileDetails() {
        Log.d(TAG, "Hiding file details")
        _state.update { it.copy(showFileDetails = false) }
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
        
        // Use filtered file list if available
        val csvPaths = if (currentState.csvFileDetails.isNotEmpty()) {
            currentState.csvFileDetails
                .filter { it.isValid && !it.isExcluded }
                .map { it.uri.toString() }
        } else {
            when (currentState.analysisMode) {
                AnalysisMode.AUTO_DETECT -> {
                    currentState.selectedWheel?.csvFiles?.map { it.toString() }
                }
                AnalysisMode.MANUAL_FOLDER -> {
                    currentState.manualFolderUri?.let { uri ->
                        listOf(uri.toString())
                    }
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
        csvPaths.forEachIndexed { idx, path ->
            Log.d(TAG, "  [$idx] $path")
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzing = true, error = null) }
            
            try {
                Log.d(TAG, "Calling analyzer.analyzeFolderForReq()...")
                val result = analyzer.analyzeFolderForReq(
                    csvPaths = csvPaths,
                    optimalFrac = 0.3,
                    parallel = false
                )
                
                Log.d(TAG, "Analysis completed successfully")
                Log.d(TAG, "  - Ns: ${result.nsGlobal}")
                Log.d(TAG, "  - V nominal: ${result.vNominal}")
                Log.d(TAG, "  - R pack nominal: ${result.rPackNominal}")
                Log.d(TAG, "  - Ea: ${result.eaJPerMol / 1000} kJ/mol")
                Log.d(TAG, "  - Alarms: ${result.alarms.size}")
                
                _state.update { it.copy(
                    analysisResult = result,
                    isAnalyzing = false,
                    showFileDetails = false
                )}
            } catch (e: ClassCastException) {
                val error = "Erreur de type dans les données CSV: ${e.message}\n\nCertaines colonnes attendues sont manquantes ou invalides. Vérifiez que vos logs contiennent toutes les données nécessaires (tension, courant, température, etc.)."
                Log.e(TAG, "ClassCastException during analysis", e)
                e.printStackTrace()
                _state.update { it.copy(
                    isAnalyzing = false,
                    error = error
                )}
            } catch (e: RuntimeException) {
                val error = when {
                    e.message?.contains("No exploitable logs") == true -> {
                        "Aucun log exploitable pour calibration.\n\nPossibles causes:\n" +
                        "- Logs trop courts (<100 points)\n" +
                        "- Colonnes manquantes (voltage, current, extra)\n" +
                        "- Pas assez de variation de température"
                    }
                    else -> "Erreur analyse: ${e.message}"
                }
                Log.e(TAG, "RuntimeException during analysis", e)
                e.printStackTrace()
                _state.update { it.copy(
                    isAnalyzing = false,
                    error = error
                )}
            } catch (e: Exception) {
                val error = "Erreur inattendue: ${e.javaClass.simpleName}: ${e.message}"
                Log.e(TAG, "Unexpected exception during analysis", e)
                e.printStackTrace()
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
