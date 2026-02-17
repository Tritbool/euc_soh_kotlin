// Analyzer.kt
package com.euc.soh.analysis

import com.euc.soh.config.Constants
import com.euc.soh.io.FileProvider
import com.euc.soh.model.*
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Fournit des fonctions pour analyser une liste de LogData (par roue)
 * et produire les structures équivalentes à df_stats et df_alarms du code Python.
 */
object Analyzer {

    fun estimateCellResistanceMohm(vNom: Double?): Double {
        if (vNom == null) return 18.0
        return when {
            vNom < 80.0 -> 22.0
            vNom < 110.0 -> 18.0
            vNom < 150.0 -> 14.0
            else -> 12.0
        }
    }

    fun computePackNominalResistance(nsGlobal: Int?, vNom: Double?): Double? {
        if (nsGlobal == null) return null
        val rCellMohm = estimateCellResistanceMohm(vNom)
        return nsGlobal * rCellMohm / 1000.0
    }

    fun inferPackConfig(logs: List<LogData>): PackConfig {
        val nsSeries = logs.filter { it.socRefOk && it.nsSeries != null }
            .mapNotNull { it.nsSeries }
        var nsGlobal: Int? = null
        var vNom: Double? = null
        if (nsSeries.isNotEmpty()) {
            val median = nsSeries.sorted().let { arr -> arr[arr.size / 2] }
            if (median in Constants.NS_MIN..Constants.NS_MAX) {
                // choose nearest known series
                nsGlobal = Constants.KNOWN_SERIES.minByOrNull { kotlin.math.abs(it - median) }
                vNom = nsGlobal?.let { it * Constants.NOMINAL_CELL_V }
            }
        }
        return PackConfig(nsGlobal, vNom)
    }

    private fun quantile(values: List<Double>, q: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val s = values.sorted()
        val idx = (s.size - 1) * q
        val low = kotlin.math.floor(idx).toInt()
        val high = kotlin.math.ceil(idx).toInt()
        if (low == high) return s[low]
        val w = idx - low
        return s[low] * (1 - w) + s[high] * w
    }

