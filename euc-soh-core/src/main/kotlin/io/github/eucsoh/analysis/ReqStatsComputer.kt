package io.github.eucsoh.analysis

import com.google.common.base.Objects
import io.github.eucsoh.Constants
import io.github.eucsoh.Constants.CommonColumns
import io.github.eucsoh.Constants.WheelLogColumns
import io.github.eucsoh.Constants.EUCWorldColumns
import io.github.eucsoh.Constants.EUC_WORLD
import io.github.eucsoh.Constants.KNOWN_SERIES
import io.github.eucsoh.Constants.MAXIIMAL_CELL_V
import io.github.eucsoh.Constants.WHEELLOG
import io.github.eucsoh.CsvSource
import io.github.eucsoh.model.MOSFETParams
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.ceil
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
        val rBattMedian25C: Double?,
        val pwm95p: Double?,
        val pwmMax: Double?
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
        var stream: InputStream? = null
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
        } finally {
            try {
                stream?.close()
            } catch (e: Exception) {
            }
        }

        if (df.rowsCount() == 0) return null
        val source = SourceDetection.detectSource(df)

        val vCol = CommonColumns.VOLTAGE.csv_code
        val iCol = CommonColumns.CURRENT.csv_code
        val sCol = CommonColumns.SPEED.csv_code

        // Check required columns
        if (vCol !in df.columnNames() || iCol !in df.columnNames() || sCol !in df.columnNames()) {
            if (Constants.DEBUG) println("[ERROR] Missing required columns in $csvPath")
            return null
        }
        df = df.filter { row ->
            row[vCol] != null && row[iCol] != null
        }

        val tempBoardCol = when {
            EUCWorldColumns.BOARD_TEMPERATURE.csv_code in df.columnNames() -> EUCWorldColumns.BOARD_TEMPERATURE.csv_code
            WheelLogColumns.BOARD_TEMPERATURE.csv_code in df.columnNames() -> WheelLogColumns.BOARD_TEMPERATURE.csv_code
            else -> null
        }

        val tempMotorCol = when {
            EUCWorldColumns.MOTOR_TEMPERATURE.csv_code in df.columnNames() -> EUCWorldColumns.MOTOR_TEMPERATURE.csv_code
            WheelLogColumns.MOTOR_TEMPERATURE.csv_code in df.columnNames() -> WheelLogColumns.MOTOR_TEMPERATURE.csv_code
            else -> null
        }

        val iPhaseCol = when {
            EUCWorldColumns.CURRENT_PHASE.csv_code in df.columnNames() -> EUCWorldColumns.CURRENT_PHASE.csv_code
            WheelLogColumns.CURRENT_PHASE.csv_code in df.columnNames() -> WheelLogColumns.CURRENT_PHASE.csv_code
            else -> null
        }
        val pwmCol = when {
            EUCWorldColumns.PWM.csv_code in df.columnNames() -> EUCWorldColumns.PWM.csv_code
            WheelLogColumns.PWM.csv_code in df.columnNames() -> WheelLogColumns.PWM.csv_code
            else -> null
        }
        
        val socCol = listOf(WheelLogColumns.SOC.csv_code, EUCWorldColumns.SOC.csv_code, "soc")
            .firstOrNull { it in df.columnNames() }

        val (wheelKm, kmSource) = SourceDetection.normalizeDistanceTotal(df, source)

        // Estimate Ns from max voltage
        val vIdleMax =
            df[vCol].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0
        val nsEst = {
            val ceiled = ceil(vIdleMax / MAXIIMAL_CELL_V).toInt()
            val rounded = round(vIdleMax / MAXIIMAL_CELL_V).toInt()
            if (ceiled in KNOWN_SERIES) ceiled else rounded
        }
        val ns = if (nsEst() in Constants.NS_MIN..Constants.NS_MAX) nsEst() else null

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
                val vFull =
                    dfFull[vCol].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
                        ?: 0.0
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

        val pwms: DoubleArray =
            if (pwmCol != null && pwmCol == EUCWorldColumns.PWM.csv_code) {
                df.rows()                                     // iterable of rows
                    .asSequence()
                    .mapNotNull { row ->
                        val speed = (row[sCol] as? Number)?.toDouble() ?: return@mapNotNull null
                        if (speed <= speedThr) return@mapNotNull null

                        val pwm = row[pwmCol] as? Number ?: return@mapNotNull null
                        100.0 - pwm.toDouble()
                    }
                    .toList()
                    .toDoubleArray()
            } else {
                DoubleArray(0)
            }
            
        val pwmMax = pwms.maxOrNull() ?: 0.0

        val pwm95p = if(pwms.isEmpty()) 0.0 else pwms.sorted().let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
        

