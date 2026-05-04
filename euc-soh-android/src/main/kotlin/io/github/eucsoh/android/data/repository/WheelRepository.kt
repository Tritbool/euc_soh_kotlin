/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.android.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.eucsoh.android.data.database.WheelDatabase
import io.github.eucsoh.android.data.database.toEntities
import io.github.eucsoh.android.data.database.toEntity
import io.github.eucsoh.android.data.database.toWheelIdentities
import io.github.eucsoh.android.data.database.toWheelIdentity
import io.github.eucsoh.android.data.model.WheelIdentity
import io.github.eucsoh.android.data.scanner.WheelScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import androidx.core.net.toUri

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
        private const val PREF_ROOT_URI = "scan_root_uri"
    }

    /**
     * Sets the SAF root URI for scanning.
     */
    fun setRootUri(uri: Uri) {
        Log.d(TAG, "Setting root URI: $uri")
        prefs.edit {
            putString(PREF_ROOT_URI, uri.toString())
        }
    }

    /**
     * Gets the configured root URI (if in DocumentFile mode).
     */
    fun getRootUri(): Uri? {
        val storedUri = prefs.getString(PREF_ROOT_URI, null) ?: return null
        return storedUri.toUri()
    }

    /**
     * Gets all detected wheels, using cache if available and fresh.
     * 
     * @param forceRefresh If true, ignores cache and scans from scratch
     * @return Map of MAC address -> WheelIdentity
     */
    suspend fun getWheels(
        forceRefresh: Boolean = false,
        darknessBotEnabled: Boolean = false
    ): Map<String, WheelIdentity> = withContext(Dispatchers.IO) {
        Log.d(TAG, "getWheels called: forceRefresh=$forceRefresh darknessBotEnabled=$darknessBotEnabled")
        val rootUri = getRootUri()
        if (rootUri == null) {
            Log.d(TAG, "No SAF root URI configured, returning empty list")
            wheelDao.clearAll()
            return@withContext emptyMap()
        }

        if (forceRefresh) {
            Log.d(TAG, "Force refresh requested, scanning...")
            scanAndCache(rootUri, darknessBotEnabled)
        } else {
            // Try cache first
            val cached = wheelDao.getAllWheels()
            Log.d(TAG, "Cache contains ${cached.size} wheels")

            if (cached.isNotEmpty() && !isCacheExpired(cached)) {
                Log.d(TAG, "Using cached data")
                cached.toWheelIdentities()
            } else {
                Log.d(TAG, "Cache miss or expired, scanning...")
                scanAndCache(rootUri, darknessBotEnabled)
            }
        }
    }


    /**
     * Scans using configured path/URI and updates cache.
     */
    private suspend fun scanAndCache(
        rootUri: Uri,
        darknessBotEnabled: Boolean = false
    ): Map<String, WheelIdentity> {
        Log.d(TAG, "Scanning from URI: $rootUri")
        val wheels: Map<String, WheelIdentity> = scanner.scanFromUri(rootUri, darknessBotEnabled)

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
    @Suppress("unused so far")
    suspend fun getWheelByMac(mac: String): WheelIdentity? = withContext(Dispatchers.IO) {
        wheelDao.getWheelByMac(mac)?.toWheelIdentity()
    }

    /**
     * Clears all cached data.
     */
    @Suppress("unused so far")
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Clearing cache")
        wheelDao.clearAll()
    }

    suspend fun saveWheel(wheel: WheelIdentity) = withContext(Dispatchers.IO) {
        val existing = wheelDao.getWheelByMac(wheel.macAddress)
        val timestamp = existing?.lastScanTimestamp ?: System.currentTimeMillis()
        wheelDao.insertWheels(listOf(wheel.toEntity(timestamp)))
        Log.d(TAG, "Saved wheel ${wheel.macAddress} (alias=${wheel.userAlias})")
    }
}
