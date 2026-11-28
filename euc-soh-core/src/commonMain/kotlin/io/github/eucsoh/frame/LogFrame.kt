/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.eucsoh.frame

/**
 * Minimal multiplatform column-oriented data frame used by the analysis pipeline.
 * Replaces kotlinx DataFrame (JVM-only) with a Kotlin Multiplatform equivalent.
 *
 * Values are typed as [Any?]: numbers are stored as [Double] or [Long],
 * booleans as [Boolean], everything else as [String].
 */
class LogFrame internal constructor(
    private val columns: LinkedHashMap<String, List<Any?>>
) {

    class LogRow internal constructor(
        private val frame: LogFrame,
        val index: Int
    ) {
        operator fun get(column: String): Any? = frame.column(column)[index]
    }

    fun rowsCount(): Int = columns.values.maxOfOrNull { it.size } ?: 0

    fun columnNames(): List<String> = columns.keys.toList()

    fun has(column: String): Boolean = column in columns

    operator fun get(column: String): List<Any?> = column(column)

    fun column(column: String): List<Any?> =
        columns[column] ?: throw IllegalArgumentException("Unknown column: $column")

    fun row(index: Int): LogRow = LogRow(this, index)

    fun rows(): List<LogRow> = (0 until rowsCount()).map { LogRow(this, it) }

    fun filter(predicate: (LogRow) -> Boolean): LogFrame {
        val kept = (0 until rowsCount()).filter { predicate(LogRow(this, it)) }
        val newColumns = LinkedHashMap<String, List<Any?>>()
        for ((name, values) in columns) {
            newColumns[name] = kept.map { values[it] }
        }
        return LogFrame(newColumns)
    }

    fun add(column: String, values: List<Any?>): LogFrame {
        require(values.size == rowsCount() || rowsCount() == 0) {
            "Column size mismatch: ${values.size} vs ${rowsCount()}"
        }
        val newColumns = LinkedHashMap(columns)
        newColumns[column] = values
        return LogFrame(newColumns)
    }

    fun addConstant(column: String, value: Any?): LogFrame {
        return add(column, List(rowsCount()) { value })
    }

    fun sortBy(column: String): LogFrame {
        val values = column(column)
        val order = values.indices.sortedWith(compareBy(nullsLast()) { values[it] as? Comparable<Any?> })
        val newColumns = LinkedHashMap<String, List<Any?>>()
        for ((name, col) in columns) {
            newColumns[name] = order.map { col[it] }
        }
        return LogFrame(newColumns)
    }

    fun take(n: Int): LogFrame {
        val newColumns = LinkedHashMap<String, List<Any?>>()
        for ((name, values) in columns) {
            newColumns[name] = values.take(n)
        }
        return LogFrame(newColumns)
    }

    companion object {
        fun of(vararg columns: Pair<String, List<Any?>>): LogFrame {
            val map = LinkedHashMap<String, List<Any?>>()
            for ((name, values) in columns) {
                map[name] = values.toList()
            }
            return LogFrame(map)
        }

        /**
         * Parses CSV text with a header row. Numeric-looking cells are stored
         * as [Double] (or [Long] for integer values), booleans as [Boolean],
         * empty cells as null, everything else as [String].
         *
         * Le fichier est parcouru une seule fois, ligne par ligne, et chaque cellule
         * est rangée directement dans sa colonne : rien ne retient la grille de cellules
         * intermédiaire. C'est ce qui garde l'empreinte mémoire proche de la taille des
         * seules colonnes conservées — un journal de 2 Mo ne doit pas coûter 50 Mo de tas,
         * surtout sur un téléphone.
         */
        fun readCsv(text: String): LogFrame {
            val cursor = CsvCursor(text)

            var header: List<String>? = null
            while (header == null) {
                val row = cursor.nextRow() ?: return of()
                if (row.any { it.isNotBlank() }) header = row
            }

            val names = ArrayList<String>(header.size)
            val slots = ArrayList<Int>(header.size)
            for ((colIdx, name) in header.withIndex()) {
                val cleanName = name.trim()
                if (cleanName.isEmpty()) continue
                names += cleanName
                slots += colIdx
            }
            if (names.isEmpty()) return of()

            // Prédimensionne : grandir une colonne par recopie coûte plus cher que
            // l'espace légèrement sur-reservé sur un fichier à lignes vides.
            val capacity = text.count { it == '\n' }.coerceAtLeast(16)
            val columns = ArrayList<MutableList<Any?>>(names.size)
            repeat(names.size) { columns += ArrayList<Any?>(capacity) }

            val row = ArrayList<String>(names.size)
            while (cursor.nextRowInto(row)) {
                if (!row.any { it.isNotBlank() }) continue
                for (k in names.indices) {
                    val slot = slots[k]
                    columns[k].add(if (slot < row.size) inferValue(row[slot]) else null)
                }
            }

            val built = LinkedHashMap<String, List<Any?>>(names.size)
            for (k in names.indices) built[names[k]] = columns[k]
            return LogFrame(built)
        }

        /**
         * Curseur CSV : découpe [text] ligne par ligne sans jamais retenir les lignes
         * déjà lues. La liste de sortie est réutilisée entre les appels pour éviter
         * une allocation par ligne.
         */
        private class CsvCursor(private val text: String) {
            private var i = 0
            private val cell = StringBuilder()

            fun nextRowInto(out: MutableList<String>): Boolean {
                out.clear()
                if (i >= text.length) return false

                var inQuotes = false
                while (i < text.length) {
                    val ch = text[i]
                    when {
                        inQuotes -> {
                            if (ch == '"') {
                                if (i + 1 < text.length && text[i + 1] == '"') {
                                    cell.append('"')
                                    i++
                                } else {
                                    inQuotes = false
                                }
                            } else {
                                cell.append(ch)
                            }
                        }

                        ch == '"' -> inQuotes = true
                        ch == ',' -> {
                            out.add(cell.toString())
                            cell.clear()
                        }

                        ch == '\n' -> {
                            i++
                            out.add(cell.toString())
                            cell.clear()
                            return true
                        }

                        ch == '\r' -> {}
                        else -> cell.append(ch)
                    }
                    i++
                }

                // Dernière ligne sans saut de ligne final ; sinon on est déjà sorti.
                if (cell.isEmpty() && out.isEmpty()) return false
                out.add(cell.toString())
                cell.clear()
                return true
            }

            fun nextRow(): List<String>? {
                val row = ArrayList<String>(8)
                return if (nextRowInto(row)) row else null
            }
        }


        private fun inferValue(raw: String): Any? {
            val s = raw.trim()
            if (s.isEmpty()) return null
            if (s.equals("true", ignoreCase = true)) return true
            if (s.equals("false", ignoreCase = false)) return false
            if (s.contains("e") || s.contains("E")) {
                return s.toDoubleOrNull()
            }
            if (s.contains(".")) {
                return s.toDoubleOrNull() ?: s
            }
            s.toLongOrNull()?.let { return it }
            return s
        }
    }
}
