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

package io.github.eucsoh.android.data.database

import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.eucsoh.android.data.model.WheelDataSource
import io.github.eucsoh.android.data.model.WheelIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room entity for persisting detected wheels.
 */
@Entity(tableName = "detected_wheels")
data class WheelEntity(
    @PrimaryKey val macAddress: String,
    val displayName: String,
    val userAlias: String? = null,
    val manufacturer: String?,
    val model: String?,
    val serialNumber: String?,
    val csvFileUris: String,  // JSON array of URIs
    val source: String,
    val lastScanTimestamp: Long
)

/**
 * Conversion extensions between WheelIdentity and WheelEntity.
 */
fun WheelIdentity.toEntity(timestamp: Long = System.currentTimeMillis()): WheelEntity {
    val urisJson = Json.encodeToString(csvFiles.map { it.toString() })
    
    return WheelEntity(
        macAddress = macAddress,
        displayName = displayName,
        userAlias = userAlias,
        manufacturer = manufacturer,
        model = model,
        serialNumber = serialNumber,
        csvFileUris = urisJson,
        source = source.name,
        lastScanTimestamp = timestamp
    )
}

fun WheelEntity.toWheelIdentity(): WheelIdentity {
    val uris = Json.decodeFromString<List<String>>(csvFileUris)
        .map { it.toUri() }
    
    return WheelIdentity(
        macAddress = macAddress,
        displayName = displayName,
        userAlias = userAlias,
        manufacturer = manufacturer,
        model = model,
        serialNumber = serialNumber,
        csvFiles =  uris.toSet(),
        source = WheelDataSource.valueOf(source)
    )
}

fun Map<String, WheelIdentity>.toEntities(timestamp: Long = System.currentTimeMillis()): List<WheelEntity> {
    return values.map { it.toEntity(timestamp) }
}

fun List<WheelEntity>.toWheelIdentities(): Map<String, WheelIdentity> {
    return associate { it.macAddress to it.toWheelIdentity() }
}
