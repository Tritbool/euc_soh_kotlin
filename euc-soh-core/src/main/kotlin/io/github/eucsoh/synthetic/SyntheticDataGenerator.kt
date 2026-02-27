package io.github.eucsoh.synthetic

import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.analysis.GaussianAlarmDetector
import io.github.eucsoh.model.ThresholdInfo
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.maxBy
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.random.Random

/**
 * Synthetic EUC data generator for testing SoH analysis.
 * Port of synth_en.py from EUC_SOH project.
 * 
 * Generates realistic degradation scenarios with:
 * - Baseline noise around thresholds
 * - Slow initial drift
 * - Accelerated degradation after "knee" point
 * - Multiple failure modes (battery, MOSFET, global)
 * 
 * Supports two modes:
 * 1. Pure synthetic: provide thresholds manually (default, for unit tests)
 * 2. From real logs: analyze real data folder and extrapolate degradation
 */
object SyntheticDataGenerator {

    private val METRICS = listOf(
        "Req_median", "Req_95p", "sag_95p", "sag_max", "v_min_strong",
        "i_max", "i_95p", "i_phase_95p", "i_phase_max", "I_phase2_int",
        "temp_board_max", "temp_motor_max", "R_batt_median", "R_mosfet_hot"
    )

    private val BATT_METRICS = setOf(
        "Req_median", "Req_95p", "R_batt_median", "sag_95p", "sag_max"
    )

    private val MOSFET_METRICS = setOf(
        "R_mosfet_hot", "temp_board_max", "i_phase_95p", "i_phase_max", "I_phase2_int"
    )

    enum class DegradationMode {
        GLOBAL,       // All metrics drift
        BATT_ONLY,    // Mainly battery degradation
        MOSFET_ONLY   // Mainly MOSFET/thermal degradation
    }

    data class SyntheticConfig(
        val years: Int = 3,
        val kmPerWeek: Double = 100.0,
        val kneeFrac: Double = 0.90,
        val mode: DegradationMode = DegradationMode.GLOBAL,
        val finalOffsetSigma: Double = 2.0,
        val noiseFrac: Double = 0.3,
        val wheelName: String = "synthetic",
        val vIdle: Double = 134.0,
        val startKm: Double = 0.0
    )

    /**
     * Real SoH data loaded from actual logs.
     * Equivalent to Python's load_real_soh() return value.
     */
    data class RealSoh(
        val stats: DataFrame<*>,
        val thresholds: Map<String, ThresholdInfo>,
        val maxKm: Double
    )

    /**
     * Loads and analyzes real logs to extract stats and thresholds.
     * Equivalent to Python's load_real_soh(folder).
     * 
     * @param folder Directory containing real CSV logs
     * @param optimalFrac Fraction of best logs to use for threshold computation
     * @return RealSoh with stats, thresholds, and max km
     */
    suspend fun loadRealSoh(
        folder: File,
        optimalFrac: Double = 0.3
    ): RealSoh {
        val csvFiles = folder.listFiles()?.filter { it.extension == "csv" } ?: emptyList()
        require(csvFiles.isNotEmpty()) { "No CSV files found in ${folder.absolutePath}" }

        val csvPaths = csvFiles.map { it.absolutePath }.sorted()

        val analyzer = SohAnalyzer()
        val result = analyzer.analyzeFolderForReq(
            csvPaths = csvPaths,
            optimalFrac = optimalFrac,
            parallel = false
        )

        val stats = result.stats
        val thresholds = GaussianAlarmDetector.computeThresholds(
            df = stats,
            optimalFrac = optimalFrac,
            nSigma = 2.0
        )

        // Extract max km from stats
        val maxKmRow = stats.maxBy { (it["wheel_km"] as? Number)?.toDouble() ?: 0.0 }
        val maxKm = (maxKmRow?.get("wheel_km") as? Number)?.toDouble() ?: 0.0

        return RealSoh(
            stats = stats,
            thresholds = thresholds,
            maxKm = maxKm
        )
    }