    fun computeWheelStatisticsFromLogs(
        wheelName: String,
        logs: List<LogData>,
        optimalFrac: Double = 0.5,
        mosfetParams: MOSFETParams? = null,
        eaJPerMol: Double? = null
    ): WheelStatistics {
        if (logs.isEmpty()) return WheelStatistics(wheelName, emptyList(), null, null, null, 0.0, 0.0, null, null, 20000.0, false)

        // 1) Compute or calibrate Ea
        val ea = eaJPerMol ?: ArrheniusNormalizer.calibrateEaFromLogs(logs)

        // 2) Enrich logs with Arrhenius-normalized values and MOSFET split
        val enriched = logs.map { log ->
            val reqMedian = log.reqMedian
            val tempBoard = log.tempBoardMax
            val reqMedian25C = if (!reqMedian.isNaN()) ArrheniusNormalizer.normalizeRBattTo25C(reqMedian, tempBoard, ea) else Double.NaN

            var rMosfetHot: Double? = null
            var rBattMedian: Double? = null
            var rBattMedian25C: Double? = null
            if (mosfetParams != null && tempBoard != null) {
                rMosfetHot = mosfetParams.rMosfetAtTemp(tempBoard)
                val battMedian = kotlin.math.max(0.0, reqMedian - rMosfetHot)
                rBattMedian = battMedian
                rBattMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(battMedian, tempBoard, ea)
            }

            log.copy(
                reqMedian25C = reqMedian25C,
                rBattMedian = rBattMedian,
                rBattMedian25C = rBattMedian25C,
                rMosfetHot = rMosfetHot
            )
        }

        // 3) Pack inference and nominal resistance
        val pack = inferPackConfig(enriched)
        val rPackNom = computePackNominalResistance(pack.nsGlobal, pack.vNominal)

        // 4) Compute Req band using Req_median_25C if present
        val metricValuesForReqMedian25C = enriched.mapNotNull { v ->
            val x = v.reqMedian25C
            if (x.isNaN()) null else x
        }

        val metricForBand = if (metricValuesForReqMedian25C.size >= 3) metricValuesForReqMedian25C else enriched.mapNotNull { if (!it.reqMedian.isNaN()) it.reqMedian else null }

        val sortedForBand = metricForBand.sorted()
        val nOpt = kotlin.math.max(1, (sortedForBand.size * optimalFrac).toInt())
        val optVals = sortedForBand.take(nOpt)

        val reqBandLow = quantile(optVals, 0.10)
        val reqBandHigh = quantile(optVals, 0.90)

        // 5) R_batt band if available
        val rBattEff = enriched.mapNotNull { it.rBattMedian25C ?: it.rBattMedian }
            .filter { it != null }
            .map { it!! }
        val rBattBandLow = if (rBattEff.isNotEmpty()) quantile(rBattEff.sorted(), 0.10) else null
        val rBattBandHigh = if (rBattEff.isNotEmpty()) quantile(rBattEff.sorted(), 0.90) else null

        // 6) thresholds via Gaussian
        val thresholds = GaussianAlarmDetector.computeThresholds(enriched, optimalFrac = optimalFrac, nSigma = 2.0, useBattMetric = true)

        // 7) alarms: gaussian
        val gaussianAlarms = GaussianAlarmDetector.detectAlarms(enriched, thresholds, checkAbsoluteLimit = true, rPackNominal = rPackNom)

        // 8) CUSUM alarms for configured metrics
        val cusumExtraAlarms = mutableListOf<Alarm>()
        val kmMax = enriched.mapNotNull { it.wheelKm }.maxOrNull() ?: 0.0
        val refKmMax = kmMax * 0.3
        val testKmMin = refKmMax

        for (m in Constants.CUSUM_METRICS) {
            // Ensure metric present
            val valuesPresent = enriched.any { it.getMetric(m) != null }
            if (!valuesPresent) continue

            val cusumResult = CUSUMDetector.detectCUSUM(enriched, metric = m, refKmMax = refKmMax, testKmMin = testKmMin)
            if (cusumResult.alarmIndices.isNotEmpty()) {
                // pick first alarm index and create a human-readable alarm similar to Python
                val firstIdx = cusumResult.alarmIndices[0]
                val log = enriched.getOrNull(firstIdx)
                if (log != null) {
                    val reason = "Regime change detected on $m (CUSUM): µ_ref=${cusumResult.muRef ?: Double.NaN}, σ_ref=${cusumResult.sigmaRef ?: Double.NaN}, triggering log ≈ ${log.wheelKm ?: 0.0} km"
                    cusumExtraAlarms.add(Alarm(fileName = log.fileName, wheelKm = log.wheelKm, datetimeFirst = log.datetimeFirst, reasons = reason))
                }
            }
        }

        val allAlarms = (gaussianAlarms + cusumExtraAlarms).toList()

        // 9) Build WheelStatistics
        val wheelStats = WheelStatistics(
            wheelName = wheelName,
            logs = enriched.sortedWith(compareBy(nullsLast()) { it.wheelKm }),
            nsGlobal = pack.nsGlobal,
            vNominal = pack.vNominal,
            rPackNominal = rPackNom,
            reqBandLow = reqBandLow,
            reqBandHigh = reqBandHigh,
            rBattBandLow = rBattBandLow,
            rBattBandHigh = rBattBandHigh,
            arrheniusEaKJperMol = ea / 1000.0,
            arrheniusAutoCalibrated = eaJPerMol == null,
            alarms = allAlarms,
            thresholds = thresholds
        )

        return wheelStats
    }

