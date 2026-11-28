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

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Multiplatform fixed-decimal formatting (replacement for "%.Nf".format).
 */
fun Double.toFixed(decimals: Int): String {
    if (isNaN()) return "NaN"
    if (isInfinite()) return if (this > 0) "Infinity" else "-Infinity"

    require(decimals in 0..9) { "Unsupported decimals: $decimals" }
    val factor = 10.0.pow(decimals)
    val negative = this < 0
    val scaledAbs = round(abs(this) * factor).toLong()
    val intPart = scaledAbs / factor.toLong()
    var fracPart = scaledAbs % factor.toLong()

    val intStr = intPart.toString()
    if (decimals == 0) return if (negative) "-$intStr" else intStr
    val fracStr = fracPart.toString().padStart(decimals, '0')
    val sign = if (negative && (intPart != 0L || fracPart != 0L)) "-" else if (negative) "-" else ""
    return "$sign$intStr.$fracStr"
}
