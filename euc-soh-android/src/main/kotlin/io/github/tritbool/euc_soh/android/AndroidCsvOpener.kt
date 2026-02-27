package io.github.tritbool.euc_soh.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import io.github.tritbool.euc_soh.core.CsvSource
import java.io.File
import java.io.InputStream

/**
 * Android implementation of CsvSource.
 * Handles both content:// URIs (SAF) and file:// paths.
 */
class AndroidCsvOpener(private val context: Context) : CsvSource {

    override fun openCsvStream(path: String): InputStream {
        return when {
            path.startsWith("content://") -> {
                val uri = Uri.parse(path)
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open content URI: $path")
            }
            else -> {
                // Regular file path
                File(path).inputStream()
            }
        }
    }

    override fun listCsvFiles(folderPath: String): List<String> {
        return when {
            folderPath.startsWith("content://") -> {
                val uri = Uri.parse(folderPath)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                    ?: return emptyList()

                docFile.listFiles()
                    .filter { it.isFile && it.name?.endsWith(".csv", ignoreCase = true) == true }
                    .mapNotNull { it.uri.toString() }
            }
            else -> {
                // Regular directory
                File(folderPath).listFiles { file ->
                    file.extension.equals("csv", ignoreCase = true)
                }?.map { it.absolutePath } ?: emptyList()
            }
        }
    }
}
