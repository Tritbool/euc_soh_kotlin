// SourceDetector.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogSource

/**
 * Détecte la source d'un fichier CSV EUC (EUC World ou WheelLog).
 * Port de detect_source() depuis soh_core_en.py.
 */
object SourceDetector {

    /**
     * Détecte la source d'un CSV à partir de ses colonnes.
     *
     * Règles (même logique que Python) :
     * - "euc_world" si colonnes contiennent "datetime" ET "distance_total"
     * - "wheellog" si colonnes contiennent "date" ET "time" ET "totaldistance"
     * - "euc_world" si colonne "datetime" présente seule
     * - "wheellog" sinon
     *
     * @param columns Ensemble des noms de colonnes du CSV (insensible à la casse)
     * @return LogSource.EUC_WORLD ou LogSource.WHEELLOG
     */
    fun detectSource(columns: Set<String>): LogSource {
        val cols = columns.map { it.trim().lowercase() }.toSet()
        return when {
            "datetime" in cols && "distance_total" in cols -> LogSource.EUC_WORLD
            "date" in cols && "time" in cols && "totaldistance" in cols -> LogSource.WHEELLOG
            "datetime" in cols -> LogSource.EUC_WORLD
            else -> LogSource.WHEELLOG
        }
    }

    /**
     * Détecte la source depuis une liste ordonnée de colonnes.
     */
    fun detectSource(columns: List<String>): LogSource = detectSource(columns.toSet())

    /**
     * Retourne la chaîne Python-compatible ("euc_world" / "wheellog").
     */
    fun detectSourceString(columns: Set<String>): String = when (detectSource(columns)) {
        LogSource.EUC_WORLD -> "euc_world"
        LogSource.WHEELLOG -> "wheellog"
    }
}
