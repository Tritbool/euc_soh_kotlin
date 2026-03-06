package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.CsvSource
import io.github.eucsoh.model.MOSFETParams
import org.apache.http.entity.InputStreamEntity
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.round

/**
 * Main CSV analysis: computes R_eq and SoH metrics for a single file.
 * Port of compute_req_stats_for_file() from soh_core_en.py.
 */
object ReqStatsComputer {

    data class FileStats(
        val file: String,
        val source: String,
        val datetimeFirst: String?,
        val wheelKm: Double?,
        val wheelKmSource: String?,
        val vIdle: Double,
        val ns: Int?,
        val socRefOk: Boolean,
        val socRefVFull: Double?,
        val nPoints: Int,
        val reqMean: Double,
        val reqMedian: Double,
        val reqMedian25C: Double,
        val req95p: Double,
        val sag95p: Double,
        val sagMax: Double,
        val vMinStrong: Double,
        val iMax: Double,
        val i95p: Double,
        val tempBoardMax: Double?,
        val tempMotorMax: Double?,
        val iPhase2Int: Double?,
        val iPhaseMax: Double?,
        val iPhase95p: Double?,
        val rMosfetHot: Double?,
        val rBattMedian: Double?,
        val rBattMedian25C: Double?
    )

    /**
     * Analyzes one CSV file and returns stats.
     */
    fun computeReqStatsForFile(
        csvPath: String,
        csvSource: CsvSource? = null,
        speedThr: Double = 20.0,
        curThr: Double = 5.0,
        mosfetParams: MOSFETParams? = null,
        eaJPerMol: Double? = null
    ): FileStats? {
        var stream: InputStream?=null
        var df = try {
            if (csvSource != null) {
                stream = csvSource.openCsvStream(csvPath)
                DataFrame.readCSV(stream)
            } else {
                DataFrame.readCSV(csvPath)
            }
        } catch (e: Exception) {
            if (Constants.DEBUG) println("[ERROR] Failed to read $csvPath: ${e.message}")
            return null
        }
        finally{
            try{stream?.close()}
            catch (e:Exception){}
        }

        if (df.rowsCount() == 0) return null
        val source = SourceDetection.detectSource(df)

        val vCol = "voltage"
        val iCol = "current"
        val sCol = "speed"

        // Check required columns
        if (vCol !in df.columnNames() || iCol !in df.columnNames() || sCol !in df.columnNames()) {
            if (Constants.DEBUG) println("[ERROR] Missing required columns in $csvPath")
            return null
        }
        df= df.filter { row ->
            row[vCol] != null && row[iCol] != null
        }

        val tempBoardCol = when {
            "system_temp" in df.columnNames() -> "system_temp"
            "temp" in df.columnNames() -> "temp"
            else -> null
        }

        val tempMotorCol = when {
            "temp_motor" in df.columnNames() -> "temp_motor"
            "temp2" in df.columnNames() -> "temp2"
            else -> null
        }

        val iPhaseCol = when {
            "current_phase" in df.columnNames() -> "current_phase"
            "phase_current" in df.columnNames() -> "phase_current"
            else -> null
        }

        val socCol = listOf("battery_level", "battery", "soc")
            .firstOrNull { it in df.columnNames() }

        val (wheelKm, kmSource) = SourceDetection.normalizeDistanceTotal(df, source)

        // Estimate Ns from max voltage
        val vIdleMax = df[vCol].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0
        val nsEst = round(vIdleMax / 4.2).toInt()
        val ns = if (nsEst in Constants.NS_MIN..Constants.NS_MAX) nsEst else null

        // SoC reference check
        var socRefOk = false
        var socRefVIdle: Double? = null

        if (ns != null && socCol != null) {
            val dfFull = df.filter {
                val soc = (it[socCol] as? Number)?.toDouble()
                val cur = (it[iCol] as? Number)?.toDouble()
                soc != null && cur != null && soc >= 98.0 && abs(cur) < 2.0
            }

            if (dfFull.rowsCount() > 0) {
                val vFull = dfFull[vCol].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0
                val vCellFull = vFull / ns
                if (vCellFull in 4.05..4.25) {
                    socRefOk = true
                    socRefVIdle = vFull
                }
            }
        }

        // Compute SoC voltage if possible
        var socVoltCol: String? = null
        if (ns != null && socRefOk && socRefVIdle != null) {
            val vCellFull = socRefVIdle / ns
            val vCellMin = 3.0
            val span = vCellFull - vCellMin

            if (span > 0.5) {
                val socVolt = df[vCol].values()
                    .filterNotNull()
                    .mapNotNull { v ->
                        (v as? Number)?.toDouble()?.let { vNum ->
                            val vCell = vNum / ns
                            val soc = ((vCell - vCellMin) / span).coerceIn(0.0, 1.0) * 100.0
                            soc
                        }
                    }

                socVoltCol = "soc_voltage"
                df = df.add(socVoltCol) { socVolt }
            }
        }

        // Build V_idle_local
        val vIdleLocal = VIdleProfileBuilder.buildVIdleProfile(
            df = df,
            vCol = vCol,
            iCol = iCol,
            socVoltCol = socVoltCol,
            idleCurrentAbs = 3.0,
            minIdleDurationS = 5.0,
            maxDvdtAbs = 0.5
        )

        // Global V_idle (for export/info)
        val vIdleGlobal = vIdleLocal.average()

        // Current window
        val (iMinBase, iMaxBase) = PackInference.chooseBatteryCurrentWindow(ns)
        var i_Min = maxOf(iMinBase, curThr)
        var i_Max = iMaxBase

        // Build arrays with null-safe extraction
        // Python implicitly filters nulls via boolean conditions - we do the same
        val rowCount = df.rowsCount()
        val voltages = DoubleArray(rowCount)
        val currents = DoubleArray(rowCount)
        val speeds = DoubleArray(rowCount)
        
        for (i in 0 until rowCount) {
            voltages[i] = (df[vCol][i] as? Number)?.toDouble() ?: Double.NaN
            currents[i] = (df[iCol][i] as? Number)?.toDouble() ?: Double.NaN
            speeds[i] = (df[sCol][i] as? Number)?.toDouble() ?: Double.NaN
        }

        val socValues = if (socVoltCol != null) {
            // Use computed SoC voltage
            DoubleArray(rowCount) { i -> 
                val v = voltages[i]
                if (v.isNaN()) Double.NaN
                else (v / (ns ?: 1) - 3.0) / 1.2 * 100.0
            }
        } else if (socCol != null) {
            DoubleArray(rowCount) { i ->
                (df[socCol][i] as? Number)?.toDouble() ?: Double.NaN
            }
        } else {
            DoubleArray(rowCount) { Double.NaN }
        }

        // Filter indices - nulls (NaN) will naturally fail the comparison conditions
        var filteredIndices = (0 until rowCount).filter { i ->
            !speeds[i].isNaN() && !currents[i].isNaN() && !voltages[i].isNaN() &&
            speeds[i] > speedThr &&
            abs(currents[i]) >= i_Min &&
            abs(currents[i]) <= i_Max
        }

        if (filteredIndices.size < 50) {
            i_Min *= 0.7
            i_Max *= 1.3
            filteredIndices = (0 until rowCount).filter { i ->
                !speeds[i].isNaN() && !currents[i].isNaN() && !voltages[i].isNaN() &&
                speeds[i] > speedThr &&
                abs(currents[i]) >= i_Min &&
                abs(currents[i]) <= i_Max
            }
        }

        // SoC filtering
        filteredIndices = filteredIndices.filter { i ->
            val soc = socValues[i]
            soc.isNaN() || (soc > 20.0 && soc < 90.0)
        }

        if (filteredIndices.isEmpty()) return null

        // Compute sag and Req
        val sags = filteredIndices.map { i ->
            vIdleLocal[i] - voltages[i]
        }

        val reqs = filteredIndices.map { i ->
            sags[filteredIndices.indexOf(i)] / abs(currents[i])
        }

        val reqMean = reqs.average()
        val reqMedian = reqs.sorted().let { it[it.size / 2] }
        val req95p = reqs.sorted().let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
        val sag95p = sags.sorted().let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
        val sagMax = sags.maxOrNull() ?: 0.0

        val vMinStrong = filteredIndices.map { voltages[it] }.minOrNull() ?: 0.0
        val iMax = filteredIndices.map { abs(currents[it]) }.maxOrNull() ?: 0.0
        val i95p = filteredIndices.map { abs(currents[it]) }.sorted()
            .let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }

