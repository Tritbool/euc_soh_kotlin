package io.github.eucsoh

import java.io.InputStream

/**
 * Platform-agnostic CSV file access abstraction.
 * Implemented by Android (ContentResolver) and Desktop (File).
 */
interface CsvSource {
    fun openCsvStream(path: String): InputStream
    fun listCsvFiles(folderPath: String): List<String>
}
