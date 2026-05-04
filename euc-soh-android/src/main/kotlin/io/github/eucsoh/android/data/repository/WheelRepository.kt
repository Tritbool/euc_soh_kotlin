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
import io.github.eucsoh.android.data.model.WheelDataSource
import org.json.JSONArray
import org.json.JSONException

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
        private const val PREF_ROOT_URIS = "scan_root_uris"
        private const val PREF_ROOT_URI = "scan_root_uri"
    }

    /**
     * Sets the SAF root URI for scanning.
     */
    fun setRootUri(uri: Uri) {
        Log.d(TAG, "Setting root URI: $uri")
        saveRootUris(listOf(uri))
    }

    /**
     * Adds a SAF root URI to the list of scan sources.
     */
    fun addRootUri(uri: Uri) {
        val current = getRootUris().map { it.toString() }.toMutableList()
        if (uri.toString() !in current) {
            current.add(uri.toString())
        }
        val updated = current.map { it.toUri() }
        Log.d(TAG, "Adding root URI: $uri (total=${updated.size})")
        saveRootUris(updated)
    }

    private fun saveRootUris(uris: List<Uri>) {
        val json = JSONArray()
        uris.forEach { json.put(it.toString()) }
        prefs.edit {
            putString(PREF_ROOT_URIS, json.toString())
            // Keep legacy key for backward compatibility.
            putString(PREF_ROOT_URI, uris.firstOrNull()?.toString())
        }
    }

    /**
     * Gets the configured root URI (if in DocumentFile mode).
     */
    fun getRootUri(): Uri? = getRootUris().firstOrNull()

    /**
     * Gets all configured root URIs (multi-source SAF mode).
     */
    fun getRootUris(): List<Uri> {
        val storedUris = prefs.getString(PREF_ROOT_URIS, null)
        if (!storedUris.isNullOrBlank()) {
            try {
                val array = JSONArray(storedUris)
                return buildList {
                    for (i in 0 until array.length()) {
                        val value = array.optString(i)
                        if (value.isNotBlank()) {
                            add(value.toUri())
                        }
                    }
                }
            } catch (e: JSONException) {
                Log.w(TAG, "Failed to parse persisted root URIs, falling back to legacy key", e)
            }
        }

        // Legacy single-root fallback.
        val legacy = prefs.getString(PREF_ROOT_URI, null) ?: return emptyList()
        return listOf(legacy.toUri())
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
        val rootUris = getRootUris()
        if (rootUris.isEmpty()) {
            Log.d(TAG, "No SAF root URI configured, returning empty map")
            wheelDao.clearAll()
            return@withContext emptyMap()
        }

        if (forceRefresh) {
            Log.d(TAG, "Force refresh requested, scanning...")
            scanAndCache(rootUris, darknessBotEnabled)
        } else {
            // Try cache first
            val cached = wheelDao.getAllWheels()
            Log.d(TAG, "Cache contains ${cached.size} wheels")

            if (cached.isNotEmpty() && !isCacheExpired(cached)) {
                Log.d(TAG, "Using cached data")
                cached.toWheelIdentities()
            } else {
                Log.d(TAG, "Cache miss or expired, scanning...")
                scanAndCache(rootUris, darknessBotEnabled)
            }
        }
    }


    /**
     * Scans using configured path/URI and updates cache.
     */
    private suspend fun scanAndCache(
        rootUris: List<Uri>,
        darknessBotEnabled: Boolean = false
    ): Map<String, WheelIdentity> {
        val wheels = mutableMapOf<String, WheelIdentity>()
        rootUris.forEach { rootUri ->
            Log.d(TAG, "Scanning from URI: $rootUri")
            val scanResult = scanner.scanFromUri(rootUri, darknessBotEnabled)
            mergeWheelMaps(wheels, scanResult)
        }

        Log.d(TAG, "Scan returned ${wheels.size} wheels")
        wheelDao.clearAll()
        wheelDao.insertWheels(wheels.toEntities())
        Log.d(TAG, "Cache updated")
        return wheels
    }

    private fun mergeWheelMaps(
        target: MutableMap<String, WheelIdentity>,
        source: Map<String, WheelIdentity>
    ) {
        source.forEach { (mac, newIdentity) ->
            target.merge(mac, newIdentity) { existing, new ->
                val mergedCsv = existing.csvFiles + new.csvFiles
                existing.copy(
                    displayName = if (shouldPreferDisplayName(existing, new)) {
                        new.displayName
                    } else {
                        existing.displayName
                    },
                    csvFiles = mergedCsv,
                    manufacturer = new.manufacturer ?: existing.manufacturer,
                    model = new.model ?: existing.model,
                    serialNumber = new.serialNumber ?: existing.serialNumber,
                    source = if (existing.source == new.source) {
                        existing.source
                    } else {
                        WheelDataSource.MIX
                    }
                )
            }
        }
    }

    private fun shouldPreferDisplayName(existing: WheelIdentity, incoming: WheelIdentity): Boolean {
        return existing.displayName == existing.macAddress &&
                incoming.displayName != incoming.macAddress
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
