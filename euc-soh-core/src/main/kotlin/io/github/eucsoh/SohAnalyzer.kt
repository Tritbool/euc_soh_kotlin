package io.github.eucsoh

import io.github.eucsoh.analysis.*
import io.github.eucsoh.model.MOSFETParams
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

    data class AnalysisResult(
        val stats: DataFrame<*>,
        val alarms: List<GaussianAlarmDetector.Alarm>,
        val thresholds: Map<String, io.github.eucsoh.model.ThresholdInfo>,
        val eaJPerMol: Double,
        val nsGlobal: Int?,
        val vNominal: Double?,
        val rPackNominal: Double?
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
     * @param parallel Enable parallel processing
     */
    suspend fun analyzeFolderForReq(
        csvPaths: List<String>,
        optimalFrac: Double = 0.3,
        eaJPerMol: Double? = null,
        parallel: Boolean = false
    ): AnalysisResult = coroutineScope {

        logger.d("SohAnalyzer", "Starting analysis of ${csvPaths.size} files")

        // Pass 1: Calibrate Ea if needed
        var ea = eaJPerMol
        if (ea == null) {
            logger.d("SohAnalyzer", "Pass 1: Ea calibration")
            
            val tempStats = if (parallel) {
                csvPaths.mapIndexed { idx, path ->
                    async(Dispatchers.IO) {
                        val filename = path.substringAfterLast('/')
                        logger.d("SohAnalyzer", "  Processing [$idx/${ csvPaths.size}] $filename")
                        try {
                            val result = ReqStatsComputer.computeReqStatsForFile(
                                csvPath = path,
                                csvSource = csvSource,
                                mosfetParams = mosfetParams,
                                eaJPerMol = null
                            )
                            logger.d("SohAnalyzer", "  [$idx] SUCCESS: ${result?.nPoints?:0.0} points, req=${result?.reqMedian?:0.0}")
                            result
                        } catch (e: Exception) {
                            logger.e("SohAnalyzer", "  [$idx] FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            } else {
                csvPaths.mapIndexedNotNull { idx, path ->
                    val filename = path.substringAfterLast('/')
                    logger.d("SohAnalyzer", "  Processing [$idx/${csvPaths.size}] $filename")
                    try {
                        val result = ReqStatsComputer.computeReqStatsForFile(
                            csvPath = path,
                            csvSource = csvSource,
                            mosfetParams = mosfetParams,
                            eaJPerMol = null
                        )
                        logger.d("SohAnalyzer", "  [$idx] SUCCESS: ${result?.nPoints?:0.0} points, req=${result?.reqMedian?:0.0}")
                        result
                    } catch (e: Exception) {
                        logger.e("SohAnalyzer", "  [$idx] FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                        null
                    }
                }
            }

            logger.d("SohAnalyzer", "Pass 1: ${tempStats.size}/${csvPaths.size} files exploitable")

            if (tempStats.isEmpty()) {
                throw RuntimeException("No exploitable logs for calibration (0/${csvPaths.size} files readable)")
            }

            // Filter stats with critical null values for calibration
            val validTempStats = tempStats.filter { stat ->
                stat.reqMedian > 0.0 && stat.tempBoardMax != null && stat.nPoints >= 50
            }

            logger.d("SohAnalyzer", "Pass 1: ${validTempStats.size}/${tempStats.size} stats valid for calibration")

            if (validTempStats.isEmpty()) {
                throw RuntimeException("No valid logs for calibration: all logs missing temperature or have insufficient data points")
            }

            val dfTemp = statsToDataFrame(validTempStats)
            ea = ArrheniusNormalizer.calibrateEaFromDataFrame(
                df = dfTemp,
                metric = "Req_median",
                tempCol = "temp_board_max"
            )
            
            logger.d("SohAnalyzer", "Calibrated Ea: ${ea / 1000.0} kJ/mol")
        }

        // Pass 2: Final analysis with calibrated Ea
        logger.d("SohAnalyzer", "Pass 2: Final analysis with Ea=${ea / 1000.0} kJ/mol")
        
        val finalStats = if (parallel) {
            csvPaths.mapIndexed { idx, path ->
                async(Dispatchers.IO) {
                    try {
                        ReqStatsComputer.computeReqStatsForFile(
                            csvPath = path,
                            csvSource = csvSource,
                            mosfetParams = mosfetParams,
                            eaJPerMol = ea
                        )
                    } catch (e: Exception) {
                        logger.e("SohAnalyzer", "  Pass2 [$idx] FAILED: ${e.message}", e)
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        } else {
            csvPaths.mapIndexedNotNull { idx, path ->
                try {
                    ReqStatsComputer.computeReqStatsForFile(
                        csvPath = path,
                        csvSource = csvSource,
                        mosfetParams = mosfetParams,
                        eaJPerMol = ea
                    )
                } catch (e: Exception) {
                    logger.e("SohAnalyzer", "  Pass2 [$idx] FAILED: ${e.message}", e)
                    null
                }
            }
        }

        logger.d("SohAnalyzer", "Pass 2: ${finalStats.size}/${csvPaths.size} files processed")

        if (finalStats.isEmpty()) {
            throw RuntimeException("No exploitable logs in folder (0/${csvPaths.size} files readable)")
        }

        // Filter stats with minimum required data
        val validStats = finalStats.filter { stat ->
            stat.reqMedian > 0.0 && stat.nPoints >= 50
        }

        logger.d("SohAnalyzer", "Final: ${validStats.size}/${finalStats.size} stats valid")

        if (validStats.isEmpty()) {
            throw RuntimeException("No valid logs after filtering (all logs have insufficient data points or invalid Req)")
        }

        var dfStats = statsToDataFrame(validStats)

        // Sort by datetime
        dfStats = dfStats.sortBy("datetime_first")

        // Pack inference
        val (nsGlobal, vNominal) = PackInference.inferPackConfig(dfStats)
        val rPackNominal = PackInference.computePackNominalResistance(nsGlobal, vNominal)

        logger.d("SohAnalyzer", "Pack config: Ns=$nsGlobal, Vnom=$vNominal, Rpack=$rPackNominal")

        // Compute Req band (10th-90th percentile of optimal logs)
        val dfSorted = dfStats.sortBy("Req_median")
        val nOpt = maxOf(3, (dfSorted.rowsCount() * optimalFrac).toInt())
        val dfOpt = dfSorted.take(nOpt)

        val reqBandLow = quantile(
            dfOpt["Req_median_25C"].values().filterIsInstance<Number>().map { it.toDouble() },
            0.10
        )
        val reqBandHigh = quantile(
            dfOpt["Req_median_25C"].values().filterIsInstance<Number>().map { it.toDouble() },
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
            useBattMetric = true
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
        val kmMax = dfStats["wheel_km"].values()
            .filterIsInstance<Number>()
            .maxOfOrNull { it.toDouble() } ?: 0.0
        val refKmMax = kmMax * 0.3
        val testKmMin = refKmMax

        for (metric in Constants.CUSUM_METRICS) {
            if (metric !in dfStats.columnNames()) continue

            val cusumResult = CUSUMDetector.detectCUSUM(
                df = dfStats,
                metric = metric,
                refKmMax = refKmMax,
                testKmMin = testKmMin
            )

            if (cusumResult.alarmIndices.isNotEmpty()) {
                val firstIdx = cusumResult.alarmIndices.first()
                cusumAlarms.add(
                    GaussianAlarmDetector.Alarm(
                        file = (dfStats["file"][firstIdx] as? String) ?: "unknown",
                        wheelKm = (dfStats["wheel_km"][firstIdx] as? Number)?.toDouble(),
                        datetimeFirst = dfStats["datetime_first"][firstIdx] as? String,
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
            if (metric !in dfStats.columnNames()) continue

            val trendResult = TrendDetector.detectTrendLinear(
                df = dfStats,
                metric = metric
            )

            if (trendResult.isSignificant && trendResult.slope != null) {
                val slopePer1000km = trendResult.slope * 1000.0
                trendAlarms.add(
                    GaussianAlarmDetector.Alarm(
                        file = "TREND_ANALYSIS",
                        wheelKm = kmMax,
                        datetimeFirst = dfStats["datetime_first"][dfStats.rowsCount() - 1] as? String,
                        reasons = "Upward trend on $metric: +${"%.4f".format(slopePer1000km)}Ω/1000km " +
                                "(p=${"%.3f".format(trendResult.pValue)})"
                    )
                )
            }
        }

        val allAlarms = gaussianAlarms + cusumAlarms + trendAlarms

        logger.d("SohAnalyzer", "Analysis complete: ${allAlarms.size} alarms detected")

        AnalysisResult(
            stats = dfStats,
            alarms = allAlarms,
            thresholds = thresholds,
            eaJPerMol = ea,
            nsGlobal = nsGlobal,
            vNominal = vNominal,
            rPackNominal = rPackNominal
        )
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
                kmMin = df["wheel_km"].values().filterIsInstance<Number>().minOfOrNull { it.toDouble() } ?: 0.0,
                kmMax = df["wheel_km"].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0,
                reqMedianMin = df["Req_median"].values().filterIsInstance<Number>().minOfOrNull { it.toDouble() } ?: 0.0,
                reqMedianMax = df["Req_median"].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() } ?: 0.0,
                rBattMedianMin = if ("R_batt_median_25C" in df.columnNames()) {
                    df["R_batt_median_25C"].values().filterIsInstance<Number>().minOfOrNull { it.toDouble() }
                } else null,
                rBattMedianMax = if ("R_batt_median_25C" in df.columnNames()) {
                    df["R_batt_median_25C"].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
                } else null,
                rMosfetHotMin = if ("R_mosfet_hot" in df.columnNames()) {
                    df["R_mosfet_hot"].values().filterIsInstance<Number>().minOfOrNull { it.toDouble() }
                } else null,
                rMosfetHotMax = if ("R_mosfet_hot" in df.columnNames()) {
                    df["R_mosfet_hot"].values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
                } else null
            ),
            pack = SummaryData.PackInfo(
                ns = result.nsGlobal,
                vNominal = result.vNominal,
                rPackNominal = result.rPackNominal
            ),
            socVoltageAvailable = df["soc_ref_ok"].values()
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
            "file" to stats.map { it.file },
            "source" to stats.map { it.source },
            "datetime_first" to stats.map { it.datetimeFirst },
            "wheel_km" to stats.map { it.wheelKm },
            "wheel_km_source" to stats.map { it.wheelKmSource },
            "v_idle" to stats.map { it.vIdle },
            "Ns" to stats.map { it.ns },
            "soc_ref_ok" to stats.map { it.socRefOk },
            "soc_ref_v_full" to stats.map { it.socRefVFull },
            "n_points" to stats.map { it.nPoints },
            "Req_mean" to stats.map { it.reqMean },
            "Req_median" to stats.map { it.reqMedian },
            "Req_median_25C" to stats.map { it.reqMedian25C },
            "Req_95p" to stats.map { it.req95p },
            "sag_95p" to stats.map { it.sag95p },
            "sag_max" to stats.map { it.sagMax },
            "v_min_strong" to stats.map { it.vMinStrong },
            "i_max" to stats.map { it.iMax },
            "i_95p" to stats.map { it.i95p },
            "temp_board_max" to stats.map { it.tempBoardMax },
            "temp_motor_max" to stats.map { it.tempMotorMax },
            "I_phase2_int" to stats.map { it.iPhase2Int },
            "i_phase_max" to stats.map { it.iPhaseMax },
            "i_phase_95p" to stats.map { it.iPhase95p },
            "R_mosfet_hot" to stats.map { it.rMosfetHot },
            "R_batt_median" to stats.map { it.rBattMedian },
            "R_batt_median_25C" to stats.map { it.rBattMedian25C }
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
