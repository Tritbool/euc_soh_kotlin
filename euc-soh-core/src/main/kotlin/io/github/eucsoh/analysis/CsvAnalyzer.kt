package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.CsvSource
import io.github.eucsoh.model.FileStats
import io.github.eucsoh.model.MOSFETParams
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.readCSV
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/**
 * Main CSV analyzer for SoH metrics computation.
 * Port of compute_req_stats_for_file() from soh_core_en.py
 */
object CsvAnalyzer {

    /**
     * Analyzes a single CSV file and computes Req, SoH metrics.
     * 
     * @param csvPath path to CSV file
     * @param speedThr minimum speed threshold (km/h)
     * @param curThr minimum current threshold (A)
     * @param mosfetParams optional MOSFET parameters for R_batt decomposition
     * @param eaJPerMol Arrhenius activation energy (J/mol), null = default 20kJ/mol
     * @return FileStats or null if file can't be analyzed
     */
    fun computeReqStatsForFile(
        csvPath: String,
        speedThr: Double = 20.0,
        curThr: Double = 5.0,
        mosfetParams: MOSFETParams? = null,
        eaJPerMol: Double? = null
    ): FileStats? {
        // Load CSV
        val df = try {
            DataFrame.readCSV(csvPath)
        } catch (e: Exception) {
            if (Constants.DEBUG) println("[CsvAnalyzer] Failed to read $csvPath: ${e.message}")
            return null
        }

        if (df.rowsCount() == 0) return null

        val source = SourceDetection.detectSource(df)

        // Column mapping
        val vCol = "voltage"
        val iCol = "current"
        val sCol = "speed"
        val tempBoardCol = if ("system_temp" in df.columnNames()) "system_temp" else "temp"

        // Phase current
        val iPhaseCol = when {
            "current_phase" in df.columnNames() -> "current_phase"
            "phase_current" in df.columnNames() -> "phase_current"
            else -> null
        }

        val tempMotorCol = when {
            "temp_motor" in df.columnNames() -> "temp_motor"
            "temp2" in df.columnNames() -> "temp2"
            else -> null
        }

        val socCol = listOf("battery_level", "battery", "soc")
            .firstOrNull { it in df.columnNames() }

        // Check required columns
        for (col in listOf(vCol, iCol, sCol)) {
            if (col !in df.columnNames()) {
                if (Constants.DEBUG) println("[CsvAnalyzer] Missing column $col in $csvPath")
                return null
            }
        }

        val (wheelKm, kmSource) = SourceDetection.normalizeDistanceTotal(df, source)

        // Estimate Ns from V_idle_max
        var ns: Int? = null
        var socRefOk = false
        var socRefVIdle: Double? = null

        if (socCol != null) {
            val vIdleMax = df[vCol].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0
            val nsEst = round(vIdleMax / 4.2).toInt()

            if (nsEst in Constants.NS_MIN..Constants.NS_MAX) {
                ns = nsEst

                // Check if we have a valid SoC reference at full charge
                val fullMask = df.filter {
                    val soc = (it[socCol] as? Number)?.toDouble() ?: return@filter false
                    val cur = (it[iCol] as? Number)?.toDouble() ?: return@filter false
                    soc >= 98.0 && abs(cur) < 2.0
                }

                if (fullMask.rowsCount() > 0) {
                    val vFull = fullMask[vCol].values()
                        .filterIsInstance<Number>()
                        .maxOfOrNull { it.toDouble() } ?: 0.0
                    val vCellFull = vFull / ns

                    if (vCellFull in 4.05..4.25) {
                        socRefOk = true
                        socRefVIdle = vFull
                    }
                }
            }
        }

        // Compute SoC from voltage if valid reference
        val socVoltCol = if (ns != null && socRefOk && socRefVIdle != null) {
            val vCellFull = socRefVIdle / ns
            val vCellMin = 3.0
            val span = vCellFull - vCellMin

            if (span > 0.5) {
                val socVoltage = df.convert(vCol)
                    .with { v->
                        val vCell = v as Double / ns
                        ((vCell - vCellMin) / span).coerceIn(0.0, 1.0) * 100.0
                    }
                // Would need to add column to df, skipping for now
                "soc_voltage"
            } else null
        } else null

        // Build V_idle_local profile
        val vIdleLocal = VIdleProfileBuilder.buildVIdleProfile(
            df = df,
            vCol = vCol,
            iCol = iCol,
            socVoltCol = socVoltCol,
            idleCurrentAbs = 3.0,
            minIdleDurationS = 5.0,
            maxDvdtAbs = 0.5
        )

        // Global V_idle for compatibility (mean of V_idle_local on low-current points)
        val currents = df[iCol].values().filterIsInstance<Number>().map { it.toDouble() }
        val lowIndices = currents.indices.filter { abs(currents[it]) < 3.0 }
        val vIdle = if (lowIndices.isNotEmpty()) {
            lowIndices.map { vIdleLocal[it] }.average()
        } else {
            vIdleLocal.average()
        }

        // Choose current window for Req
        var (i_Min, i_Max) = PackInference.chooseBatteryCurrentWindow(ns)
        i_Min = max(i_Max, curThr)

        // Filter points for Req calculation
        val voltages = df[vCol].values().filterIsInstance<Number>().map { it.toDouble() }
        val speeds = df[sCol].values().filterIsInstance<Number>().map { it.toDouble() }

        var validIndices = (0 until df.rowsCount()).filter { i ->
            speeds[i] > speedThr &&
            abs(currents[i]) >= i_Min &&
            abs(currents[i]) <= i_Max
        }

        // Relax thresholds if too few points
        if (validIndices.size < 50) {
            i_Min *= 0.7
            i_Max *= 1.3
            validIndices = (0 until df.rowsCount()).filter { i ->
                speeds[i] > speedThr &&
                abs(currents[i]) >= i_Min &&
                abs(currents[i]) <= i_Max
            }
        }

        // Apply SoC filter if available
        if (socVoltCol != null || socCol != null) {
            val socColToUse = socVoltCol ?: socCol
            validIndices = validIndices.filter { i ->
                val soc = (df[socColToUse!!][i] as? Number)?.toDouble() ?: return@filter false
                soc in 20.0..90.0
            }
        }

        if (validIndices.isEmpty()) {
            if (Constants.DEBUG) println("[CsvAnalyzer] No valid points for Req in $csvPath")
            return null
        }

        // Compute sag and Req using V_idle_local
        val sags = validIndices.map { i ->
            vIdleLocal[i] - voltages[i]
        }

        val reqs = validIndices.map { i ->
            sags[validIndices.indexOf(i)] / abs(currents[i])
        }

        val reqMean = reqs.average()
        val reqMedian = reqs.sorted().let { it[it.size / 2] }
        val req95p = reqs.sorted().let { it[(it.size * 0.95).toInt()] }
        val sag95p = sags.sorted().let { it[(it.size * 0.95).toInt()] }
        val sagMax = sags.maxOrNull() ?: 0.0

        val vMinStrong = validIndices.map { voltages[it] }.minOrNull() ?: 0.0
        val iMax = validIndices.map { abs(currents[it]) }.maxOrNull() ?: 0.0
        val i95p = validIndices.map { abs(currents[it]) }.sorted().let { it[(it.size * 0.95).toInt()] }

        // Temperature
        val tempBoardMax = if (tempBoardCol in df.columnNames()) {
            df[tempBoardCol].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
        } else null

        val tempMotorMax = if (tempMotorCol != null && tempMotorCol in df.columnNames()) {
            df[tempMotorCol].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
        } else null

        // Phase current metrics (MOSFET stress)
        var iPhase2Int: Double? = null
        var iPhaseMax: Double? = null
        var iPhase95p: Double? = null

        if (iPhaseCol != null && iPhaseCol in df.columnNames()) {
            val iPhaseValues = validIndices.mapNotNull { i ->
                (df[iPhaseCol][i] as? Number)?.toDouble()
            }

            if (iPhaseValues.isNotEmpty()) {
                val iPhaseAbs = iPhaseValues.map { abs(it) }

                // Estimate dt (0.1s fallback)
                val dt = 0.1
                iPhase2Int = iPhaseAbs.sumOf { it * it } * dt

                iPhaseMax = iPhaseAbs.maxOrNull()
                iPhase95p = iPhaseAbs.sorted().let { it[(it.size * 0.95).toInt()] }
            }
        }

        val datetimeFirst = SourceDetection.getFirstDatetime(df, source)

        // Arrhenius normalization
        val ea = eaJPerMol ?: 20000.0
        val reqMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(reqMedian, tempBoardMax, ea)

        // MOSFET decomposition
        var rMosfetHot: Double? = null
        var rBattMedian: Double? = null
        var rBattMedian25C: Double? = null

        if (mosfetParams != null && tempBoardMax != null) {
            rMosfetHot = mosfetParams.rMosfetAtTemp(tempBoardMax)
            rBattMedian = max(0.0, reqMedian - rMosfetHot)
            rBattMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(rBattMedian, tempBoardMax, ea)
        }

        return FileStats(
            file = csvPath.substringAfterLast('/'),
            source = source,
            datetimeFirst = datetimeFirst,
            wheelKm = wheelKm,
            wheelKmSource = kmSource,
            vIdle = vIdle,
            ns = ns,
            socRefOk = socRefOk,
            socRefVFull = socRefVIdle,
            nPoints = validIndices.size,
            reqMean = reqMean,
            reqMedian = reqMedian,
            reqMedian25C = reqMedian25C,
            req95p = req95p,
            sag95p = sag95p,
            sagMax = sagMax,
            vMinStrong = vMinStrong,
            iMax = iMax,
            i95p = i95p,
            tempBoardMax = tempBoardMax,
            tempMotorMax = tempMotorMax,
            iPhase2Int = iPhase2Int,
            iPhaseMax = iPhaseMax,
            iPhase95p = iPhase95p,
            rMosfetHot = rMosfetHot,
            rBattMedian = rBattMedian,
            rBattMedian25C = rBattMedian25C
        )
    }
}