    /**
     * Analyse un CSV (chemin) et retourne les statistiques équivalentes à compute_req_stats_for_file (FileStats)
     * Cette fonction est volontairement tolérante et supporte des CSV simples pour les tests.
     */
    fun analyzeLogForReq(csvPath: String, eaJPerMol: Double? = null): FileStats? {
        val file = File(csvPath)
        if (!file.exists() || !file.isFile) return null

        val rowsMap = try {
            csvReader().readAllWithHeader(file)
        } catch (e: Exception) {
            return null
        }

        if (rowsMap.isEmpty()) return null

        // Helper to get header-insensitive keys
        val rawHeaders = rowsMap.first().keys.toList()

        fun pick(keyCandidates: List<String>): String? {
            for (candidate in keyCandidates) {
                val found = rawHeaders.firstOrNull { it.trim().equals(candidate, ignoreCase = true) }
                if (found != null) return found
            }
            return null
        }

        val colDatetime = pick(listOf("datetime", "date"))
        val colDistanceTotal = pick(listOf("distance_total", "totaldistance"))
        val colVoltage = pick(listOf("voltage"))
        val colCurrent = pick(listOf("current"))
        val colSpeed = pick(listOf("speed"))
        val colTempBoard = pick(listOf("system_temp", "temp"))
        val colTempMotor = pick(listOf("temp_motor", "temp2"))
        val colBattery = pick(listOf("battery_level", "battery", "soc"))

        val voltages = mutableListOf<Double>()
        val currents = mutableListOf<Double>()
        val speeds = mutableListOf<Double>()
        val tempsBoard = mutableListOf<Double?>()
        val tempsMotor = mutableListOf<Double?>()
        val datetimes = mutableListOf<String?>()
        val distances = mutableListOf<Double?>()

        for (row in rowsMap) {
            fun getDouble(ci: String?): Double? = ci?.let { row[it]?.toDoubleOrNull() }
            fun getString(ci: String?): String? = ci?.let { row[it] }

            voltages.add(getDouble(colVoltage) ?: Double.NaN)
            currents.add(getDouble(colCurrent) ?: Double.NaN)
            speeds.add(getDouble(colSpeed) ?: Double.NaN)
            tempsBoard.add(getDouble(colTempBoard))
            tempsMotor.add(getDouble(colTempMotor))
            datetimes.add(getString(colDatetime))
            distances.add(getDouble(colDistanceTotal))
        }

        // compute wheel_km from distances if available
        val wheelKm = distances.filterNotNull().maxOrNull()?.let { d ->
            // if header was totaldistance (wheellog) in meters, try to detect via header name
            val usesMeters = rawHeaders.any { it.equals("totaldistance", ignoreCase = true) }
            if (usesMeters) d / 1000.0 else d
        }

        // v_idle global: use points with low current abs < 3 A
        val lowIdx = currents.mapIndexedNotNull { i, v -> if (!v.isNaN() && kotlin.math.abs(v) < 3.0) i else null }
        val vIdleGlobal = if (lowIdx.isNotEmpty()) {
            val vs = lowIdx.map { voltages[it] }.filter { !it.isNaN() }
            if (vs.isNotEmpty()) quantile(vs, 0.95) else voltages.filter { !it.isNaN() }.maxOrNull() ?: Double.NaN
        } else {
            voltages.filter { !it.isNaN() }.maxOrNull() ?: Double.NaN
        }

        // choose current window
        val (iMinDef, iMaxDef) = ResistanceCalculator.chooseBatteryCurrentWindow(null)
        var I_min = iMinDef
        var I_max = iMaxDef

        // filter points for Req calculation
        fun isPointValid(i: Int): Boolean {
            val sp = speeds.getOrNull(i) ?: Double.NaN
            val cur = currents.getOrNull(i) ?: Double.NaN
            if (sp.isNaN() || cur.isNaN()) return false
            val speedOk = sp > 20.0
            val curOk = kotlin.math.abs(cur) >= I_min && kotlin.math.abs(cur) <= I_max
            return speedOk && curOk
        }

        var idxs = (0 until voltages.size).filter { isPointValid(it) }
        if (idxs.size < 50) {
            I_min *= 0.7
            I_max *= 1.3
            idxs = (0 until voltages.size).filter { isPointValid(it) }
        }

        if (idxs.isEmpty()) return null

        val sags = idxs.map { i ->
            val v = voltages[i]
            val cur = currents[i]
            val sag = vIdleGlobal - v
            Pair(sag, kotlin.math.abs(cur))
        }.filter { (sag, cur) -> !sag.isNaN() && !cur.isNaN() && cur > 0.0 }

        if (sags.isEmpty()) return null

        val reqs = sags.map { (sag, cur) -> sag / cur }
        val reqMean = reqs.average()
        val reqMedian = quantile(reqs.sorted(), 0.5)
        val req95p = quantile(reqs.sorted(), 0.95)
        val sagValues = sags.map { it.first }.sorted()
        val sag95p = quantile(sagValues, 0.95)
        val sagMax = sagValues.maxOrNull() ?: Double.NaN
        val vMinStrong = voltages.filter { !it.isNaN() }.minOrNull() ?: Double.NaN
        val iAbs = currents.map { kotlin.math.abs(it) }.filter { !it.isNaN() }
        val iMax = iAbs.maxOrNull() ?: Double.NaN
        val i95p = quantile(iAbs.sorted(), 0.95)

        val tempBoardMax = tempsBoard.filterNotNull().maxOrNull()
        val tempMotorMax = tempsMotor.filterNotNull().maxOrNull()

        val firstDt = datetimes.firstOrNull()

        val nPoints = idxs.size

        // compute Req_median_25C using provided ea or default
        val eaVal = eaJPerMol ?: 20000.0
        val reqMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(reqMedian, tempBoardMax, eaVal)

        // Basic Ns estimation from v_idle_global
        val nsEst = if (!vIdleGlobal.isNaN() && vIdleGlobal > 0.0) {
            kotlin.math.round(vIdleGlobal / 4.2).toInt().takeIf { it in Constants.NS_MIN..Constants.NS_MAX }
        } else null

        // Build FileStats (lighter than LogData)
        val fileStats = FileStats(
            fileName = file.name,
            datetimeFirst = firstDt,
            wheelKm = wheelKm,
            vIdle = vIdleGlobal,
            nsSeries = nsEst,
            nPoints = nPoints,
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
            tempMotorMax = tempMotorMax
        )

        return fileStats
    }

