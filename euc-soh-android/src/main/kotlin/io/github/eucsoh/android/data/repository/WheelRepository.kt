package io.github.eucsoh.android.data.repository

import android.content.Context
import android.os.Environment
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
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val PREF_ROOT_PATH = "scan_root_path"
    }
    
    /**
     * Gets the configured root path for scanning.
     * Defaults to external storage root if not configured.
     */
    fun getRootPath(): File {
        val storedPath = prefs.getString(PREF_ROOT_PATH, null)
        return if (storedPath != null) {
            File(storedPath)
        } else {
            Environment.getExternalStorageDirectory()
        }
    }
    
    /**
     * Sets the root path for scanning.
     */
    fun setRootPath(path: File) {
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
        
        if (forceRefresh) {
            // Force full scan
            scanAndCache(scanPath)
        } else {
            // Try cache first
            val cached = wheelDao.getAllWheels()
            
            if (cached.isNotEmpty() && !isCacheExpired(cached)) {
                cached.toWheelIdentities()
            } else {
                // Cache miss or expired → scan
                scanAndCache(scanPath)
            }
        }
    }
    
    /**
     * Scans from specified root path and updates cache.
     */
    private suspend fun scanAndCache(rootPath: File): Map<String, WheelIdentity> {
        val scanner = WheelScanner(context, rootPath)
        val wheels = scanner.scanAllWheels()
        
        // Update cache
        wheelDao.clearAll()
        wheelDao.insertWheels(wheels.toEntities())
        
        return wheels
    }
    
    /**
     * Checks if cached data is older than MAX_AGE.
     */
    private fun isCacheExpired(cached: List<io.github.eucsoh.android.data.database.WheelEntity>): Boolean {
        if (cached.isEmpty()) return true
        
        val now = System.currentTimeMillis()
        val oldestTimestamp = cached.minOfOrNull { it.lastScanTimestamp } ?: return true
        
        return (now - oldestTimestamp) > CACHE_MAX_AGE_MS
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
        wheelDao.clearAll()
    }
}
