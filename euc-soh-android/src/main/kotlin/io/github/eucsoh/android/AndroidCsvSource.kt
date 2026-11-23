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

package io.github.eucsoh.android

import android.content.Context
import androidx.core.net.toUri
import io.github.eucsoh.CsvSource

/**
 * Android implementation of CsvSource using ContentResolver.
 */
class AndroidCsvSource(private val context: Context) : CsvSource {

    override fun readCsv(path: String): String {
        val uri = path.toUri()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot open stream for $path")
        return bytes.decodeToString()
    }

    override fun listCsvFiles(folderPath: String): List<String> {
        // Android uses DocumentProvider API for folder listing
        // This is a placeholder - full implementation would use DocumentFile
        return emptyList()
    }
}