    /**
     * Generates synthetic SoH time series from real data.
     * Equivalent to Python's generate_soh_timeseries(df_stats, thresholds, ...).
     * 
     * @param real Real SoH data from loadRealSoh()
     * @param years Number of years to simulate
     * @param wheelName Name prefix for generated files
     * @param mode Degradation mode
     * @param kmPerWeek Average km per week
     * @param kneeFrac Fraction of lifespan before accelerated degradation
     * @return List of synthetic log metadata
     */
    fun generateSohTimeseriesFromReal(
        real: RealSoh,
        years: Int = 3,
        wheelName: String = "synthetic_wheel",
        mode: DegradationMode = DegradationMode.GLOBAL,
        kmPerWeek: Double = 100.0,
        kneeFrac: Double = 0.90
    ): List<Map<String, Any?>> {
        val config = SyntheticConfig(
            years = years,
            kmPerWeek = kmPerWeek,
            kneeFrac = kneeFrac,
            mode = mode,
            wheelName = wheelName,
            vIdle = 134.0,  // Will be overridden if needed
            startKm = real.maxKm
        )

        return generateSohTimeseries(
            thresholds = real.thresholds,
            config = config
        )
    }

    /**
     * Generates complete synthetic folder from real logs.
     * Equivalent to Python's generate_synthetic_folder(input_folder, output_folder, ...).
     * 
     * @param inputFolder Directory with real CSV logs
     * @param outputFolder Directory where synthetic logs will be written
     * @param vIdle Idle voltage for synthetic logs
     * @param years Number of years to simulate
     * @param wheelName Name prefix for generated files
     * @param mode Degradation mode
     * @param kmPerWeek Average km per week
     * @param kneeFrac Fraction of lifespan before accelerated degradation
     * @return List of synthetic log metadata
     */
    suspend fun generateSyntheticFolderFromReal(
        inputFolder: File,
        outputFolder: File,
        vIdle: Double,
        years: Int = 3,
        wheelName: String = "synthetic",
        mode: DegradationMode = DegradationMode.GLOBAL,
        kmPerWeek: Double = 100.0,
        kneeFrac: Double = 0.90
    ): List<Map<String, Any?>> {
        outputFolder.mkdirs()

        val real = loadRealSoh(inputFolder)

        val timeseries = generateSohTimeseriesFromReal(
            real = real,
            years = years,
            wheelName = wheelName,
            mode = mode,
            kmPerWeek = kmPerWeek,
            kneeFrac = kneeFrac
        )

        // Generate WheelLog CSVs
        timeseries.forEach { row ->
            val fileName = row["file"] as String
            val kmEnd = row["wheel_km"] as Double
            val csvFile = File(outputFolder, fileName)

            synthesizeWheellogCsv(
                outputFile = csvFile,
                vIdle = vIdle,
                kmEnd = kmEnd,
                metrics = row
            )
        }

        // Write summary CSV
        writeSummaryCsv(outputFolder, wheelName, timeseries)

        return timeseries
    }

    /**
     * Generates synthetic SoH time series from thresholds.
     * 
     * @param thresholds Threshold info from real data analysis
     * @param config Generation parameters
     * @return List of synthetic file stats
     */
    fun generateSohTimeseries(
        thresholds: Map<String, ThresholdInfo>,
        config: SyntheticConfig = SyntheticConfig()
    ): List<Map<String, Any?>> {
        val nWeeks = config.years * 52
        val startDt = LocalDateTime.now()

        // Generate km progression
        val kmArray = DoubleArray(nWeeks) { i ->
            config.startKm + config.kmPerWeek * (i + 1)
        }

        // Generate metric series
        val metricSeries = generateMetricSeries(
            thresholds = thresholds,
            kmArray = kmArray,
            config = config
        )

        // Build output rows
        return (0 until nWeeks).map { i ->
            val dt = startDt.plusWeeks(i.toLong())
            val row = mutableMapOf<String, Any?>(
                "file" to "${config.wheelName}_%04d.csv".format(i),
                "datetime_first" to dt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "wheel_km" to kmArray[i]
            )

            // Add metric values
            METRICS.forEach { metric ->
                metricSeries[metric]?.let { series ->
                    row[metric] = series[i]
                }
            }

            row
        }
    }