        // Temperature
        val tempBoardMax = tempBoardCol?.let { col ->
            df[col].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
        }

        val tempMotorMax = tempMotorCol?.let { col ->
            df[col].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
        }

        // Phase current metrics (I²dt dose)
        var iPhase2Int: Double? = null
        var iPhaseMax: Double? = null
        var iPhase95p: Double? = null

        if (iPhaseCol != null) {
            val iPhaseVals = filteredIndices.mapNotNull { i ->
                (df[iPhaseCol][i] as? Number)?.toDouble()
            }

            if (iPhaseVals.isNotEmpty()) {
                val iPhaseAbs = iPhaseVals.map { abs(it) }

                // I²dt integration (simple: dt ≈ 0.1s)
                val dt = 0.1
                iPhase2Int = iPhaseAbs.sumOf { it * it } * dt

                iPhaseMax = iPhaseAbs.maxOrNull()
                iPhase95p = iPhaseAbs.sorted().let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
            }
        }

        val ea = eaJPerMol ?: 20000.0

        val reqMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(
            rBattMeasured = reqMedian,
            tempMeasuredC = tempBoardMax,
            eaJPerMol = ea
        )

        // MOSFET split if params provided
        var rMosfetHot: Double? = null
        var rBattMedian: Double? = null
        var rBattMedian25C: Double? = null

        if (mosfetParams != null && tempBoardMax != null) {
            rMosfetHot = mosfetParams.rMosfetAtTemp(tempBoardMax)
            rBattMedian = maxOf(0.0, reqMedian - rMosfetHot)
            rBattMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(
                rBattMeasured = rBattMedian,
                tempMeasuredC = tempBoardMax,
                eaJPerMol = ea
            )
        }

        val firstDt = SourceDetection.getFirstDatetime(df, source)

        return FileStats(
            file = csvPath.substringAfterLast('/'),
            source = source,
            datetimeFirst = firstDt,
            wheelKm = wheelKm,
            wheelKmSource = kmSource,
            vIdle = vIdleGlobal,
            ns = ns,
            socRefOk = socRefOk,
            socRefVFull = socRefVIdle,
            nPoints = filteredIndices.size,
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
