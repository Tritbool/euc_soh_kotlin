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

package io.github.eucsoh

/**
 * Platform-agnostic CSV file access abstraction.
 * Implemented by Android (ContentResolver) and Desktop (File).
 */
interface CsvSource {
    /** Returns the full UTF-8 CSV content of the file at [path]. */
    fun readCsv(path: String): String

    fun listCsvFiles(folderPath: String): List<String>
}

/**
 * Platform default CSV reading when no [CsvSource] is supplied.
 * JVM reads from the filesystem; Android requires an explicit [CsvSource].
 */
internal expect fun readCsvFromPath(path: String): String?
