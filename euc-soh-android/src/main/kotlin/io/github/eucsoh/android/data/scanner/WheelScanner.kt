package io.github.eucsoh.android.data.scanner

import android.content.Context
import android.util.Log
import io.github.eucsoh.android.data.model.WheelIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Unified scanner that aggregates results from WheelLog and EUC World.
 * 
 * Starts from an absolute path (user-selected folder) and recursively searches for:
 * - WheelLog folders
 * - EUC World folders
 * 
 * Merges data by MAC address:
 * - WheelLog provides MAC (from folder name) + CSV files
 * - EUC World provides MAC (from CSV content) + metadata (make, model, name) + CSV files
 * 
 * Result: One WheelIdentity per MAC with all CSV files and best available metadata.
 */
class WheelScanner(
    private val context: Context,
    private val rootPath: File
) {

    private val wheelLogScanner = WheelLogScanner(context)
    private val eucWorldScanner = EucWorldScanner(context)

    companion object {
        private const val TAG = "WheelScanner"
    }

    /**
     * Scans from rootPath, finding WheelLog and EUC World folders recursively.
     * Returns a map of MAC -> WheelIdentity with aggregated data.
     */
    suspend fun scanAllWheels(): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting scan from: ${rootPath.absolutePath}")
        
        if (!rootPath.exists()) {
            Log.e(TAG, "Root path does not exist: ${rootPath.absolutePath}")
            return@withContext emptyMap()
        }
        
        if (!rootPath.isDirectory) {
            Log.e(TAG, "Root path is not a directory: ${rootPath.absolutePath}")
            return@withContext emptyMap()
        }

        if (!rootPath.canRead()) {
            Log.e(TAG, "Root path is not readable: ${rootPath.absolutePath}")
            return@withContext emptyMap()
        }

        Log.d(TAG, "Root path is valid, starting recursive search")

        // Find WheelLog and EUC World folders recursively
        val wheelLogFolders = mutableListOf<File>()
        val eucWorldFolders = mutableListOf<File>()

        try {
            var foldersScanned = 0
            rootPath.walkTopDown()
                .maxDepth(10)
                .onEnter { dir ->
                    foldersScanned++
                    if (foldersScanned % 100 == 0) {
                        Log.d(TAG, "Scanned $foldersScanned folders...")
                    }
                    true
                }
                .filter { it.isDirectory }
                .forEach { dir ->
                    when {
                        dir.name.equals("WheelLog", ignoreCase = true) -> {
                            Log.d(TAG, "Found WheelLog folder: ${dir.absolutePath}")
                            wheelLogFolders.add(dir)
                        }
                        dir.name.equals("EUC World", ignoreCase = true) -> {
                            Log.d(TAG, "Found EUC World folder: ${dir.absolutePath}")
                            eucWorldFolders.add(dir)
                        }
                    }
                }
            
            Log.d(TAG, "Scan complete: $foldersScanned folders scanned")
            Log.d(TAG, "Found ${wheelLogFolders.size} WheelLog folders")
            Log.d(TAG, "Found ${eucWorldFolders.size} EUC World folders")
        } catch (e: Exception) {
            Log.e(TAG, "Error during folder walk", e)
        }

        // Scan found folders in parallel
        val wheelLogDeferred = async { 
            try {
                val wheels = wheelLogFolders.flatMap { folder ->
                    Log.d(TAG, "Scanning WheelLog folder: ${folder.absolutePath}")
                    val result = wheelLogScanner.scanFolder(folder)
                    Log.d(TAG, "Found ${result.size} wheels in ${folder.name}")
                    result.values
                }.associateBy { it.macAddress }
                Log.d(TAG, "Total WheelLog wheels: ${wheels.size}")
                wheels
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning WheelLog folders", e)
                emptyMap()
            }
        }
        
        val eucWorldDeferred = async { 
            try {
                val wheels = eucWorldFolders.flatMap { folder ->
                    Log.d(TAG, "Scanning EUC World folder: ${folder.absolutePath}")
                    val result = eucWorldScanner.scanFolder(folder)
                    Log.d(TAG, "Found ${result.size} wheels in ${folder.name}")
                    result.values
                }.associateBy { it.macAddress }
                Log.d(TAG, "Total EUC World wheels: ${wheels.size}")
                wheels
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning EUC World folders", e)
                emptyMap()
            }
        }

        val wheelLogWheels = wheelLogDeferred.await()
        val eucWorldWheels = eucWorldDeferred.await()

        // Merge by MAC address
        val merged = mergeWheels(wheelLogWheels, eucWorldWheels)
        Log.d(TAG, "Final merged result: ${merged.size} unique wheels")
        
        return@withContext merged
    }

    /**
     * Merges wheel data from multiple sources.
     * Prioritizes EUC World metadata (make, model, name) over WheelLog.
     */
    private fun mergeWheels(
        wheelLogWheels: Map<String, WheelIdentity>,
        eucWorldWheels: Map<String, WheelIdentity>
    ): Map<String, WheelIdentity> {
        val result = mutableMapOf<String, WheelIdentity>()

        // Add all WheelLog wheels first
        result.putAll(wheelLogWheels)

        // Merge EUC World data
        eucWorldWheels.forEach { (mac, eucWheel) ->
            result.merge(mac, eucWheel) { existing, new ->
                existing.copy(
                    // Prefer EUC World display name (contains model info)
                    displayName = if (new.displayName != new.macAddress) 
                        new.displayName 
                    else 
                        existing.displayName,
                    
                    // Aggregate CSV files from both sources
                    csvFiles = (existing.csvFiles + new.csvFiles).distinct(),
                    
                    // EUC World provides these, WheelLog doesn't
                    manufacturer = new.manufacturer ?: existing.manufacturer,
                    model = new.model ?: existing.model,
                    serialNumber = new.serialNumber ?: existing.serialNumber,
                    
                    // Keep combined source info (could be useful for debugging)
                    source = if (existing.csvFiles.isNotEmpty() && new.csvFiles.isNotEmpty()) 
                        io.github.eucsoh.android.data.model.WheelDataSource.WHEELLOG  // Both sources
                    else 
                        new.source
                )
            }
        }

        return result
    }
}