// Phase current metrics (I²dt dose, normalized by ride duration like Python)
        var iPhase2Int: Double? = null
        var iPhaseMax: Double? = null
        var iPhase95p: Double? = null

        if (iPhaseCol != null) {
            val iPhaseVals = filteredIndices.mapNotNull { idx ->
                (df[iPhaseCol][idx] as? Number)?.toDouble()
            }

            if (iPhaseVals.isNotEmpty()) {
                val iPhaseAbs = iPhaseVals.map { abs(it) }

                // 1) Estime la durée de ride en secondes
                val rideDurationSeconds: Double? = when (source) {
                    EUC_WORLD -> {
                        when {
                            // a) Colonne duration (en secondes ou ms selon ton CSV)
                            EUCWorldColumns.DURATION.csv_code in df.columnNames() -> {
                                df[EUCWorldColumns.DURATION.csv_code].values()
                                    .filterIsInstance<Number>()
                                    .maxOfOrNull { it.toDouble() }
                            }
                            // b) datetime/gps_datetime : max - min
                            EUCWorldColumns.TIMESTAMP.csv_code in df.columnNames() -> {
                                val times = df[EUCWorldColumns.TIMESTAMP.csv_code].values()
                                    .filterIsInstance<java.time.temporal.Temporal>()
                                if (times.isNotEmpty()) {
                                    val tMin = times.minByOrNull { it.toString() }!!
                                    val tMax = times.maxByOrNull { it.toString() }!!
                                    java.time.Duration.between(tMin, tMax).seconds.toDouble()
                                } else null
                            }

                            else -> null
                        }
                    }

                    WHEELLOG -> {
                        when {
                            // d) date + time -> construire LocalDateTime si tu les as comme String
                            WheelLogColumns.DATE.csv_code in df.columnNames() &&
                                    WheelLogColumns.TIME.csv_code in df.columnNames() -> {
                                val datetimeDf =
                                    df.select { WheelLogColumns.DATE.csv_code and WheelLogColumns.TIME.csv_code }
                                        .merge { WheelLogColumns.DATE.csv_code and WheelLogColumns.TIME.csv_code }
                                        .by("T").into("datetime")

                                val first = java.time.LocalDateTime.parse(
                                    datetimeDf["datetime"].first().toString()
                                )
                                val last = java.time.LocalDateTime.parse(
                                    datetimeDf["datetime"].last().toString()
                                )
                                java.time.Duration.between(first, last).seconds.toDouble()

                            }

                            else -> null
                        }
                    }

                    else -> null
                }

                // 2) Intégration I²dt
                val i2dtRaw: Double =
                    if (rideDurationSeconds != null && rideDurationSeconds > 10.0) {
                        // Si tu as un vrai temps, tu peux approximer dt = rideDuration / N
                        val n = iPhaseAbs.size
                        if (n > 1) {
                            val dt = rideDurationSeconds / (n - 1)
                            iPhaseAbs.sumOf { it * it * dt }
                        } else {
                            0.0
                        }
                    } else {
                        // Fallback Python: dt ≈ 0.1 s
                        val dtFallback = 0.1
                        iPhaseAbs.sumOf { it * it * dtFallback }
                    }

                iPhase2Int = if (rideDurationSeconds != null && rideDurationSeconds > 10.0) {
                    i2dtRaw / rideDurationSeconds
                } else {
                    i2dtRaw
                }

                iPhaseMax = iPhaseAbs.maxOrNull()
                iPhase95p = iPhaseAbs.sorted()
                    .let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
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
            rBattMedian25C = rBattMedian25C,
            pwm95p = pwm95p,
            pwmMax = pwmMax
        )
    }
}
