// FileProvider.kt
package com.euc.soh.io

/**
 * Interface d'abstraction pour fournir une liste de chemins vers des fichiers CSV à analyser.
 *
 * L'implémentation réelle (ex: Android ContentProvider, list of URIs, filesystem scanner, cloud provider)
 * doit retourner les chemins absolus ou relatifs des CSV qui seront parsés côté Analyzer.
 */
interface FileProvider {
    /**
     * Retourne la liste des chemins vers les fichiers CSV à analyser.
     */
    fun getFiles(): List<String>
}