    /**
     * Analyse tous les CSV fournis par le FileProvider et agrège les statistiques en une WheelStatistics
     * similaire à analyze_folder_for_req du code Python.
     * Par défaut exécution séquentielle (low-memory). Peut être parallélisé en passant parallel=true.
     */
    fun analyzeFolderForReq(
        wheelName: String,
        provider: FileProvider,
        optimalFrac: Double = 0.5,
        mosfetParams: MOSFETParams? = null,
        eaJPerMol: Double? = null,
        parallel: Boolean = false,
        maxConcurrency: Int? = null
    ): WheelStatistics {
        val files = provider.getFiles()
        val fileStats = mutableListOf<FileStats>()

        if (!parallel) {
            for (p in files) {
                val fs = analyzeLogForReq(p, eaJPerMol)
                if (fs != null) fileStats.add(fs)
            }
        } else {
            // parallel execution using coroutines with a semaphore to limit concurrency
            val concurrency = maxConcurrency ?: (maxOf(1, Runtime.getRuntime().availableProcessors() - 1))
            runBlocking {
                val sem = Semaphore(concurrency)
                val deferred = files.map { path ->
                    async(Dispatchers.IO) {
                        sem.withPermit {
                            analyzeLogForReq(path, eaJPerMol)
                        }
                    }
                }
                val results = deferred.awaitAll()
                for (r in results) if (r != null) fileStats.add(r)
            }
        }

        if (fileStats.isEmpty()) throw RuntimeException("No exploitable log for R_eq in this provider.")

        // Convert FileStats -> LogData for aggregation
        val logs = fileStats.map { fs ->
            LogData(
                fileName = fs.fileName,
                source = if (fs.wheelKm != null) LogSource.EUC_WORLD else LogSource.WHEELLOG,
                datetimeFirst = fs.datetimeFirst,
                wheelKm = fs.wheelKm,
                wheelKmSource = if (fs.wheelKm != null) "distance_inferred" else null,
                vIdle = fs.vIdle,
                nsSeries = fs.nsSeries,
                socRefOk = false,
                socRefVFull = null,
                nPoints = fs.nPoints,
                reqMean = fs.reqMean,
                reqMedian = fs.reqMedian,
                reqMedian25C = fs.reqMedian25C,
                req95p = fs.req95p,
                sag95p = fs.sag95p,
                sagMax = fs.sagMax,
                vMinStrong = fs.vMinStrong,
                iMax = fs.iMax,
                i95p = fs.i95p,
                tempBoardMax = fs.tempBoardMax,
                tempMotorMax = fs.tempMotorMax,
                iPhase2Int = null,
                iPhaseMax = null,
                iPhase95p = null,
                rBattMedian = null,
                rBattMedian25C = null,
                rMosfetHot = null
            )
        }

        // delegate to existing aggregation
        return computeWheelStatisticsFromLogs(wheelName, logs, optimalFrac, mosfetParams, eaJPerMol)
    }
}
