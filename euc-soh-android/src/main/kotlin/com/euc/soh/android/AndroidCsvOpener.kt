// AndroidCsvOpener.kt
package com.euc.soh.android

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.euc.soh.io.CsvSource
import java.io.File
import java.io.InputStream

/**
 * Implémentation Android de CsvSource.
 * Supporte à la fois les URI content:// (Storage Access Framework)
 * et les chemins de fichiers classiques.
 */
class AndroidCsvOpener(private val context: Context) : CsvSource {

    /**
     * Ouvre un flux d'entrée pour un fichier CSV.
     * - Si path commence par "content://" → utilise ContentResolver
     * - Sinon → ouvre le fichier directement
     */
    override fun openCsvStream(path: String): InputStream {
        return if (path.startsWith("content://")) {
            val uri = Uri.parse(path)
            context.contentResolver.openInputStream(uri)
                ?: error("Cannot open content URI: $path")
        } else {
            File(path).inputStream()
        }
    }

    /**
     * Liste les fichiers CSV d'un dossier.
     * - Si folderPath est une URI "content://" → utilise DocumentFile
     * - Sinon → liste les fichiers .csv du répertoire
     */
    override fun listCsvFiles(folderPath: String): List<String> {
        return if (folderPath.startsWith("content://")) {
            val uri = Uri.parse(folderPath)
            val documentFile = DocumentFile.fromTreeUri(context, uri)
                ?: return emptyList()
            documentFile.listFiles()
                .filter { it.isFile && it.name?.endsWith(".csv", ignoreCase = true) == true }
                .mapNotNull { it.uri.toString() }
        } else {
            File(folderPath)
                .listFiles { f -> f.extension.equals("csv", ignoreCase = true) }
                ?.map { it.absolutePath }
                ?: emptyList()
        }
    }
}
