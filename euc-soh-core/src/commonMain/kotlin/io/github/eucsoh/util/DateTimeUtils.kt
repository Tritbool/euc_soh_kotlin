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

        // Le scanner rapide ne reconnaît que les formes que ISO_PATTERN accepte et rend
        // la main au motif sur tout le reste : le résultat ne peut donc pas diverger du
        // motif, il est juste lu sans machine à expressions régulières. Les horodatages
        // de journaux sont l'opération la plus appelée de toute l'analyse — un par
        // échantillon — et le motif coûtait à lui seul un cinquième du temps de mesure.
        return parseIsoFast(s) ?: parseIsoWithPattern(s)
    }

    /**
     * Corps d'origine d'ISO_PATTERN, gardé comme référence et comme filet pour tout ce
     * que [parseIsoFast] ne reconnaît pas.
     */
    private fun parseIsoWithPattern(s: String): Double? {
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

    /**
     * Lit un horodatage ISO 8601 sans regex, pour les formes exactement couvertes par
     * ISO_PATTERN : `AAAA-MM-JJ[T ]HH:MM[:SS][.,D{1,9}][ \t]*(Z|±HH[:]MM)`.
     *
     * Renvoie null dès que la chaîne sort de cette forme — y compris sur un doute — pour
     * que le motif tranche. Les champs ne sont pas validés (mois 13 passe, comme avec le
     * motif) : c'est le comportement d'origine.
     *
     * Visible pour le test d'équivalence, qui vérifie qu'il couvre bien les formes
     * réelles des journaux — un scanner qui ne correspond jamais reste vert sans le
     * filet, puisqu'il rend simplement la main au motif.
     */
    internal fun parseIsoFast(s: String): Double? {
        if (s.length < 16) return null
        if (!isDigitsAt(s, 0, 4) || s[4] != '-') return null
        if (!isDigitsAt(s, 5, 2) || s[7] != '-') return null
        if (!isDigitsAt(s, 8, 2)) return null
        val dateSep = s[10]
        if (dateSep != 'T' && dateSep != ' ') return null
        if (!isDigitsAt(s, 11, 2) || s[13] != ':' || !isDigitsAt(s, 14, 2)) return null

        var p = 16
        var second = 0
        if (s.length >= p + 3 && s[p] == ':' && isDigitsAt(s, p + 1, 2)) {
            second = intAt(s, p + 1, 2)
            p += 3
        }

        var fraction = 0.0
        if (p < s.length && (s[p] == '.' || s[p] == ',')) {
            var q = p + 1
            while (q < s.length && q - p <= 9 && s[q] >= '0' && s[q] <= '9') q++
            val digits = q - (p + 1)
            if (digits in 1..9) {
                // Même calcul que la version regex, chiffre à chiffre près : la fraction
                // est complétée à neuf chiffres puis divisée par un milliard.
                fraction = s.substring(p + 1, q).padEnd(9, '0').toDouble() / 1_000_000_000.0
                p = q
            }
        }

        while (p < s.length && s[p] == ' ') p++

        var offsetSeconds = 0.0
        if (p < s.length) {
            val rest = s.substring(p)
            val offsetOk = when {
                rest == "Z" -> true
                rest.length == 6 && (rest[0] == '+' || rest[0] == '-') &&
                    isDigitsAt(rest, 1, 2) && rest[3] == ':' && isDigitsAt(rest, 4, 2) -> true
                rest.length == 5 && (rest[0] == '+' || rest[0] == '-') &&
                    isDigitsAt(rest, 1, 4) -> true
                else -> false
            }
            if (!offsetOk) return null
            offsetSeconds = parseOffset(rest)
        }

        val days = daysFromCivil(intAt(s, 0, 4), intAt(s, 5, 2), intAt(s, 8, 2))
        return days * 86400.0 +
            intAt(s, 11, 2) * 3600 + intAt(s, 14, 2) * 60 + second + fraction - offsetSeconds
    }

    private fun isDigitsAt(s: String, at: Int, count: Int): Boolean {
        if (at < 0 || at + count > s.length) return false
        for (k in at until at + count) {
            if (s[k] < '0' || s[k] > '9') return false
        }
        return true
    }

    private fun intAt(s: String, at: Int, count: Int): Int {
        var v = 0
        for (k in at until at + count) v = v * 10 + (s[k] - '0')
        return v
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
