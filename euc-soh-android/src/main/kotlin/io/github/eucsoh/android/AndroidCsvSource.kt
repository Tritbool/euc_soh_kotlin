package io.github.eucsoh.android

import android.content.Context
import android.net.Uri
import io.github.eucsoh.CsvSource
import java.io.InputStream

/**
 * Android implementation of CsvSource using ContentResolver.
 */
class AndroidCsvSource(private val context: Context) : CsvSource {

    override fun openCsvStream(path: String): InputStream {
        val uri = Uri.parse(path)
        return context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open stream for $path")
    }

    override fun listCsvFiles(folderPath: String): List<String> {
        // Android uses DocumentProvider API for folder listing
        // This is a placeholder - full implementation would use DocumentFile
        return emptyList()
    }
}
