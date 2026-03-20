package io.github.eucsoh.android.data.repository

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import io.github.eucsoh.android.data.database.WheelDatabase
import io.github.eucsoh.android.data.database.toEntities
import io.github.eucsoh.android.data.database.toWheelIdentities
import io.github.eucsoh.android.data.database.toWheelIdentity
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.scanner.WheelScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for wheel detection with caching.
 * 
 * Strategy:
 * - First load: scan from specified root path/URI + persist to Room
 * - Subsequent loads: use cache unless force refresh
 * - Cache expires after 24h
 */
class WheelRepository(private val context: Context) {

    private val wheelDao = WheelDatabase.getInstance(context).wheelDao()
    private val prefs = context.getSharedPreferences("euc_soh_prefs", Context.MODE_PRIVATE)
    private val scanner = WheelScanner(context)

    companion object {
        private const val TAG = "WheelRepository"
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val PREF_ROOT_PATH = "scan_root_path"
        private const val PREF_ROOT_URI = "scan_root_uri"

        /**
         * Common storage locations to try.
         */
        private val STORAGE_LOCATIONS = listOf(
            //"Download",
            //"Downloads",
            "" // Root of external storage
        )
    }

    /**
     * Gets the configured root path for scanning.
     * Tries multiple common locations.
     */
    fun getRootPath(): File {
        val storedPath = prefs.getString(PREF_ROOT_PATH, null)

        if (storedPath != null) {
            val file = File(storedPath)
            if (file.exists() && file.isDirectory) {
                Log.d(TAG, "Using stored path: $storedPath")
                return file
            }
        }

        // Try common locations
        val externalStorage = Environment.getExternalStorageDirectory()

        for (location in STORAGE_LOCATIONS) {
            val path = if (location.isEmpty()) {
                externalStorage
            } else {
                File(externalStorage, location)
            }

            if (path.exists() && path.isDirectory && path.canRead()) {
                Log.d(TAG, "Found valid storage location: ${path.absolutePath}")
                // Save for next time
                setRootPath(path)
                return path
            }
        }

        // Fallback to external storage root
        Log.d(TAG, "Using fallback path: ${externalStorage.absolutePath}")
        return externalStorage
    }

    /**
     * Sets the root path for scanning (File mode).
     */
    fun setRootPath(path: File) {
        Log.d(TAG, "Setting root path: ${path.absolutePath}")
        prefs.edit()
            .putString(PREF_ROOT_PATH, path.absolutePath)
            .remove(PREF_ROOT_URI)  // Clear URI if switching to File mode
            .apply()
    }

    /**
     * Sets the root URI for scanning (DocumentFile mode).
     */
    fun setRootUri(uri: Uri) {
        Log.d(TAG, "Setting root URI: $uri")
        prefs.edit()
            .putString(PREF_ROOT_URI, uri.toString())
            .remove(PREF_ROOT_PATH)  // Clear path if switching to URI mode
            .apply()
    }

    /**
     * Gets the configured root URI (if in DocumentFile mode).
     */
    fun getRootUri(): Uri? {
        val storedUri = prefs.getString(PREF_ROOT_URI, null) ?: return null
        return Uri.parse(storedUri)
    }

    /**
     * Gets all detected wheels, using cache if available and fresh.
     * 
     * @param forceRefresh If true, ignores cache and scans from scratch
     * @return Map of MAC address -> WheelIdentity
     */
    suspend fun getWheels(
        forceRefresh: Boolean = false
    ): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getWheels called: forceRefresh=$forceRefresh")

        if (forceRefresh) {
            Log.d(TAG, "Force refresh requested, scanning...")
            scanAndCache()
        } else {
            // Try cache first
            val cached = wheelDao.getAllWheels()
            Log.d(TAG, "Cache contains ${cached.size} wheels")

            if (cached.isNotEmpty() && !isCacheExpired(cached)) {
                Log.d(TAG, "Using cached data")
                cached.toWheelIdentities()
            } else {
                Log.d(TAG, "Cache miss or expired, scanning...")
                scanAndCache()
            }
        }
    }

    fun getStorageRoots(): List<File> {
        val roots = mutableListOf<File>()

        // getExternalFilesDirs renvoie un path par volume monté :
        // [/storage/emulated/0/Android/data/<pkg>/files, /storage/0000-0000/Android/data/<pkg>/files, ...]
        // On remonte à la racine du volume en faisant .parentFile 4 fois
        context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
            // appDir = /storage/XXXX/Android/data/<package>/files
            // on veut  /storage/XXXX/
            val volumeRoot = appDir.parentFile?.parentFile?.parentFile?.parentFile
            if (volumeRoot != null && volumeRoot.exists()) {
                Log.d(TAG, "Volume root found: ${volumeRoot.absolutePath}, canRead=${volumeRoot.canRead()}")
                roots.add(volumeRoot)
            }
        }

        Log.d(TAG, "Storage roots: ${roots.map { it.absolutePath }}")
        return roots
    }


    /**
     * Scans using configured path/URI and updates cache.
     */
    private suspend fun scanAndCache(): Map<String, WheelIdentity> {
        val rootUri = getRootUri()

        val wheels = if (rootUri != null) {
            Log.d(TAG, "Scanning from URI: $rootUri")
            scanner.scanFromUri(rootUri)
        } else {
            val roots = getStorageRoots()
            Log.d(TAG, "Scanning from ${roots.size} storage root(s)")
            roots.fold(mutableMapOf<String, WheelIdentity>()) { acc, root ->
                acc.putAll(scanner.scanFromFile(root))
                acc
            }
        }

        Log.d(TAG, "Scan returned ${wheels.size} wheels")
        wheelDao.clearAll()
        wheelDao.insertWheels(wheels.toEntities())
        Log.d(TAG, "Cache updated")
        return wheels
    }


    /**
     * Checks if cached data is older than MAX_AGE.
     */
    private fun isCacheExpired(cached: List<io.github.eucsoh.android.data.database.WheelEntity>): Boolean {
        if (cached.isEmpty()) return true

        val now = System.currentTimeMillis()
        val oldestTimestamp = cached.minOfOrNull { it.lastScanTimestamp } ?: return true

        val expired = (now - oldestTimestamp) > CACHE_MAX_AGE_MS
        Log.d(TAG, "Cache age: ${(now - oldestTimestamp) / 1000 / 60} minutes, expired=$expired")
        return expired
    }

    /**
     * Gets a specific wheel by MAC address.
     */
    suspend fun getWheelByMac(mac: String): WheelIdentity? = withContext(Dispatchers.IO) {
        wheelDao.getWheelByMac(mac)?.toWheelIdentity()
    }

    /**
     * Clears all cached data.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Clearing cache")
        wheelDao.clearAll()
    }
}
