package io.github.eucsoh.analysis

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.abs

/**
 * Builds V_idle_local(SoC) profile from quasi-idle plateaus.
 * Port of build_vidle_profile() from soh_core_en.py
 */
object VIdleProfileBuilder {

    data class IdleSegment(
        val indices: List<Int>,
        val vIdle: Double,
        val socMean: Double?
    )

    /**
     * Estimates dt series (seconds between samples).
     * Uses datetime column if available, otherwise assumes ~10Hz.
     */
    private fun estimateDtSeries(df: DataFrame<*>): List<Double>? {
        if ("datetime" !in df.columnNames()) {
            // Fallback: 10Hz
            return List(df.rowsCount() - 1) { 0.1 }
        }

        // Try to parse datetime column
        val datetimes = df["datetime"].values().map { it?.toString() }
        if (datetimes.filterNotNull().size < 2) {
            return List(df.rowsCount() - 1) { 0.1 }
        }

        // Simple diff estimation (would need proper datetime parsing)
        // For now, fallback to 10Hz
        return List(df.rowsCount() - 1) { 0.1 }
    }

    /**
     * Builds V_idle_local series aligned to DataFrame index.
     * 
     * @param vCol voltage column name
     * @param iCol current column name
     * @param socVoltCol optional SoC voltage column
     * @param idleCurrentAbs threshold for quasi-idle detection (A)
     * @param minIdleDurationS minimum duration for valid plateau (s)
     * @param maxDvdtAbs maximum |dV/dt| for stable points (V/s)
     */
    fun buildVIdleProfile(
        df: DataFrame<*>,
        vCol: String,
        iCol: String,
        socVoltCol: String? = null,
        idleCurrentAbs: Double = 3.0,
        minIdleDurationS: Double = 5.0,
        maxDvdtAbs: Double = 0.5
    ): List<Double> {
        if (df.rowsCount() == 0) {
            return emptyList()
        }

        val dtSeries = estimateDtSeries(df)
        if (dtSeries == null || dtSeries.isEmpty()) {
            // Fallback to global V_idle
            val voltages = df[vCol].values().filterIsInstance<Number>().map { it.toDouble() }
            val currents = df[iCol].values().filterIsInstance<Number>().map { it.toDouble() }
            val lowIndices = currents.indices.filter { abs(currents[it]) < idleCurrentAbs }

            val vIdleGlobal = if (lowIndices.isNotEmpty()) {
                voltages.filterIndexed { i, _ -> i in lowIndices }.sorted()
                    .let { it.getOrNull((it.size * 0.95).toInt()) ?: voltages.maxOrNull() ?: 0.0 }
            } else {
                voltages.maxOrNull() ?: 0.0
            }

            return List(df.rowsCount()) { vIdleGlobal }
        }

        val voltages = df[vCol].values().filterIsInstance<Number>().map { it.toDouble() }
        val currents = df[iCol].values().filterIsInstance<Number>().map { it.toDouble() }

        // Detect low current segments
        val lowMask = currents.map { abs(it) < idleCurrentAbs }
        val lowIndices = lowMask.indices.filter { lowMask[it] }

        if (lowIndices.isEmpty()) {
            // No idle points, fallback
            val vIdleGlobal = voltages.maxOrNull() ?: 0.0
            return List(df.rowsCount()) { vIdleGlobal }
        }

        // Group consecutive low-current segments
        val segments = mutableListOf<Pair<Int, Int>>()
        if (lowIndices.isNotEmpty()) {
            var start = lowIndices[0]
            var prev = lowIndices[0]

            for (idx in lowIndices.drop(1)) {
                if (idx == prev + 1) {
                    prev = idx
                } else {
                    segments.add(start to prev)
                    start = idx
                    prev = idx
                }
            }
            segments.add(start to prev)
        }

        // Filter segments by duration and voltage stability
        val validSegments = mutableListOf<IdleSegment>()

        for ((i0, i1) in segments) {
            if (i1 <= i0) continue

            // Compute segment duration
            val segDt = dtSeries.subList(i0, minOf(i1, dtSeries.size))
            val duration = segDt.sum()

            if (duration < minIdleDurationS) continue

            // Check voltage stability (dV/dt)
            val vSeg = voltages.subList(i0, i1 + 1)
            val dtSeg = dtSeries.subList(i0, minOf(i1, dtSeries.size))

            val dvdt = vSeg.zipWithNext().zip(dtSeg).map { (dv, dt) ->
                (dv.second - dv.first) / dt
            }

            val stableIndices = dvdt.indices.filter { abs(dvdt[it]) <= maxDvdtAbs }
            if (stableIndices.size < 3) continue

            val stableVoltages = stableIndices.map { vSeg[it] }
            val vIdleSeg = stableVoltages.sorted()
                .let { it.getOrNull((it.size * 0.95).toInt()) ?: stableVoltages.average() }

            val socMean = if (socVoltCol != null && socVoltCol in df.columnNames()) {
                val socVals = df[socVoltCol].values()
                    .filterIsInstance<Number>()
                    .map { it.toDouble() }
                    .filterIndexed { idx, _ -> idx in (i0..i1) && idx in stableIndices }
                socVals.average()
            } else {
                null
            }

            validSegments.add(IdleSegment((i0..i1).toList(), vIdleSeg, socMean))
        }

        if (validSegments.isEmpty()) {
            // No valid plateaus, use global fallback
            val lowVoltages = lowIndices.map { voltages[it] }
            val vIdleGlobal = lowVoltages.sorted()
                .let { it.getOrNull((it.size * 0.95).toInt()) ?: voltages.maxOrNull() ?: 0.0 }
            return List(df.rowsCount()) { vIdleGlobal }
        }

        // Build V_idle(SoC) interpolation if SoC available
        if (socVoltCol != null && validSegments.all { it.socMean != null }) {
            val socArray = validSegments.mapNotNull { it.socMean }.sorted()
            val vArray = validSegments.mapNotNull { seg ->
                if (seg.socMean != null) seg.vIdle else null
            }

            if (socArray.size >= 2) {
                // Linear interpolation function
                val socMin = socArray.first()
                val socMax = socArray.last()
                val vMin = vArray.first()
                val vMax = vArray.last()

                val socValues = if (socVoltCol in df.columnNames()) {
                    df[socVoltCol].values().filterIsInstance<Number>().map { it.toDouble() }
                } else {
                    List(df.rowsCount()) { 50.0 }
                }

                return socValues.map { soc ->
                    when {
                        soc.isNaN() -> vArray.average()
                        soc <= socMin -> vMin
                        soc >= socMax -> vMax
                        else -> {
                            // Linear interpolation
                            val idx = socArray.indexOfFirst { it >= soc }
                            if (idx <= 0) vMin
                            else {
                                val s0 = socArray[idx - 1]
                                val s1 = socArray[idx]
                                val v0 = vArray[idx - 1]
                                val v1 = vArray[idx]
                                v0 + (v1 - v0) * (soc - s0) / (s1 - s0)
                            }
                        }
                    }
                }
            }
        }

        // Fallback: use mean of valid segments
        val vIdleGlobal = validSegments.map { it.vIdle }.average()
        return List(df.rowsCount()) { vIdleGlobal }
    }
}