    /**
     * Generates metric time series with realistic degradation patterns.
     */
    private fun generateMetricSeries(
        thresholds: Map<String, ThresholdInfo>,
        kmArray: DoubleArray,
        config: SyntheticConfig
    ): Map<String, DoubleArray> {
        val kmMin = kmArray.minOrNull() ?: 0.0
        val kmMax = kmArray.maxOrNull() ?: 1.0
        val span = max(1.0, kmMax - kmMin)
        val xKnee = kmMin + config.kneeFrac * span

        val result = mutableMapOf<String, DoubleArray>()

        thresholds.forEach { (metric, info) ->
            val mu = info.mean
            val sigma = info.std
            val direction = info.direction

            val driftScale = getDriftScale(metric, config.mode)

            if (driftScale <= 0.0 || sigma == 0.0) {
                // No drift, just noise
                result[metric] = DoubleArray(kmArray.size) {
                    mu + Random.nextGaussian() * config.noiseFrac * max(sigma, 1e-6)
                }
                return@forEach
            }

            // Target drift
            var targetOffset = config.finalOffsetSigma * sigma * driftScale

            // Force positive drift for battery metrics
            if (metric in BATT_METRICS && targetOffset < 0) {
                targetOffset = -targetOffset
            }

            // Slow drift (20% of total effect)
            val alpha1 = 0.2 * targetOffset / span

            // Fast drift after knee (80%)
            val spanFast = max(1.0, kmMax - xKnee)
            val alpha2 = (targetOffset - alpha1 * span) / spanFast

            // Generate values
            val values = DoubleArray(kmArray.size) { i ->
                val km = kmArray[i]
                val slowTerm = alpha1 * (km - kmMin)
                val fastTerm = alpha2 * max(0.0, km - xKnee)
                var drift = slowTerm + fastTerm

                // Respect direction for non-battery metrics
                if (metric !in BATT_METRICS && direction == "lower_is_bad") {
                    drift = -drift
                }

                // Add noise (lower for resistance metrics)
                var sigmaNoise = config.noiseFrac * sigma
                if (metric in (BATT_METRICS + MOSFET_METRICS)) {
                    sigmaNoise *= 0.3
                }

                val noise = Random.nextGaussian() * sigmaNoise
                mu + drift + noise
            }

            result[metric] = values
        }

        return result
    }

    /**
     * Returns drift scale factor based on degradation mode.
     */
    private fun getDriftScale(metric: String, mode: DegradationMode): Double {
        return when (mode) {
            DegradationMode.GLOBAL -> 1.0
            
            DegradationMode.BATT_ONLY -> when {
                metric in BATT_METRICS -> 5.0     // Clear battery drift
                metric in MOSFET_METRICS -> 0.05  // Almost flat
                else -> 0.3                        // Neutral
            }
            
            DegradationMode.MOSFET_ONLY -> when {
                metric in MOSFET_METRICS -> 2.0   // Clear MOSFET drift
                metric in BATT_METRICS -> 0.05    // Almost flat
                else -> 0.3                        // Neutral
            }
        }
    }

