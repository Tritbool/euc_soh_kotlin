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

package io.github.eucsoh.android.visualization

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class ArchiveFileEntry(
    val name: String,
    val sha256: String
)

@Serializable
data class ArchiveManifest(
    val app: String = "euc_soh",
    val version: Int = 1,
    val appVersionCode: Int,
    val wheelMac: String,
    val files: List<ArchiveFileEntry>,
    val hmac: String = ""
) {
    /**
     * Computes HMAC-SHA256 over the canonical JSON representation of this manifest.
     *
     * Canonical JSON: keys sorted alphabetically (app, appVersionCode, files, version, wheelMac),
     * no whitespace, hmac field ABSENT from the input.
     * Each file entry also has keys sorted: name, sha256.
     */
    fun computeHmac(secret: String): String {
        val canonicalJson = buildCanonicalJson()
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(keySpec)
        val hmacBytes = mac.doFinal(canonicalJson.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies that [hmac] matches the recomputed HMAC for this manifest.
     */
    fun verify(secret: String): Boolean {
        return hmac == computeHmac(secret)
    }

    /**
     * Builds the canonical JSON string with alphabetically sorted keys, no whitespace,
     * and the hmac field excluded.
     */
    private fun buildCanonicalJson(): String {
        val filesArray = buildJsonArray {
            for (entry in files) {
                add(buildJsonObject {
                    put("name", JsonPrimitive(entry.name))
                    put("sha256", JsonPrimitive(entry.sha256))
                })
            }
        }
        val obj = buildJsonObject {
            put("app", JsonPrimitive(app))
            put("appVersionCode", JsonPrimitive(appVersionCode))
            put("files", filesArray)
            put("version", JsonPrimitive(version))
            put("wheelMac", JsonPrimitive(wheelMac))
        }
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    /**
     * Serializes the full manifest (including hmac) to JSON for writing to the archive.
     */
    fun toJson(): String {
        return Json.encodeToString(serializer(), this)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): ArchiveManifest {
            return json.decodeFromString(serializer(), jsonString)
        }
    }
}
