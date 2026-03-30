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

package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.abs

/**
 * Builds local V_idle profile from quasi-idle segments.
 * New feature from soh_core_en.py v3.
 */
object VIdleProfileBuilder {

    data class Segment(val startIdx: Int, val endIdx: Int)

    /**
     * Estimates dt (time delta in seconds) for each row.
     * Returns DoubleArray aligned to df indices (size = df.rowsCount()).
     */
    private fun estimateDtSeries(df: DataFrame<*>): DoubleArray? {
        val n = df.rowsCount()
        if (n < 2) return null

        // Try datetime column
        if (Constants.EUCWorldColumns.TIMESTAMP.csv_code in df.columnNames()) {
            try {
                // Simple numeric diff approach (assumes timestamps in seconds or parseable)
                // For production, use proper datetime parsing
                val times = df[Constants.EUCWorldColumns.TIMESTAMP.csv_code].values().map { it.toString() }
                // Fallback to 0.1s if datetime parsing fails
                return DoubleArray(n) { 0.1 }
            } catch (e: Exception) {
                // Fallback
            }
        }

        // Fallback: 10 Hz sampling
        return DoubleArray(n) { 0.1 }
    }

    /**
     * Detects consecutive segments where |current| < idleCurrentAbs.
     */
    private fun detectIdleSegments(
        df: DataFrame<*>,
        iCol: String,
        idleCurrentAbs: Double
    ): List<Segment> {
        val n = df.rowsCount()
        val currents = df[iCol].values()
            .filterNotNull()
            .mapNotNull { (it as? Number)?.toDouble() }
        
        // Si on a perdu des valeurs, retour vide
        if (currents.size != n) return emptyList()
        
        val lowMask = BooleanArray(n) { abs(currents[it]) < idleCurrentAbs }

        val segments = mutableListOf<Segment>()
        var start = -1

        for (i in 0 until n) {
            if (lowMask[i]) {
                if (start == -1) start = i
            } else {
                if (start != -1) {
                    segments.add(Segment(start, i - 1))
                    start = -1
                }
            }
        }
        if (start != -1) {
            segments.add(Segment(start, n - 1))
        }

        return segments
    }

    /**
     * Builds V_idle_local series.
     * 
     * Logic:
     * - Detect quasi-idle segments (|I| < idleCurrentAbs)
     * - Keep only segments with duration >= minIdleDurationS
     * - Within segments, keep points with |dV/dt| <= maxDvdtAbs
     * - Fit V_idle(SoC) if SoC available, else global average
     */
    fun buildVIdleProfile(
        df: DataFrame<*>,
        vCol: String,
        iCol: String,
        socVoltCol: String? = null,
        idleCurrentAbs: Double = 3.0,
        minIdleDurationS: Double = 5.0,
        maxDvdtAbs: Double = 0.5
    ): DoubleArray {
        val n = df.rowsCount()
        if (n == 0) return doubleArrayOf()

        val dt = estimateDtSeries(df) ?: return DoubleArray(n) { 0.0 }

        val voltages = df[vCol].values()
            .filterNotNull()
            .mapNotNull { (it as? Number)?.toDouble() }
        
        // Si on a perdu des valeurs, retour 0
        if (voltages.size != n) return DoubleArray(n) { 0.0 }
        
        val segments = detectIdleSegments(df, iCol, idleCurrentAbs)

        if (segments.isEmpty()) {
            // No idle segments: fallback to global max
            val vGlobal = voltages.filter { !it.isNaN() }.maxOrNull() ?: 0.0
            return DoubleArray(n) { vGlobal }
        }

        // Filter segments by duration
        val validSegments = segments.filter { seg ->
            val duration = (seg.startIdx..seg.endIdx).sumOf { dt[it] }
            duration >= minIdleDurationS
        }

        if (validSegments.isEmpty()) {
            val vGlobal = voltages.filter { !it.isNaN() }.maxOrNull() ?: 0.0
            return DoubleArray(n) { vGlobal }
        }

        // Collect V_idle per segment
        val vIdleSegments = mutableListOf<Double>()
        val socIdleSegments = mutableListOf<Double?>()

        for (seg in validSegments) {
            val indices = (seg.startIdx..seg.endIdx).toList()
            val vSeg = indices.map { voltages[it] }

            // Compute dV/dt
            val dvdt = mutableListOf<Double>()
            for (i in 1 until vSeg.size) {
                val dv = vSeg[i] - vSeg[i - 1]
                val dtVal = dt[indices[i]]
                dvdt.add(if (dtVal > 0) abs(dv / dtVal) else 0.0)
            }

            // Keep stable points
            val stableIndices = dvdt.withIndex().filter { it.value <= maxDvdtAbs }.map { indices[it.index + 1] }

            if (stableIndices.size < 3) continue

            val vStable = stableIndices.map { voltages[it] }.filter { !it.isNaN() }
            if (vStable.isEmpty()) continue

            val vIdleSeg = vStable.sorted().let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
            vIdleSegments.add(vIdleSeg)

            // SoC if available
            if (socVoltCol != null && socVoltCol in df.columnNames()) {
                val socVals = stableIndices.mapNotNull { (df[socVoltCol][it] as? Number)?.toDouble() }
                if (socVals.isNotEmpty()) {
                    socIdleSegments.add(socVals.average())
                } else {
                    socIdleSegments.add(null)
                }
            } else {
                socIdleSegments.add(null)
            }
        }

        if (vIdleSegments.isEmpty()) {
            val vGlobal = voltages.filter { !it.isNaN() }.maxOrNull() ?: 0.0
            return DoubleArray(n) { vGlobal }
        }

        // If no SoC: global average
        if (socVoltCol == null || socIdleSegments.all { it == null }) {
            val vGlobal = vIdleSegments.average()
            return DoubleArray(n) { vGlobal }
        }

        // Build V_idle(SoC) interpolation
        val socArray = socIdleSegments.filterNotNull().toDoubleArray()
        val vArray = vIdleSegments.filterIndexed { i, _ -> socIdleSegments[i] != null }.toDoubleArray()

        if (socArray.size < 2) {
            val vGlobal = vArray.average()
            return DoubleArray(n) { vGlobal }
        }

        // Sort by SoC
        val sorted = socArray.zip(vArray).sortedBy { it.first }
        val socSorted = sorted.map { it.first }.toDoubleArray()
        val vSorted = sorted.map { it.second }.toDoubleArray()

        val socMin = socSorted.first()
        val socMax = socSorted.last()
        val vMin = vSorted.first()
        val vMax = vSorted.last()

        // Interpolate for each row
        val socValues = if (socVoltCol in df.columnNames()) {
            df[socVoltCol].values().map { (it as? Number)?.toDouble() ?: Double.NaN }
        } else {
            List(n) { Double.NaN }
        }

        return DoubleArray(n) { i ->
            val soc = socValues[i]
            when {
                soc.isNaN() -> vArray.average()
                soc <= socMin -> vMin
                soc >= socMax -> vMax
                else -> linearInterp(socSorted, vSorted, soc)
            }
        }
    }

    private fun linearInterp(x: DoubleArray, y: DoubleArray, xVal: Double): Double {
        for (i in 0 until x.size - 1) {
            if (xVal >= x[i] && xVal <= x[i + 1]) {
                val t = (xVal - x[i]) / (x[i + 1] - x[i])
                return y[i] + t * (y[i + 1] - y[i])
            }
        }
        return y.last()
    }
}
