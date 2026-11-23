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
         */
        fun readCsv(text: String): LogFrame {
            val lines = splitCsvLines(text)
            if (lines.isEmpty()) return of()

            val header = lines.first()
            val data = lines.drop(1)
            val columns = LinkedHashMap<String, List<Any?>>()
            for ((colIdx, name) in header.withIndex()) {
                val cleanName = name.trim()
                if (cleanName.isEmpty()) continue
                columns[cleanName] = data.map { row ->
                    if (colIdx < row.size) inferValue(row[colIdx]) else null
                }
            }
            return LogFrame(columns)
        }

        private fun splitCsvLines(text: String): List<List<String>> {
            val rows = mutableListOf<List<String>>()
            var current = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            var i = 0

            fun endCell() {
                current.add(sb.toString())
                sb.clear()
            }

            fun endRow() {
                endCell()
                rows.add(current)
                current = mutableListOf()
            }

            while (i < text.length) {
                val ch = text[i]
                when {
                    inQuotes -> {
                        if (ch == '"') {
                            if (i + 1 < text.length && text[i + 1] == '"') {
                                sb.append('"')
                                i++
                            } else {
                                inQuotes = false
                            }
                        } else {
                            sb.append(ch)
                        }
                    }
                    ch == '"' -> inQuotes = true
                    ch == ',' -> endCell()
                    ch == '\n' -> endRow()
                    ch == '\r' -> {}
                    else -> sb.append(ch)
                }
                i++
            }
            if (sb.isNotEmpty() || current.isNotEmpty()) endRow()

            return rows.filter { row -> row.any { it.isNotBlank() } }
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
