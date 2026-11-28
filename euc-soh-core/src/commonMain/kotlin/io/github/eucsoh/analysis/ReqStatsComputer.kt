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

import io.github.eucsoh.frame.LogFrame

import io.github.eucsoh.Constants
import io.github.eucsoh.Constants.CommonColumns
import io.github.eucsoh.Constants.EUCWorldColumns
import io.github.eucsoh.Constants.EUC_WORLD
import io.github.eucsoh.Constants.KNOWN_SERIES
import io.github.eucsoh.Constants.MAXIIMAL_CELL_V
import io.github.eucsoh.Constants.TUXWHEEL
import io.github.eucsoh.Constants.TuxWheelV2Columns
import io.github.eucsoh.Constants.WHEELLOG
import io.github.eucsoh.Constants.WheelLogColumns
import io.github.eucsoh.CsvSource
import io.github.eucsoh.Logger
import io.github.eucsoh.NoOpLogger
import io.github.eucsoh.model.MOSFETParams
import io.github.eucsoh.util.DateTimeUtils
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

/**
 * Main CSV analysis: computes R_eq and SoH metrics for a single file.
 */
object ReqStatsComputer {
    const val TAG = "ReqStatsComputer"
    /**
     * Parses timestamp strings (EUC World ISO-8601 or WheelLog date+time) to
     * epoch seconds.  EUC World produces offsets either as "+0200" (no colon)
     * or "+02:00" (with colon) depending on app version / device locale; both
     * are handled so that parse never silently falls back to dt=0.1.
     */
    private fun parseTimestamp(rawValue: Any?): Double? {
        if (rawValue == null) return null
        val raw = rawValue.toString().trim()
        if (raw.isEmpty()) return null
        return DateTimeUtils.parseToEpochSeconds(raw)
    }