    /**
     * Generates a minimal WheelLog-style CSV from SoH metrics.
     * 
     * @param outputFile Output CSV file
     * @param vIdle Idle voltage (V)
     * @param kmEnd Total mileage (km)
     * @param metrics Metric values for this log
     * @param nPoints Number of data points
     */
    fun synthesizeWheellogCsv(
        outputFile: File,
        vIdle: Double,
        kmEnd: Double,
        metrics: Map<String, Any?>,
        nPoints: Int = 2000
    ) {
        val vMinStrong = (metrics["v_min_strong"] as? Number)?.toDouble() ?: (vIdle - 15.0)
        val sagTarget = (metrics["sag_95p"] as? Number)?.toDouble() ?: (vIdle - vMinStrong)
        val reqMedian = (metrics["Req_median"] as? Number)?.toDouble() ?: (sagTarget / 60.0)

        val dt = 0.5 // seconds

        // Generate speed (km/h)
        val speed = DoubleArray(nPoints) {
            (Random.nextGaussian() * 10.0 + 25.0).coerceIn(5.0, 50.0)
        }

        // Generate battery current (A)
        val current = DoubleArray(nPoints) {
            (Random.nextGaussian() * 15.0 + 40.0).coerceIn(5.0, 80.0)
        }

        // Phase current (higher at low speed)
        val phaseCurrent = DoubleArray(nPoints) { i ->
            val ratio = 1.0 + 2.0 * (1.0 - speed[i] / 50.0).coerceIn(0.0, 1.0)
            current[i] * ratio
        }

        // Voltage (with sag)
        val voltage = DoubleArray(nPoints) { i ->
            val sag = reqMedian * kotlin.math.abs(current[i])
            vIdle - sag
        }

        // Scale to match v_min_strong
        val vMin = voltage.minOrNull() ?: vIdle
        val scale = (vIdle - vMinStrong) / max(1e-3, vIdle - vMin)
        voltage.indices.forEach { i ->
            voltage[i] = vIdle - (vIdle - voltage[i]) * scale
        }

        // Temperature simulation
        val boardTarget = (metrics["temp_board_max"] as? Number)?.toDouble() ?: 40.0
        val motorTarget = (metrics["temp_motor_max"] as? Number)?.toDouble() ?: 60.0

        val tAir = 20.0
        val alphaBoard = 0.004
        val alphaMotor = 0.006
        val tauBoard = 300.0
        val tauMotor = 400.0

        val tBoard = DoubleArray(nPoints)
        val tMotor = DoubleArray(nPoints)
        tBoard[0] = tAir + 2.0
        tMotor[0] = tAir + 3.0

        for (k in 1 until nPoints) {
            val i2 = current[k] * current[k]
            val dTBoard = dt * (alphaBoard * i2 - (tBoard[k - 1] - tAir) / tauBoard)
            val dTMotor = dt * (alphaMotor * i2 - (tMotor[k - 1] - tAir) / tauMotor)
            tBoard[k] = tBoard[k - 1] + dTBoard
            tMotor[k] = tMotor[k - 1] + dTMotor
        }

        // Scale temperatures to match targets
        val tBoardMax = tBoard.maxOrNull() ?: tAir
        if (tBoardMax > tAir) {
            val scaleBoard = (boardTarget - tAir) / max(1e-3, tBoardMax - tAir)
            tBoard.indices.forEach { i ->
                tBoard[i] = tAir + (tBoard[i] - tAir) * scaleBoard
            }
        }

        val tMotorMax = tMotor.maxOrNull() ?: tAir
        if (tMotorMax > tAir) {
            val scaleMotor = (motorTarget - tAir) / max(1e-3, tMotorMax - tAir)
            tMotor.indices.forEach { i ->
                tMotor[i] = tAir + (tMotor[i] - tAir) * scaleMotor
            }
        }

        // Add noise to temperatures
        val systemTemp = DoubleArray(nPoints) { i ->
            tBoard[i] + Random.nextGaussian() * 0.5
        }
        val tempMotor = DoubleArray(nPoints) { i ->
            tMotor[i] + Random.nextGaussian() * 0.8
        }

        val totalDistance = (kmEnd * 1000.0).toLong()

        // DateTime
        val dt0 = (metrics["datetime_first"] as? String)?.let {
            LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } ?: LocalDateTime.now()

        // Write CSV
        outputFile.bufferedWriter().use { writer ->
            writer.write("date,time,totaldistance,voltage,current,phase_current,speed,system_temp,temp_motor\n")

            for (i in 0 until nPoints) {
                val timestamp = dt0.plusSeconds((i * dt).toLong())
                val line = listOf(
                    timestamp.toLocalDate().toString(),
                    timestamp.toLocalTime().toString(),
                    totalDistance,
                    "%.2f".format(voltage[i]),
                    "%.2f".format(current[i]),
                    "%.2f".format(phaseCurrent[i]),
                    "%.2f".format(speed[i]),
                    "%.2f".format(systemTemp[i]),
                    "%.2f".format(tempMotor[i])
                ).joinToString(",")
                writer.write(line)
                writer.newLine()
            }
        }
    }

    /**
     * Generates a complete folder of synthetic logs (pure synthetic mode).
     * 
     * @param outputDir Output directory
     * @param thresholds Thresholds from real analysis
     * @param config Generation configuration
     * @return Summary of generated logs
     */
    fun generateSyntheticFolder(
        outputDir: File,
        thresholds: Map<String, ThresholdInfo>,
        config: SyntheticConfig = SyntheticConfig()
    ): List<Map<String, Any?>> {
        outputDir.mkdirs()

        val timeseries = generateSohTimeseries(thresholds, config)

        timeseries.forEach { row ->
            val filename = row["file"] as String
            val csvFile = File(outputDir, filename)
            val kmEnd = row["wheel_km"] as Double

            synthesizeWheellogCsv(
                outputFile = csvFile,
                vIdle = config.vIdle,
                kmEnd = kmEnd,
                metrics = row
            )
        }

        writeSummaryCsv(outputDir, config.wheelName, timeseries)

        return timeseries
    }

    /**
     * Writes summary CSV file.
     */
    private fun writeSummaryCsv(
        outputDir: File,
        wheelName: String,
        timeseries: List<Map<String, Any?>>
    ) {
        val summaryFile = File(outputDir, "${wheelName}_summary.csv")
        summaryFile.bufferedWriter().use { writer ->
            val headers = listOf("file", "datetime_first", "wheel_km") + METRICS
            writer.write(headers.joinToString(","))
            writer.newLine()

            timeseries.forEach { row ->
                val values = headers.map { header ->
                    row[header]?.toString() ?: ""
                }
                writer.write(values.joinToString(","))
                writer.newLine()
            }
        }
    }
}
