package io.github.tritbool.euc_soh.core

import java.io.InputStream

/**
 * Platform-agnostic abstraction for CSV file access.
 * Implementations handle Android ContentResolver, desktop File I/O, etc.
 */
interface CsvSource {
    /**
     * Opens an InputStream for a CSV file at the given path.
     * @param path File path or URI (e.g., "content://..." on Android, "/path/to/file.csv" on desktop)
     * @return InputStream to read CSV content
     */
    fun openCsvStream(path: String): InputStream

    /**
     * Lists all CSV files in a folder.
     * @param folderPath Folder path or URI
     * @return List of file paths/URIs
     */
    fun listCsvFiles(folderPath: String): List<String>
}
