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
     */
    suspend fun listCsvFiles(wheelDirUri: Uri): List<CsvFileInfo> = withContext(Dispatchers.IO) {
        val wheelDir = DocumentFile.fromTreeUri(context, wheelDirUri)
            ?: return@withContext emptyList()

        wheelDir.listFiles()
            .filter { it.isFile && it.name?.endsWith(".csv", ignoreCase = true) == true }
            .map { docFile ->
                CsvFileInfo(
                    name = docFile.name ?: "unknown.csv",
                    uri = docFile.uri.toString(),
                    sizeBytes = docFile.length(),
                    lastModified = docFile.lastModified()
                )
            }
            .sortedByDescending { it.lastModified }
    }

    /**
     * Read first N lines of CSV for preview.
     */
    suspend fun previewCsv(fileUri: Uri, maxLines: Int = 20): List<String> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { inputStream ->
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
            context.contentResolver.openInputStream(Uri.parse(fileUri))?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                    lines.count()
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
