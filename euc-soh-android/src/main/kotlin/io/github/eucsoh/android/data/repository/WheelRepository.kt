package io.github.eucsoh.android.data.repository

import android.content.Context
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
 * - First load: scan from specified root path + persist to Room
 * - Subsequent loads: use cache unless force refresh
 * - Cache expires after 24h
 */
class WheelRepository(private val context: Context) {
    
    private val wheelDao = WheelDatabase.getInstance(context).wheelDao()
    private val prefs = context.getSharedPreferences("euc_soh_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "WheelRepository"
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val PREF_ROOT_PATH = "scan_root_path"
        
        /**
         * Common storage locations to try.
         */
        private val STORAGE_LOCATIONS = listOf(
            "Download",
            "Downloads", 
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
     * Sets the root path for scanning.
     */
    fun setRootPath(path: File) {
        Log.d(TAG, "Setting root path: ${path.absolutePath}")
        prefs.edit().putString(PREF_ROOT_PATH, path.absolutePath).apply()
    }
    
    /**
     * Gets all detected wheels, using cache if available and fresh.
     * 
     * @param forceRefresh If true, ignores cache and scans from scratch
     * @param customPath Optional custom path to scan (overrides configured path)
     * @return Map of MAC address -> WheelIdentity
     */
    suspend fun getWheels(
        forceRefresh: Boolean = false,
        customPath: File? = null
    ): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        val scanPath = customPath ?: getRootPath()
        Log.d(TAG, "getWheels called: forceRefresh=$forceRefresh, scanPath=${scanPath.absolutePath}")
        
        if (forceRefresh) {
            Log.d(TAG, "Force refresh requested, scanning...")
            // Force full scan
            scanAndCache(scanPath)
        } else {
            // Try cache first
            val cached = wheelDao.getAllWheels()
            Log.d(TAG, "Cache contains ${cached.size} wheels")
            
            if (cached.isNotEmpty() && !isCacheExpired(cached)) {
                Log.d(TAG, "Using cached data")
                cached.toWheelIdentities()
            } else {
                Log.d(TAG, "Cache miss or expired, scanning...")
                // Cache miss or expired → scan
                scanAndCache(scanPath)
            }
        }
    }
    
    /**
     * Scans from specified root path and updates cache.
     */
    private suspend fun scanAndCache(rootPath: File): Map<String, WheelIdentity> {
        Log.d(TAG, "Creating scanner for path: ${rootPath.absolutePath}")
        
        if (!rootPath.exists()) {
            Log.e(TAG, "Root path does not exist!")
            return emptyMap()
        }
        
        if (!rootPath.isDirectory) {
            Log.e(TAG, "Root path is not a directory!")
            return emptyMap()
        }
        
        val scanner = WheelScanner(context, rootPath)
        Log.d(TAG, "Starting scan...")
        val wheels = scanner.scanAllWheels()
        Log.d(TAG, "Scan returned ${wheels.size} wheels")
        
        // Update cache
        Log.d(TAG, "Updating cache...")
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
