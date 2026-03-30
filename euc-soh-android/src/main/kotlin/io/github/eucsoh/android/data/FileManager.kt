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

package io.github.eucsoh.android.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.eucsoh.android.data.model.CsvFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * File management for wheel CSV files.
 * 
 * Features:
 * - List CSV files for a wheel
 * - Preview CSV content
 * - Mark files as excluded from analysis
 * - Share files with external apps
 */
class FileManager(private val context: Context) {

    /**
     * List all CSV files in the wheel directory.
     * Returns lightweight CsvFileInfo without full validation for performance.
     */
    suspend fun listCsvFiles(wheelDirUri: Uri): List<CsvFileInfo> = withContext(Dispatchers.IO) {
        val wheelDir = DocumentFile.fromTreeUri(context, wheelDirUri)
            ?: return@withContext emptyList()

        wheelDir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".csv", ignoreCase = true) == true }
            .map { docFile ->
                CsvFileInfo(
                    uri = docFile.uri,
                    fileName = docFile.name ?: "unknown.csv",
                    sizeBytes = docFile.length(),
                    isValid = true, // Assume valid for listing, full validation happens during analysis
                    validationMessage = "",
                    nPoints = null,
                    hasTemperature = false,
                    reqMedian = null,
                    wheelKm = null,
                    isExcluded = false
                )
            }
            .sortedByDescending { it.sizeBytes }
    }

    /**
     * Read first N lines of CSV for preview.
     */
    suspend fun previewCsv(fileUri: Uri, maxLines: Int = 20): List<String> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.take(maxLines).toList()
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Count lines in CSV file.
     */
    suspend fun countLines(fileUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.count()
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
