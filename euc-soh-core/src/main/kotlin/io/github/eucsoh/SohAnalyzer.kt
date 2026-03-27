package io.github.eucsoh

import io.github.eucsoh.Constants.ANALYZING
import io.github.eucsoh.Constants.CALIBRATING
import io.github.eucsoh.Constants.DONE
import io.github.eucsoh.Constants.EUC_WORLD
import io.github.eucsoh.Constants.LOWER_REQ
import io.github.eucsoh.Constants.MIN_POINTS
import io.github.eucsoh.analysis.*
import io.github.eucsoh.Constants.Metrics
import io.github.eucsoh.Constants.Metrics.*
import io.github.eucsoh.Constants.MetaColumns.*
import io.github.eucsoh.Constants.WHEELLOG
import io.github.eucsoh.model.MOSFETParams
import io.github.eucsoh.model.PlotData
import io.github.eucsoh.model.ThresholdInfo
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlinx.coroutines.*

/**
 * Main orchestrator for SoH analysis.
 * Port of analyze_folder_for_req() from soh_core_en.py.
 */
class SohAnalyzer(
    private val csvSource: CsvSource? = null,
    private val mosfetParams: MOSFETParams? = null,
    private val logger: Logger = NoOpLogger
) {
    private val TAG: String = "SohAnalyzer"

    data class FileReport(
        val path: String,          // chemin complet original
        val fileName: String,      // basename
        val source: String?,       // "WheelLog" / "EUC World" / null
        val accepted: Boolean,
        val rejectionReason: String? = null,  // null si accepted
        val nPoints: Int? = null,
        val reqMedian: Double? = null,
        val wheelKm: Double? = null
    )

    data class AnalysisResult(
        val stats: DataFrame<*>,
        val alarms: List<GaussianAlarmDetector.Alarm>,
        val thresholds: Map<String, io.github.eucsoh.model.ThresholdInfo>,
        val eaJPerMol: Double,
        val nsGlobal: Int?,
        val vNominal: Double?,
        val rPackNominal: Double?,
        val plotData: PlotData,
        val fileReports: List<FileReport>,
        val macAddress:String?=null
    )

    data class SummaryData(
        val wheelName: String,
        val reqBand: ReqBand,
        val globalStats: GlobalStats,
        val pack: PackInfo,
        val socVoltageAvailable: Boolean,
        val battReqBand: ReqBand?,
        val arrhenius: ArrheniusInfo,
        val logs: List<Map<String, Any?>>
    ) {
        data class ReqBand(val low: Double, val high: Double)

        data class GlobalStats(
            val kmMin: Double,
            val kmMax: Double,
            val reqMedianMin: Double,
            val reqMedianMax: Double,
            val rBattMedianMin: Double?,
            val rBattMedianMax: Double?,
            val rMosfetHotMin: Double?,
            val rMosfetHotMax: Double?
        )

        data class PackInfo(
            val ns: Int?,
            val vNominal: Double?,
            val rPackNominal: Double?
        )

        data class ArrheniusInfo(
            val eaKjPerMol: Double,
            val autoCalibrated: Boolean
        )
    }


    /**
     * Analyzes all CSV files in a folder/list.
     * 
     * @param csvPaths List of CSV file paths
     * @param optimalFrac Fraction of best logs to use for baseline
     * @param eaJPerMol Arrhenius activation energy (null = auto-calibrate)
     */
    suspend fun analyzeFolderForReq(
        csvPaths: List<String>,
        optimalFrac: Double = 0.3,
        eaJPerMol: Double? = null,
        onProgress: ((current: Int, total: Int, phase: String) -> Unit)? = null,
        macAddress:String? = null
    ): AnalysisResult = coroutineScope {

        logger.d(TAG, "Starting analysis of ${csvPaths.size} files")
        val validCsvPath = mutableListOf<String>()
        val fileReports = mutableListOf<FileReport>()
        // Pass 1: Calibrate Ea if needed
        var ea = eaJPerMol
        if (ea == null) {
            logger.d(TAG, "Pass 1: Ea calibration")

            val tempStats =
                csvPaths.mapIndexedNotNull { idx, path ->
                    val filename = path.substringAfterLast('/')
                    logger.d(TAG, "  Processing [$idx/${csvPaths.size}] $filename")
                    onProgress?.invoke(idx, csvPaths.size, CALIBRATING)
                    try {
                        val result = ReqStatsComputer.computeReqStatsForFile(
                            csvPath = path,
                            csvSource = csvSource,
                            mosfetParams = mosfetParams,
                            eaJPerMol = null,
                            logger = logger
                        )
                        logger.d(
                            TAG,
                            "  [$idx] SUCCESS: ${result?.nPoints ?: MIN_POINTS} points, req=${result?.reqMedian ?: LOWER_REQ}"
                        )
                        if ((result?.reqMedian?: LOWER_REQ) > LOWER_REQ &&
                            (result?.tempBoardMax) != null &&
                            (result?.nPoints ?: MIN_POINTS) >= MIN_POINTS
                        ){
                            validCsvPath.add(path)

                            val fileName = path.substringAfterLast('/')
                            val source = result.source          // voir ci-dessous
                            fileReports.add(
                                FileReport(
                                    path, fileName, source, true,
                                    nPoints = result.nPoints,
                                    reqMedian = result.reqMedian,
                                    wheelKm = result.wheelKm
                                )
                            )
                        } else {
                            val fileName = path.substringAfterLast('/')
                            val source = result?.source       // voir ci-dessous
                            val fileReport = when {
                                (result?.nPoints ?: MIN_POINTS) < MIN_POINTS ->
                                    FileReport(
                                        path, fileName, source, false,
                                        rejectionReason = "Too few points (${result?.nPoints!!} < 50)",
                                        nPoints = result?.nPoints ?: 0
                                    )

                                (result?.reqMedian?: LOWER_REQ) <= LOWER_REQ ->
                                    FileReport(
                                        path, fileName, source, false,
                                        rejectionReason = "Req not computable (reqMedian = ${result?.reqMedian ?: LOWER_REQ})",
                                        nPoints = result?.nPoints ?: 0
                                    )

                                else ->
                                    throw Exception("Error when adding file report for file $path")
                            }
                            fileReports.add(fileReport)
                        }

                        result
                    } catch (e: Exception) {
                        logger.e(
                            TAG,
                            "  [$idx] FAILED: ${e.javaClass.simpleName}: ${e.message}",
                            e
                        )
                        fileReports.add(
                            FileReport(
                                path, path.substringAfterLast('/'), "Unknown", false,
                                rejectionReason = "File unreadable or parse error"
                            )
                        )
                        null
                    }
                }


            logger.d(TAG, "Pass 1: ${tempStats.size}/${csvPaths.size} files exploitable")

            if (tempStats.isEmpty()) {
                throw RuntimeException("No exploitable logs for calibration (0/${csvPaths.size} files readable)")
            }

            // Filter stats with critical null values for calibration
            val validTempStats = tempStats.filter { stat ->
                stat.reqMedian > LOWER_REQ && stat.tempBoardMax != null && stat.nPoints >= MIN_POINTS
            }

            logger.d(
                TAG,
                "Pass 1: ${validTempStats.size}/${tempStats.size} stats valid for calibration"
            )

            if (validTempStats.isEmpty()) {
                throw RuntimeException("No valid logs for calibration: all logs missing temperature or have insufficient data points")
            }

            val dfTemp = statsToDataFrame(validTempStats)
            ea = ArrheniusNormalizer.calibrateEaFromDataFrame(
                df = dfTemp,
                metric = REQ_MEDIAN.csv_code,
                tempCol = TEMP_BOARD_MAX.csv_code
            )

            logger.d(TAG, "Calibrated Ea: ${ea / 1000.0} kJ/mol")
        }

        // Pass 2: Final analysis with calibrated Ea
        logger.d(TAG, "Pass 2: Final analysis with Ea=${ea / 1000.0} kJ/mol")
        val finalPaths = when {
            eaJPerMol == null -> validCsvPath.toList()
            else -> csvPaths
        }


        val finalStats =
            finalPaths.mapIndexedNotNull { idx, path ->
                onProgress?.invoke(idx, finalPaths.size, ANALYZING)
                try {
                    ReqStatsComputer.computeReqStatsForFile(
                        csvPath = path,
                        csvSource = csvSource,
                        mosfetParams = mosfetParams,
                        eaJPerMol = ea,
                        logger = logger
                    )
                } catch (e: Exception) {
                    logger.e(TAG, "  Pass2 [$idx] FAILED: ${e.message}", e)
                    null
                }
            }

        onProgress?.invoke(finalPaths.size, finalPaths.size, DONE)
        logger.d(TAG, "Pass 2: ${finalStats.size}/${finalPaths.size} files processed")

        if (finalStats.isEmpty()) {
            throw RuntimeException("No exploitable logs in folder (0/${finalPaths.size} files readable)")
        }

        // Filter stats with minimum required data
        // Keep it as a safeguard even if prefiltering is applied at calibration
        val validStats = finalStats.filter { stat ->
            stat.reqMedian > LOWER_REQ && stat.nPoints >= MIN_POINTS
        }

        logger.d(TAG, "Final: ${validStats.size}/${finalStats.size} stats valid")

        if (validStats.isEmpty()) {
            throw RuntimeException("No valid logs after filtering (all logs have insufficient data points or invalid Req)")
        }

        var dfStats = statsToDataFrame(validStats)

        // Sort by datetime
        dfStats = dfStats.sortBy(DATETIME_FIRST.csv_code)

        // Pack inference
        val (nsGlobal, vNominal) = PackInference.inferPackConfig(dfStats)
        val rPackNominal = PackInference.computePackNominalResistance(nsGlobal, vNominal)

        logger.d(TAG, "Pack config: Ns=$nsGlobal, Vnom=$vNominal, Rpack=$rPackNominal")

        // Compute Req band (10th-90th percentile of optimal logs)
        val dfSorted = dfStats.sortBy(REQ_MEDIAN.csv_code)
        val nOpt = maxOf(3, (dfSorted.rowsCount() * optimalFrac).toInt())
        val dfOpt = dfSorted.take(nOpt)

        val reqBandLow = quantile(
            dfOpt[REQ_MEDIAN.csv_code].values().filterIsInstance<Number>()
                .map { it.toDouble() },
            0.10
        )
        val reqBandHigh = quantile(
            dfOpt[REQ_MEDIAN.csv_code].values().filterIsInstance<Number>()
                .map { it.toDouble() },
            0.90
        )

        // Add derived columns
        dfStats = dfStats.add("Req_band_low") { reqBandLow }
        dfStats = dfStats.add("Req_band_high") { reqBandHigh }
        dfStats = dfStats.add("Ns_global") { nsGlobal }
        dfStats = dfStats.add("v_nominal") { vNominal }
        dfStats = dfStats.add("R_pack_nominal") { rPackNominal }
        dfStats = dfStats.add("arrhenius_ea_j_per_mol") { ea }
        dfStats = dfStats.add("arrhenius_ea_kj_per_mol") { ea / 1000.0 }
        dfStats = dfStats.add("arrhenius_auto_calibrated") { eaJPerMol == null }

        // Compute thresholds
        val thresholds = GaussianAlarmDetector.computeThresholds(
            df = dfStats,
            optimalFrac = optimalFrac,
            nSigma = 2.0,
            useBattMetric = true,
            logger = logger
        )

        // Detect alarms
        val gaussianAlarms = GaussianAlarmDetector.detectAlarms(
            df = dfStats,
            thresholds = thresholds,
            checkAbsoluteLimit = true,
            rPackNominal = rPackNominal
        )

        // CUSUM alarms
        val cusumAlarms = mutableListOf<GaussianAlarmDetector.Alarm>()
        val kmMax = dfStats[WHEEL_KM.csv_code].values()
            .filterIsInstance<Number>()
            .maxOfOrNull { it.toDouble() } ?: 0.0
        val refKmMax = kmMax * 0.3
        val testKmMin = refKmMax

        for (metric in Constants.CUSUM_METRICS) {
            if (metric.csv_code !in dfStats.columnNames()) continue

            val cusumResult = CUSUMDetector.detectCUSUM(
                df = dfStats,
                metric = metric.csv_code,
                refKmMax = refKmMax,
                testKmMin = testKmMin
            )

            if (cusumResult.alarmIndices.isNotEmpty()) {
                val firstIdx = cusumResult.alarmIndices.first()
                cusumAlarms.add(
                    GaussianAlarmDetector.Alarm(
                        file = (dfStats[FILE.csv_code][firstIdx] as? String) ?: "unknown",
                        wheelKm = (dfStats[WHEEL_KM.csv_code][firstIdx] as? Number)?.toDouble(),
                        datetimeFirst = dfStats[DATETIME_FIRST.csv_code][firstIdx] as? String,
                        reasons = "Regime change detected on $metric (CUSUM): " +
                                "µ_ref=${cusumResult.muRef?.let { "%.4f".format(it) }}, " +
                                "σ_ref=${cusumResult.sigmaRef?.let { "%.4f".format(it) }}"
                    )
                )
            }
        }

        // Linear trend alarms
        val trendAlarms = mutableListOf<GaussianAlarmDetector.Alarm>()
        for (metric in Constants.TREND_METRICS) {
            if (metric.csv_code !in dfStats.columnNames()) continue

            val trendResult = TrendDetector.detectTrendLinear(
                df = dfStats,
                metric = metric.csv_code
            )

            if (trendResult.isSignificant && trendResult.slope != null) {
                val slopePer1000km = trendResult.slope * 1000.0
                trendAlarms.add(
                    GaussianAlarmDetector.Alarm(
                        file = "TREND_ANALYSIS",
                        wheelKm = kmMax,
                        datetimeFirst = dfStats[DATETIME_FIRST.csv_code][dfStats.rowsCount() - 1] as? String,
                        reasons = "Upward trend on $metric: +${"%.4f".format(slopePer1000km)}Ω/1000km " +
                                "(p=${"%.3f".format(trendResult.pValue)})"
                    )
                )
            }
        }

        val allAlarms = gaussianAlarms + cusumAlarms + trendAlarms

        logger.d(TAG, "Analysis complete: ${allAlarms.size} alarms detected")

        val plotData = buildPlotData(dfStats, thresholds)

        AnalysisResult(
            stats = dfStats,
            alarms = allAlarms,
            thresholds = thresholds,
            eaJPerMol = ea,
            nsGlobal = nsGlobal,
            vNominal = vNominal,
            rPackNominal = rPackNominal,
            plotData = plotData,
            fileReports = fileReports.toList(),
            macAddress=macAddress
        )

    }

    private fun buildPlotData(
        dfStats: DataFrame<*>,
        thresholds: Map<String, ThresholdInfo>
    ): PlotData {
        val series = mutableMapOf<Constants.Metrics, List<Pair<Double, Double>>>()
        val gaussianResults = mutableMapOf<Metrics, PlotData.GaussianPlotResult>()
        val cusumResults = mutableMapOf<Metrics, PlotData.CusumPlotResult>()
        val trendResults = mutableMapOf<Metrics, PlotData.TrendPlotResult>()
        val inflexionResults = mutableMapOf<Metrics, PlotData.InflexionPlotResult>()

        val kmMax = dfStats[WHEEL_KM.csv_code].values()
            .filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0
        val refKmMax = kmMax * 0.3

        for (metric in Metrics.entries) {
            if (metric.csv_code !in dfStats.columnNames()) continue

            // Série brute
            val pts = (0 until dfStats.rowsCount()).mapNotNull { i ->
                val km =
                    (dfStats[WHEEL_KM.csv_code][i] as? Number)?.toDouble()
                        ?: return@mapNotNull null
                val v =
                    (dfStats[metric.csv_code][i] as? Number)?.toDouble()
                        ?: return@mapNotNull null
                km to v
            }.sortedBy { it.first }
            if (pts.size < 5) continue
            series[metric] = pts

            val t = thresholds[metric.csv_code] ?: continue
            gaussianResults[metric] = PlotData.GaussianPlotResult(
                mu = t.mean,
                sigma = t.std,
                higherIsBad = metric.higher_is_bad
            )

            // CUSUM (métriques concernées uniquement)
            if (metric in Constants.CUSUM_METRICS) {
                val r = CUSUMDetector.detectCUSUM(
                    df = dfStats,
                    metric = metric.csv_code,
                    refKmMax = refKmMax,
                    testKmMin = refKmMax
                )
                if (r.muRef != null && r.sigmaRef != null) {
                    cusumResults[metric] = PlotData.CusumPlotResult(
                        alarmIndices = r.alarmIndices.toSet(),
                        muRef = r.muRef,
                        sigmaRef = r.sigmaRef,
                        hSigma = 5.0
                    )
                }
            }

            // Trend
            if (metric in Constants.TREND_METRICS) {
                val r = TrendDetector.detectTrendLinear(dfStats, metric.csv_code)
                if (r.slope != null) {
                    val xVals = pts.map { it.first }
                    val yVals = pts.map { it.second }
                    val n = xVals.size
                    val sx = xVals.sum();
                    val sy = yVals.sum()
                    val intercept = (sy - r.slope * sx) / n
                    trendResults[metric] = PlotData.TrendPlotResult(
                        slope = r.slope,
                        intercept = intercept,
                        isSignificant = r.isSignificant,
                        pValue = r.pValue
                    )
                }

                // Inflexion
                val ri = TrendDetector.detectSlopeInflexions(
                    df = dfStats,
                    metric = metric.csv_code,
                    thresholds = thresholds,       // déjà calculé plus haut
                    highIsBad = metric.higher_is_bad
                )
                val limit = thresholds[metric.csv_code]?.limit
                    ?: (pts.map { it.second }.average() + 1.25 * run {
                        val m = pts.map { it.second }.average()
                        kotlin.math.sqrt(pts.sumOf { (it.second - m) * (it.second - m) } / (pts.size - 1))
                    })
                inflexionResults[metric] = PlotData.InflexionPlotResult(
                    slowIndices = ri.slowIndices,
                    inflexionIndices = ri.inflexionIndices,
                    dangerLimit = limit
                )
            }
        }

        return PlotData(series, gaussianResults, cusumResults, trendResults, inflexionResults)
    }


    /**
     * Builds a platform-agnostic summary from analysis results.
     * Port of build_summary_dict() from soh_core_en.py.
     *
     * @param result Analysis results from analyzeFolderForReq()
     * @param wheelName Name/identifier of the wheel
     * @return Structured summary data ready for JSON export or UI display
     */
    fun buildSummary(
        result: AnalysisResult,
        wheelName: String
    ): SummaryData {
        val df = result.stats

        // Safe getters for DataFrame values
        fun getDoubleAt(col: String, row: Int): Double? {
            return (df[col][row] as? Number)?.toDouble()
        }

        fun getBooleanAt(col: String, row: Int): Boolean {
            return (df[col][row] as? Boolean) ?: false
        }

        return SummaryData(
            wheelName = wheelName,
            reqBand = SummaryData.ReqBand(
                low = getDoubleAt("Req_band_low", 0) ?: 0.0,
                high = getDoubleAt("Req_band_high", 0) ?: 0.0
            ),
            globalStats = SummaryData.GlobalStats(
                kmMin = df[WHEEL_KM.csv_code].values().filterIsInstance<Number>()
                    .minOfOrNull { it.toDouble() } ?: 0.0,
                kmMax = df[WHEEL_KM.csv_code].values().filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() } ?: 0.0,
                reqMedianMin = df[REQ_MEDIAN.csv_code].values().filterIsInstance<Number>()
                    .minOfOrNull { it.toDouble() } ?: 0.0,
                reqMedianMax = df[REQ_MEDIAN.csv_code].values().filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() } ?: 0.0,
                rBattMedianMin = if (R_BATT_MEDIAN_25C.csv_code in df.columnNames()) {
                    df[R_BATT_MEDIAN_25C.csv_code].values().filterIsInstance<Number>()
                        .minOfOrNull { it.toDouble() }
                } else null,
                rBattMedianMax = if (R_BATT_MEDIAN_25C.csv_code in df.columnNames()) {
                    df[R_BATT_MEDIAN_25C.csv_code].values().filterIsInstance<Number>()
                        .maxOfOrNull { it.toDouble() }
                } else null,
                rMosfetHotMin = if (R_MOSFET_HOT.csv_code in df.columnNames()) {
                    df[R_MOSFET_HOT.csv_code].values().filterIsInstance<Number>()
                        .minOfOrNull { it.toDouble() }
                } else null,
                rMosfetHotMax = if (R_MOSFET_HOT.csv_code in df.columnNames()) {
                    df[R_MOSFET_HOT.csv_code].values().filterIsInstance<Number>()
                        .maxOfOrNull { it.toDouble() }
                } else null
            ),
            pack = SummaryData.PackInfo(
                ns = result.nsGlobal,
                vNominal = result.vNominal,
                rPackNominal = result.rPackNominal
            ),
            socVoltageAvailable = df[SOC_REF_OK.csv_code].values()
                .filterIsInstance<Boolean>()
                .any { it },
            battReqBand = if ("R_batt_band_low" in df.columnNames() && "R_batt_band_high" in df.columnNames()) {
                val low = getDoubleAt("R_batt_band_low", 0)
                val high = getDoubleAt("R_batt_band_high", 0)
                if (low != null && high != null) {
                    SummaryData.ReqBand(low = low, high = high)
                } else null
            } else null,
            arrhenius = SummaryData.ArrheniusInfo(
                eaKjPerMol = result.eaJPerMol / 1000.0,
                autoCalibrated = getBooleanAt("arrhenius_auto_calibrated", 0)
            ),

            logs = (0 until df.rowsCount()).map { i ->
                df.columnNames().associateWith { col -> df[col][i] }
            }
        )
    }

    private fun statsToDataFrame(stats: List<ReqStatsComputer.FileStats>): DataFrame<*> {
        return dataFrameOf(
            FILE.csv_code to stats.map { it.file },
            SOURCE.csv_code to stats.map { it.source },
            DATETIME_FIRST.csv_code to stats.map { it.datetimeFirst },
            WHEEL_KM.csv_code to stats.map { it.wheelKm },
            WHEEL_KM_SOURCE.csv_code to stats.map { it.wheelKmSource },
            V_IDLE.csv_code to stats.map { it.vIdle },
            NS.csv_code to stats.map { it.ns },
            SOC_REF_OK.csv_code to stats.map { it.socRefOk },
            SOC_REF_V_FULL.csv_code to stats.map { it.socRefVFull },
            N_POINTS.csv_code to stats.map { it.nPoints },
            REQ_MEAN.csv_code to stats.map { it.reqMean },
            REQ_MEDIAN.csv_code to stats.map { it.reqMedian },
            REQ_MEDIAN_25C.csv_code to stats.map { it.reqMedian25C },
            REQ_95P.csv_code to stats.map { it.req95p },
            SAG_95P.csv_code to stats.map { it.sag95p },
            SAG_MAX.csv_code to stats.map { it.sagMax },
            V_MIN_STRONG.csv_code to stats.map { it.vMinStrong },
            I_MAX.csv_code to stats.map { it.iMax },
            I_95P.csv_code to stats.map { it.i95p },
            TEMP_BOARD_MAX.csv_code to stats.map { it.tempBoardMax },
            TEMP_MOTOR_MAX.csv_code to stats.map { it.tempMotorMax },
            I_PHASE2_INT.csv_code to stats.map { it.iPhase2Int },
            I_PHASE_MAX.csv_code to stats.map { it.iPhaseMax },
            I_PHASE_95P.csv_code to stats.map { it.iPhase95p },
            R_MOSFET_HOT.csv_code to stats.map { it.rMosfetHot },
            R_BATT_MEDIAN.csv_code to stats.map { it.rBattMedian },
            R_BATT_MEDIAN_25C.csv_code to stats.map { it.rBattMedian25C },
            PWM_95P.csv_code to stats.map { it.pwm95p },
            PWM_MAX.csv_code to stats.map { it.pwmMax }

        )
    }

    private fun quantile(values: List<Double>, q: Double): Double {
        val sorted = values.sorted()
        val idx = (sorted.size - 1) * q
        val low = kotlin.math.floor(idx).toInt()
        val high = kotlin.math.ceil(idx).toInt()
        if (low == high) return sorted[low]
        val w = idx - low
        return sorted[low] * (1 - w) + sorted[high] * w
    }
}
