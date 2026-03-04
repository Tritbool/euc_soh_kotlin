package io.github.eucsoh.android.data.database

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.eucsoh.android.data.model.WheelDataSource
import io.github.eucsoh.android.data.model.WheelIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Room entity for persisting detected wheels.
 */
@Entity(tableName = "detected_wheels")
data class WheelEntity(
    @PrimaryKey val macAddress: String,
    val displayName: String,
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
        .map { Uri.parse(it) }
    
    return WheelIdentity(
        macAddress = macAddress,
        displayName = displayName,
        manufacturer = manufacturer,
        model = model,
        serialNumber = serialNumber,
        csvFiles = uris,
        source = WheelDataSource.valueOf(source)
    )
}

fun Map<String, WheelIdentity>.toEntities(timestamp: Long = System.currentTimeMillis()): List<WheelEntity> {
    return values.map { it.toEntity(timestamp) }
}

fun List<WheelEntity>.toWheelIdentities(): Map<String, WheelIdentity> {
    return associate { it.macAddress to it.toWheelIdentity() }
}
