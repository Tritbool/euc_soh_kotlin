package io.github.eucsoh.android.data.scanner

import android.content.Context
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

    /**
     * Scans from rootPath, finding WheelLog and EUC World folders recursively.
     * Returns a map of MAC -> WheelIdentity with aggregated data.
     */
    suspend fun scanAllWheels(): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        if (!rootPath.exists() || !rootPath.isDirectory) {
            return@withContext emptyMap()
        }

        // Find WheelLog and EUC World folders recursively
        val wheelLogFolders = mutableListOf<File>()
        val eucWorldFolders = mutableListOf<File>()

        rootPath.walkTopDown()
            .maxDepth(10)
            .filter { it.isDirectory }
            .forEach { dir ->
                when (dir.name) {
                    "WheelLog" -> wheelLogFolders.add(dir)
                    "EUC World" -> eucWorldFolders.add(dir)
                }
            }

        // Scan found folders in parallel
        val wheelLogDeferred = async { 
            try {
                wheelLogFolders.flatMap { folder ->
                    wheelLogScanner.scanFolder(folder).values
                }.associateBy { it.macAddress }
            } catch (e: Exception) {
                emptyMap()
            }
        }
        
        val eucWorldDeferred = async { 
            try {
                eucWorldFolders.flatMap { folder ->
                    eucWorldScanner.scanFolder(folder).values
                }.associateBy { it.macAddress }
            } catch (e: Exception) {
                emptyMap()
            }
        }

        val wheelLogWheels = wheelLogDeferred.await()
        val eucWorldWheels = eucWorldDeferred.await()

        // Merge by MAC address
        mergeWheels(wheelLogWheels, eucWorldWheels)
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
