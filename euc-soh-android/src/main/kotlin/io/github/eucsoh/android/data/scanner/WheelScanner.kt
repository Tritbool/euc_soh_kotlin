package io.github.eucsoh.android.data.scanner

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.github.eucsoh.android.data.model.WheelIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Unified scanner that aggregates results from WheelLog and EUC World.
 * 
 * Supports two input modes:
 * 1. File-based (traditional, requires file system access)
 * 2. DocumentFile-based (modern Android SAF, works everywhere)
 * 
 * Recursively searches for:
 * - WheelLog folders
 * - EUC World folders
 * 
 * Merges data by MAC address from both sources.
 */
class WheelScanner(
    private val context: Context
) {

    private val wheelLogScanner = WheelLogScanner(context)
    private val eucWorldScanner = EucWorldScanner(context)

    companion object {
        private const val TAG = "WheelScanner"
        private const val MAX_DEPTH = 10
    }

    /**
     * Scans from File path.
     */
    suspend fun scanFromFile(rootPath: File): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting File-based scan from: ${rootPath.absolutePath}")
        
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

        val wheelLogFolders = mutableListOf<File>()
        val eucWorldFolders = mutableListOf<File>()

        try {
            var foldersScanned = 0
            rootPath.walkTopDown()
                .maxDepth(MAX_DEPTH)
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

        return@withContext scanFoundFolders(wheelLogFolders, eucWorldFolders)
    }

    /**
     * Scans from DocumentFile URI (Android SAF).
     */
    suspend fun scanFromUri(rootUri: Uri): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting DocumentFile-based scan from: $rootUri")
        
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
        if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
            Log.e(TAG, "Invalid root URI")
            return@withContext emptyMap()
        }

        Log.d(TAG, "Root URI is valid, starting recursive search")

        val wheelLogDocs = mutableListOf<DocumentFile>()
        val eucWorldDocs = mutableListOf<DocumentFile>()

        try {
            var foldersScanned = 0
            walkDocumentTree(rootDoc, 0) { doc, depth ->
                foldersScanned++
                if (foldersScanned % 100 == 0) {
                    Log.d(TAG, "Scanned $foldersScanned folders...")
                }
                
                val name = doc.name ?: return@walkDocumentTree
                when {
                    name.equals("WheelLog", ignoreCase = true) -> {
                        Log.d(TAG, "Found WheelLog folder: ${doc.uri}")
                        wheelLogDocs.add(doc)
                    }
                    name.equals("EUC World", ignoreCase = true) -> {
                        Log.d(TAG, "Found EUC World folder: ${doc.uri}")
                        eucWorldDocs.add(doc)
                    }
                }
            }
            
            Log.d(TAG, "Scan complete: $foldersScanned folders scanned")
            Log.d(TAG, "Found ${wheelLogDocs.size} WheelLog folders")
            Log.d(TAG, "Found ${eucWorldDocs.size} EUC World folders")
        } catch (e: Exception) {
            Log.e(TAG, "Error during document tree walk", e)
        }

        return@withContext scanFoundDocuments(wheelLogDocs, eucWorldDocs)
    }

    /**
     * Recursively walks DocumentFile tree.
     */
    private fun walkDocumentTree(
        doc: DocumentFile,
        depth: Int,
        action: (DocumentFile, Int) -> Unit
    ) {
        if (depth > MAX_DEPTH) return
        if (!doc.isDirectory) return

        action(doc, depth)

        doc.listFiles().forEach { child ->
            if (child.isDirectory) {
                walkDocumentTree(child, depth + 1, action)
            }
        }
    }

    /**
     * Scans File-based folders.
     */
    private suspend fun scanFoundFolders(
        wheelLogFolders: List<File>,
        eucWorldFolders: List<File>
    ): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
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

        return@withContext mergeWheels(wheelLogWheels, eucWorldWheels)
    }

    /**
     * Scans DocumentFile-based folders.
     */
    private suspend fun scanFoundDocuments(
        wheelLogDocs: List<DocumentFile>,
        eucWorldDocs: List<DocumentFile>
    ): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        val wheelLogDeferred = async { 
            try {
                val wheels = wheelLogDocs.flatMap { doc ->
                    Log.d(TAG, "Scanning WheelLog document: ${doc.uri}")
                    val result = wheelLogScanner.scanDocument(doc)
                    Log.d(TAG, "Found ${result.size} wheels in ${doc.name}")
                    result.values
                }.associateBy { it.macAddress }
                Log.d(TAG, "Total WheelLog wheels: ${wheels.size}")
                wheels
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning WheelLog documents", e)
                emptyMap()
            }
        }
        
        val eucWorldDeferred = async { 
            try {
                val wheels = eucWorldDocs.flatMap { doc ->
                    Log.d(TAG, "Scanning EUC World document: ${doc.uri}")
                    val result = eucWorldScanner.scanDocument(doc)
                    Log.d(TAG, "Found ${result.size} wheels in ${doc.name}")
                    result.values
                }.associateBy { it.macAddress }
                Log.d(TAG, "Total EUC World wheels: ${wheels.size}")
                wheels
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning EUC World documents", e)
                emptyMap()
            }
        }

        val wheelLogWheels = wheelLogDeferred.await()
        val eucWorldWheels = eucWorldDeferred.await()

        return@withContext mergeWheels(wheelLogWheels, eucWorldWheels)
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

        result.putAll(wheelLogWheels)

        eucWorldWheels.forEach { (mac, eucWheel) ->
            result.merge(mac, eucWheel) { existing, new ->
                existing.copy(
                    displayName = if (new.displayName != new.macAddress) 
                        new.displayName 
                    else 
                        existing.displayName,
                    csvFiles = (existing.csvFiles + new.csvFiles).distinct(),
                    manufacturer = new.manufacturer ?: existing.manufacturer,
                    model = new.model ?: existing.model,
                    serialNumber = new.serialNumber ?: existing.serialNumber,
                    source = if (existing.csvFiles.isNotEmpty() && new.csvFiles.isNotEmpty()) 
                        io.github.eucsoh.android.data.model.WheelDataSource.WHEELLOG
                    else 
                        new.source
                )
            }
        }

        return result
    }
}