    data class FileStats(
        val file: String,
        val source: String? = null,
        val readError: Boolean? = null,
        val tooBig: Boolean? = null,
        val datetimeFirst: String? = null,
        val wheelKm: Double? = null,
        val wheelKmSource: String? = null,
        val vIdle: Double? = null,
        val ns: Int? = null,
        val socRefOk: Boolean? = null,
        val socRefVFull: Double? = null,
        val nPoints: Int? = null,
        val reqMean: Double? = null,
        val reqMedian: Double? = null,
        val reqMedian25C: Double? = null,
        val req95p: Double? = null,
        val sag95p: Double? = null,
        val sagMax: Double? = null,
        val vMinStrong: Double? = null,
        val iMax: Double? = null,
        val i95p: Double? = null,
        val tempBoardMax: Double? = null,
        val tempMotorMax: Double? = null,
        val iPhase2Int: Double? = null,
        val iPhaseMax: Double? = null,
        val iPhase95p: Double? = null,
        val rMosfetHot: Double? = null,
        val rBattMedian: Double? = null,
        val rBattMedian25C: Double? = null,
        val pwm95p: Double? = null,
        val pwmMax: Double? = null,
        val pwmMedian: Double? = null,
        /**
         * True when the I²dt integration used the dt=0.1 s fallback because
         * real timestamps could not be parsed.  False when actual log timestamps
         * were successfully parsed and used for the trapezoidal integration.
         * Null when no phase-current column was present (metric not computed).
         */
        val fallbackDtUsed: Boolean? = null
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
        eaJPerMol: Double? = null,
        logger: Logger = NoOpLogger
    ): FileStats? {
        logger.d(TAG, "Computing stats for $csvPath")

        var df = try {
            val csvText = csvSource?.readCsv(csvPath)
                ?: io.github.eucsoh.readCsvFromPath(csvPath)
                ?: throw IllegalStateException("No CSV source available for $csvPath")
            LogFrame.readCsv(csvText)
        } catch (e: Exception) {
            logger.e(TAG, "[ERROR] Failed to read $csvPath: ${e.message}")
            if (e.message != null && e.message!!.contains("OOM", ignoreCase = true)) {
                return FileStats(
                    file = csvPath.substringAfterLast('/'),
                    tooBig = true
                )
            }
            return FileStats(
                file = csvPath.substringAfterLast('/'),
                readError = true
            )
        }

        if (df.rowsCount() == 0) {
            logger.e(TAG,"[ERROR] empty file: $csvPath")
            return FileStats(
                file = csvPath.substringAfterLast('/'),
                nPoints = 0
            )
        }
        val source =
            if (csvPath.contains(Constants.DARKNESS_BOT)) Constants.DARKNESS_BOT else SourceDetection.detectSource(
                df,
                csvPath
            )

        val tuxV2 = source == TUXWHEEL && SourceDetection.isTuxWheelV2(df)
        val vCol = if (tuxV2) TuxWheelV2Columns.VOLTAGE.csv_code else CommonColumns.VOLTAGE.csv_code
        val iCol = if (tuxV2) TuxWheelV2Columns.CURRENT.csv_code else CommonColumns.CURRENT.csv_code
        val sCol = if (tuxV2) TuxWheelV2Columns.SPEED.csv_code else CommonColumns.SPEED.csv_code

        // Check required columns
        if (vCol !in df.columnNames() || iCol !in df.columnNames() || sCol !in df.columnNames()) {
            logger.e(TAG, "[ERROR] Missing required columns in $csvPath")
            return FileStats(
                file = csvPath.substringAfterLast('/'),
                nPoints = 0
            )
        }
        df = df.filter { row ->
            row[vCol] != null && row[iCol] != null
        }

        val tempBoardCol = when {
            tuxV2 && TuxWheelV2Columns.MAINBOARD_TEMPERATURE.csv_code in df.columnNames() -> TuxWheelV2Columns.MAINBOARD_TEMPERATURE.csv_code
            tuxV2 && TuxWheelV2Columns.BOARD_TEMPERATURE.csv_code in df.columnNames() -> TuxWheelV2Columns.BOARD_TEMPERATURE.csv_code
            EUCWorldColumns.BOARD_TEMPERATURE.csv_code in df.columnNames() -> EUCWorldColumns.BOARD_TEMPERATURE.csv_code
            WheelLogColumns.BOARD_TEMPERATURE.csv_code in df.columnNames() -> WheelLogColumns.BOARD_TEMPERATURE.csv_code
            else -> null
        }

        val tempMotorCol = when {
            tuxV2 && TuxWheelV2Columns.MOTOR_TEMPERATURE.csv_code in df.columnNames() -> TuxWheelV2Columns.MOTOR_TEMPERATURE.csv_code
            EUCWorldColumns.MOTOR_TEMPERATURE.csv_code in df.columnNames() -> EUCWorldColumns.MOTOR_TEMPERATURE.csv_code
            WheelLogColumns.MOTOR_TEMPERATURE.csv_code in df.columnNames() -> WheelLogColumns.MOTOR_TEMPERATURE.csv_code
            else -> null
        }

        val iPhaseCol = when {
            tuxV2 -> TuxWheelV2Columns.CURRENT_PHASE.csv_code.takeIf { it in df.columnNames() }
            EUCWorldColumns.CURRENT_PHASE.csv_code in df.columnNames() -> EUCWorldColumns.CURRENT_PHASE.csv_code
            WheelLogColumns.CURRENT_PHASE.csv_code in df.columnNames() -> WheelLogColumns.CURRENT_PHASE.csv_code
            else -> null
        }
        val pwmCol = when {
            tuxV2 -> TuxWheelV2Columns.PWM.csv_code.takeIf { it in df.columnNames() }
            EUCWorldColumns.PWM.csv_code in df.columnNames() -> EUCWorldColumns.PWM.csv_code
            WheelLogColumns.PWM.csv_code in df.columnNames() -> WheelLogColumns.PWM.csv_code
            else -> null
        }

        val socCol = listOf(
            TuxWheelV2Columns.SOC.csv_code,
            WheelLogColumns.SOC.csv_code,
            EUCWorldColumns.SOC.csv_code,
            "soc"
        ).firstOrNull { it in df.columnNames() }

        val (wheelKm, kmSource) = SourceDetection.normalizeDistanceTotal(df, source)

        // Estimate Ns from max voltage
        val vIdleMax =
            df[vCol].filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0
        val nsEst = {
            val ceiled = ceil(vIdleMax / MAXIIMAL_CELL_V).toInt()
            val rounded = round(vIdleMax / MAXIIMAL_CELL_V).toInt()
            if (ceiled in KNOWN_SERIES) ceiled else rounded
        }
        // TuxWheel v2 reports the pack's cell voltages on every row: use the
        // exact cell count when available instead of the voltage heuristic.
        val nsFromCells = if (tuxV2 && TuxWheelV2Columns.CELL_VOLTAGES.csv_code in df.columnNames()) {
            df[TuxWheelV2Columns.CELL_VOLTAGES.csv_code]
                .firstNotNullOfOrNull { raw ->
                    raw as? String ?: return@firstNotNullOfOrNull null
                }
                ?.trim()
                ?.removePrefix("[")
                ?.removeSuffix("]")
                ?.split(",")
                ?.count { it.trim().toDoubleOrNull() != null }
                ?.takeIf { it in Constants.NS_MIN..Constants.NS_MAX }
        } else {
            null
        }
        val ns = nsFromCells ?: if (nsEst() in Constants.NS_MIN..Constants.NS_MAX) nsEst() else null

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
                    dfFull[vCol].filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
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
                val socVolt = df[vCol].map { v ->
                    (v as? Number)?.toDouble()?.let { vNum ->
                        val vCell = vNum / ns
                        ((vCell - vCellMin) / span).coerceIn(0.0, 1.0) * 100.0
                    }
                }

                socVoltCol = "soc_voltage"
                df = df.add(socVoltCol, socVolt)
            }
        }

        // Current window
        val (iMinBase, iMaxBase) = PackInference.chooseBatteryCurrentWindow(ns)
        var i_Min = maxOf(iMinBase, curThr)
        var i_Max = iMaxBase


        // Build V_idle_local
        val vIdleLocal = VIdleProfileBuilder.buildVIdleProfile(
            df = df,
            vCol = vCol,
            iCol = iCol,
            socVoltCol = socVoltCol,
            idleCurrentAbs = i_Min,
            minIdleDurationS = 5.0,
            maxDvdtAbs = 0.5
        )

        // Global V_idle (for export/info)
        val vIdleGlobal = vIdleLocal.average()


        // Build arrays with null-safe extraction
        // Python implicitly filters nulls via boolean conditions - we do the same
        val rowCount = df.rowsCount()
        val voltages = DoubleArray(rowCount)
        val currents = DoubleArray(rowCount)
        val speeds = DoubleArray(rowCount)

        // Les colonnes sont résolues une fois ici : `df[col]` recherche la colonne dans la
        // map du frame, et l'appeler par cellule coûtait un lookup pour chaque ligne. Le
        // frame n'est pas modifié entre ici et la boucle, donc la série est la même.
        val vSeries = df[vCol]
        val iSeries = df[iCol]
        val sSeries = df[sCol]
        for (i in 0 until rowCount) {
            voltages[i] = (vSeries[i] as? Number)?.toDouble() ?: Double.NaN
            currents[i] = (iSeries[i] as? Number)?.toDouble() ?: Double.NaN
            speeds[i] = (sSeries[i] as? Number)?.toDouble() ?: Double.NaN
        }

        val socValues = if (socVoltCol != null) {
            // Use computed SoC voltage
            DoubleArray(rowCount) { i ->
                val v = voltages[i]
                if (v.isNaN()) Double.NaN
                else (v / (ns ?: 1) - 3.0) / 1.2 * 100.0
            }
        } else if (socCol != null) {
            val socSeries = df[socCol]
            DoubleArray(rowCount) { i ->
                (socSeries[i] as? Number)?.toDouble() ?: Double.NaN
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

        if (filteredIndices.isEmpty()) {
            logger.d(TAG, "[ERROR] Missing required data in $csvPath")
            return FileStats(
                file = csvPath.substringAfterLast('/'),
                nPoints = 0
            )
        }

        // Compute sag and Req
        val sags = filteredIndices.map { i ->
            vIdleLocal[i] - voltages[i]
        }

        // `sags[k]` est le sag de `filteredIndices[k]` : la position parcourue par `map`
        // EST l'index que `indexOf(i)` allait rechercher. L'appeler à chaque Req relisait
        // toute la liste filtrée pour chaque Req — quadratique sur les gros journaux, et
        // c'était le premier poste de coût de la mesure. Résultat strictement identique.
        val reqs = filteredIndices.mapIndexed { k, i ->
            sags[k] / abs(currents[i])
        }

        val reqMean = reqs.average()
        // Un seul tri : reqMedian et req95p triaient chacun une copie complète de reqs.
        val reqsSorted = reqs.sorted()
        val reqMedian = reqsSorted[reqsSorted.size / 2]
        val req95p = reqsSorted[(reqsSorted.size * 0.95).toInt().coerceIn(0, reqsSorted.size - 1)]
        val sag95p = sags.sorted().let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
        val sagMax = sags.maxOrNull() ?: 0.0

        val vMinStrong = filteredIndices.map { voltages[it] }.minOrNull() ?: 0.0
        val iMax = filteredIndices.map { abs(currents[it]) }.maxOrNull() ?: 0.0
        val i95p = filteredIndices.map { abs(currents[it]) }.sorted()
            .let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }

        // Temperature
        val tempBoardMax = tempBoardCol?.let { col ->
            df[col].filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
        }

        val tempMotorMax = tempMotorCol?.let { col ->
            df[col].filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
        }

        val pwms: DoubleArray =
            if (pwmCol != null) {
                df.rows()                                     // iterable of rows
                    .asSequence()
                    .mapNotNull { row ->
                        val speed = (row[sCol] as? Number)?.toDouble() ?: return@mapNotNull null
                        if (speed <= speedThr) return@mapNotNull null

                        val pwm = row[pwmCol] as? Number ?: return@mapNotNull null
                        if (pwmCol == EUCWorldColumns.PWM.csv_code) {
                            100.0 - pwm.toDouble()
                        } else {
                            pwm.toDouble()
                        }
                    }
                    .toList()
                    .toDoubleArray()
            } else {
                DoubleArray(0)
            }

        val pwmMax = pwms.maxOrNull() ?: 0.0

        val pwm95p = if (pwms.isEmpty()) 0.0 else pwms.sorted()
            .let { it[(it.size * 0.95).toInt().coerceIn(0, it.size - 1)] }
        val pwmMedian = if (pwms.isEmpty()) 0.0 else pwms.sorted().let { it[it.size / 2] }

// Phase current metrics (I²dt dose, normalized by ride duration like Python)
        var iPhase2Int: Double? = null
        var iPhaseMax: Double? = null
        var iPhase95p: Double? = null
        var fallbackDtUsed: Boolean? = null

        if (iPhaseCol != null) {
            val iPhaseVals = filteredIndices.mapNotNull { idx ->
                (df[iPhaseCol][idx] as? Number)?.toDouble()
            }

            if (iPhaseVals.isNotEmpty()) {
                val iPhaseAbs = iPhaseVals.map { abs(it) }

                // Extraire les timestamps réels des filteredIndices
                // WheelLog-style date+time columns, also used by legacy TuxWheel logs.
                fun wheelLogDateTimes(): DoubleArray? {
                    return try {
                        if (WheelLogColumns.DATE.csv_code in df.columnNames() &&
                            WheelLogColumns.TIME.csv_code in df.columnNames()
                        ) {
                            val parsedTimes = filteredIndices.map { idx ->
                                val d = df[WheelLogColumns.DATE.csv_code][idx]?.toString()
                                    ?: return@map Double.NaN
                                val t = df[WheelLogColumns.TIME.csv_code][idx]?.toString()
                                    ?: return@map Double.NaN
                                val seconds = DateTimeUtils.parseToEpochSeconds("${d}T${t}")
                                if (seconds == null) {
                                    logger.d(TAG, "Failed to parse Wheelog timestamps: $d $t")
                                    Double.NaN
                                } else {
                                    seconds
                                }
                            }
                            // If all timestamps fail to parse, fall through to dt=0.1 fallback
                            // rather than aborting the entire function.
                            val t0 = parsedTimes.filter { !it.isNaN() }.minOrNull()
                            if (t0 == null) {
                                null
                            } else {
                                parsedTimes.map { if (it.isNaN()) Double.NaN else it - t0 }
                                    .toDoubleArray()
                            }
                        } else null
                    } catch (e: Exception) {
                        logger.d(TAG, "Failed to parse Wheelog timestamps: ${e.message}")
                        null
                    }
                }

                val tSecFiltered: DoubleArray? = when (source) {
                    EUC_WORLD -> {
                        try {
                            if (EUCWorldColumns.TIMESTAMP.csv_code in df.columnNames()) {
                                // Parse tous les timestamps en OffsetDateTime
                                // EUC_WORLD_TS_FMTS handles both "+0200" and "+02:00" offsets
                                val times = df[EUCWorldColumns.TIMESTAMP.csv_code]
                                    .mapNotNull { raw ->
                                        parseTimestamp(raw)
                                    }

                                if (times.isEmpty()) {
                                    null
                                } else {
                                    val t0 = times.min()
                                    // Temps relatif en secondes pour les points filtrés
                                    filteredIndices.map { idx ->
                                        val raw = df[EUCWorldColumns.TIMESTAMP.csv_code][idx]
                                        if (raw == null) {
                                            Double.NaN
                                        } else {
                                            val t = parseTimestamp(raw)
                                            if (t == null) Double.NaN
                                            else t - t0
                                        }
                                    }.toDoubleArray()
                                }
                            } else {
                                null
                            }
                        } catch (e: Exception) {
                            logger.d(TAG, "Failed to parse EUC World timestamps: ${e.message}")
                            null
                        }
                    }


                    // TuxWheel v2 carries an authoritative epoch-ms receive time.
                    TUXWHEEL -> {
                        if (tuxV2 && TuxWheelV2Columns.TIMESTAMP.csv_code in df.columnNames()) {
                            try {
                                val parsedTimes = filteredIndices.map { idx ->
                                    val raw = df[TuxWheelV2Columns.TIMESTAMP.csv_code][idx]
                                    if (raw == null) {
                                        Double.NaN
                                    } else {
                                        parseTimestamp(raw) ?: Double.NaN
                                    }
                                }
                                val t0 = parsedTimes.filter { !it.isNaN() }.minOrNull()
                                if (t0 == null) {
                                    null
                                } else {
                                    parsedTimes.map { if (it.isNaN()) Double.NaN else it - t0 }
                                        .toDoubleArray()
                                }
                            } catch (e: Exception) {
                                logger.d(TAG, "Failed to parse TuxWheel v2 timestamps: ${e.message}")
                                null
                            }
                        } else {
                            // Legacy TuxWheel schema: WheelLog-style date + time columns.
                            wheelLogDateTimes()
                        }
                    }

                    WHEELLOG -> wheelLogDateTimes()

                    else -> null
                }

// Intégration trapèzes si timestamps disponibles, sinon fallback dt=0.1s
                val usingRealTimestamps =
                    tSecFiltered != null && tSecFiltered.count { !it.isNaN() } >= 2
                fallbackDtUsed = !usingRealTimestamps
                val i2dtRaw: Double =
                    if (usingRealTimestamps) {
                        var sum = 0.0
                        for (k in 0 until iPhaseAbs.size - 1) {
                            val dt = (tSecFiltered!![k + 1] - tSecFiltered[k]).coerceIn(0.01, 1.0)
                            sum += 0.5 * (iPhaseAbs[k] * iPhaseAbs[k] + iPhaseAbs[k + 1] * iPhaseAbs[k + 1]) * dt
                        }
                        sum
                    } else {
                        logger.d(
                            TAG,
                            "I²dt fallback: using dt=0.1s (timestamps unavailable or unparseable)"
                        )
                        iPhaseAbs.sumOf { it * it * 0.1 }
                    }

                val tTotal = tSecFiltered?.filter { !it.isNaN() }?.maxOrNull()
                iPhase2Int = if (tTotal != null && tTotal > 10.0) i2dtRaw / tTotal else i2dtRaw


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

        if (mosfetParams != null && tempBoardMax != null && (mosfetParams.rDsOn25cTotal != null)) {
            rMosfetHot = mosfetParams.rMosfetAtTemp(tempBoardMax)
            rBattMedian = maxOf(0.0, reqMedian - rMosfetHot)
            rBattMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(
                rBattMeasured = rBattMedian,
                tempMeasuredC = tempBoardMax,
                eaJPerMol = ea
            )
        }
        logger.d(TAG, "R Batt Median 25C:$rBattMedian25C")
        val firstDt = SourceDetection.getFirstDatetime(df, source)

        return FileStats(
            file = csvPath.substringAfterLast('/'),
            tooBig = false,
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
            rBattMedian25C = rBattMedian25C ?: rBattMedian,
            pwm95p = pwm95p,
            pwmMax = pwmMax,
            pwmMedian = pwmMedian,
            fallbackDtUsed = fallbackDtUsed
        )
    }
}
