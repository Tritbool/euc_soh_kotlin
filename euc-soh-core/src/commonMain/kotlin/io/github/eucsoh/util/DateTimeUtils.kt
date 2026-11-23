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

package io.github.eucsoh.util

/**
 * Multiplatform timestamp parsing without java.time.
 * All timestamps are converted to epoch seconds (UTC) as [Double].
 */
object DateTimeUtils {

    private val ISO_PATTERN = Regex(
        """^(\d{4})-(\d{2})-(\d{2})([T ])(\d{2}):(\d{2})(?::(\d{2}))?(?:[.,](\d{1,9}))?\s*(Z|[+-]\d{2}:?\d{2})?$"""
    )

    private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    /**
     * Parses common log timestamp formats to epoch seconds:
     * - ISO 8601 (with T or space, optional seconds/fractions, optional
     *   Z or ±HH:MM / ±HHMM offset — EUC World emits both variants)
     * - Raw epoch seconds or milliseconds
     *
     * Local (offset-less) timestamps are interpreted as UTC.
     *
     * @return epoch seconds, or null when the string cannot be parsed.
     */
    fun parseToEpochSeconds(raw: String): Double? {
        val s = raw.trim()
        if (s.isEmpty()) return null

        s.toLongOrNull()?.let { digits ->
            return if (s.length >= 13) digits / 1000.0 else digits.toDouble()
        }

        val m = ISO_PATTERN.matchEntire(s) ?: return null
        val (year, month, day) = Triple(
            m.groupValues[1].toInt(),
            m.groupValues[2].toInt(),
            m.groupValues[3].toInt()
        )
        val hour = m.groupValues[5].toInt()
        val minute = m.groupValues[6].toInt()
        val second = m.groupValues[7]?.toIntOrNull() ?: 0
        val fraction = m.groupValues[8]?.let {
            it.padEnd(9, '0').take(9).toDouble() / 1_000_000_000.0
        } ?: 0.0
        val offsetSeconds = parseOffset(m.groupValues[9])

        val days = daysFromCivil(year, month, day)
        return days * 86400.0 + hour * 3600 + minute * 60 + second + fraction - offsetSeconds
    }

    private fun parseOffset(offset: String?): Double {
        if (offset == null || offset.isEmpty() || offset == "Z" || offset == "z") return 0.0
        val sign = if (offset[0] == '-') -1 else 1
        val digits = offset.substring(1).replace(":", "")
        val hours = digits.take(2).toInt()
        val minutes = if (digits.length >= 4) digits.substring(2, 4).toInt() else 0
        return sign * (hours * 3600 + minutes * 60).toDouble()
    }

    /**
     * Days from 1970-01-01 (Howard Hinnant's days_from_civil algorithm).
     */
    fun daysFromCivil(year: Int, month: Int, day: Int): Int {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

    fun daysInMonth(year: Int, month: Int): Int =
        if (month == 2 && isLeapYear(year)) 29 else DAYS_IN_MONTH[month - 1]

}
