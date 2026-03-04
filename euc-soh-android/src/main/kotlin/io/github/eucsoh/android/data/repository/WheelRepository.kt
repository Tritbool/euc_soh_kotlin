package io.github.eucsoh.android.data.repository

import android.content.Context
import io.github.eucsoh.android.data.database.WheelDatabase
import io.github.eucsoh.android.data.database.toEntities
import io.github.eucsoh.android.data.database.toWheelIdentities
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.scanner.WheelScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for wheel detection with caching.
 * 
 * Strategy:
 * - First load: scan all sources + persist to Room
 * - Subsequent loads: use cache unless force refresh
 * - Cache expires after 24h
 */
class WheelRepository(context: Context) {
    
    private val scanner = WheelScanner(context)
    private val wheelDao = WheelDatabase.getInstance(context).wheelDao()
    
    companion object {
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L  // 24 hours
    }
    
    /**
     * Gets all detected wheels, using cache if available and fresh.
     * 
     * @param forceRefresh If true, ignores cache and scans from scratch
     * @return Map of MAC address -> WheelIdentity
     */
    suspend fun getWheels(forceRefresh: Boolean = false): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        if (forceRefresh) {
            // Force full scan
            scanAndCache()
        } else {
            // Try cache first
            val cached = wheelDao.getAllWheels()
            
            if (cached.isNotEmpty() && !isCacheExpired(cached)) {
                cached.toWheelIdentities()
            } else {
                // Cache miss or expired → scan
                scanAndCache()
            }
        }
    }
    
    /**
     * Scans all sources and updates cache.
     */
    private suspend fun scanAndCache(): Map<String, WheelIdentity> {
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
