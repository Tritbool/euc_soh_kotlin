// CsvSource.kt
package com.euc.soh.io

import java.io.InputStream

/**
 * Interface d'abstraction pour accéder aux fichiers CSV.
 * Implémentée différemment selon la plateforme (Android, Desktop, etc.).
 */
interface CsvSource {
    /**
     * Ouvre un flux d'entrée pour lire un fichier CSV.
     * @param path Chemin absolu ou URI du fichier CSV
     * @return InputStream du fichier CSV
     */
    fun openCsvStream(path: String): InputStream

    /**
     * Liste tous les fichiers CSV d'un dossier.
     * @param folderPath Chemin absolu ou URI du dossier
     * @return Liste des chemins vers les fichiers CSV
     */
    fun listCsvFiles(folderPath: String): List<String>
}
