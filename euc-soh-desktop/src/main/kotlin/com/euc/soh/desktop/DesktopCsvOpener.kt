// DesktopCsvOpener.kt
package com.euc.soh.desktop

import com.euc.soh.io.CsvSource
import java.io.File
import java.io.InputStream

/**
 * Implémentation Desktop (JVM) de CsvSource.
 * Accède directement au système de fichiers.
 */
class DesktopCsvOpener : CsvSource {

    /**
     * Ouvre un flux d'entrée pour un fichier CSV identifié par son chemin absolu.
     */
    override fun openCsvStream(path: String): InputStream {
        return File(path).inputStream()
    }

    /**
     * Liste tous les fichiers CSV (.csv, insensible à la casse) d'un dossier.
     */
    override fun listCsvFiles(folderPath: String): List<String> {
        return File(folderPath)
            .listFiles { f -> f.extension.equals("csv", ignoreCase = true) }
            ?.map { it.absolutePath }
            ?: emptyList()
    }
}
