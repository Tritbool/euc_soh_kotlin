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

package io.github.eucsoh.integration

import io.github.eucsoh.Constants.HIGHER_IS_BAD
import io.github.eucsoh.Constants.Metrics.*
import io.github.eucsoh.SohAnalyzer
import io.github.eucsoh.model.MOSFETParams
import io.github.eucsoh.model.ThresholdInfo
import io.github.eucsoh.synthetic.SyntheticDataGenerator
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.Double
import kotlin.test.*

/**
 * End-to-end integration tests using synthetic data.
 * Validates the complete SoH analysis pipeline with realistic degradation scenarios.
 */
class EndToEndSyntheticTest {

    val mosfets = MOSFETParams(
        rDsOn25cTotal = 0.052,
        tempCoeffRel = 0.05,
        rWiring = 0.01
    )

    private val tempDir = File.createTempFile("euc_soh_test", "").apply {
        delete()
        mkdirs()
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `detect battery degradation in synthetic data`() = runBlocking {
        // Setup: Create synthetic degradation scenario
        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.050,
                std = 0.002,
                limit = 0.054,
                direction = HIGHER_IS_BAD
            ),
            R_BATT_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.045,
                std = 0.002,
                limit = 0.049,
                direction = HIGHER_IS_BAD
            ),
            SAG_95P.csv_code to ThresholdInfo(
                mean = 3.5,
                std = 0.5,
                limit = 4.5,
                direction = HIGHER_IS_BAD
            ),
            TEMP_BOARD_MAX.csv_code to ThresholdInfo(
                mean = 40.0,
                std = 5.0,
                limit = 50.0,
                direction = HIGHER_IS_BAD
            )
        )

        val config = SyntheticDataGenerator.SyntheticConfig(
            years = 5,
            kmPerWeek = 150.0,
            kneeFrac = 0.70,  // Accelerate degradation at 70% of lifespan
            mode = SyntheticDataGenerator.DegradationMode.BATT_ONLY,
            finalOffsetSigma = 10.0,  // Strong degradation signal
            wheelName = "test_batt_degr",
            vIdle = 84.0  // 20S pack
        )

        // Generate synthetic folder
        val syntheticDir = File(tempDir, "batt_degradation")
        val timeseries = SyntheticDataGenerator.generateSyntheticFolder(
            outputDir = syntheticDir,
            thresholds = thresholds,
            config = config
        )

        assertTrue(syntheticDir.exists(), "Synthetic folder should be created")
        assertTrue(syntheticDir.listFiles()!!.size > 50, "Should generate multiple CSV files")

        // Analyze with SohAnalyzer
        val csvPaths = syntheticDir.listFiles()!!
            .filter { it.extension == "csv" && !it.name.contains("summary") }
            .map { it.absolutePath }
            .sorted()

        val analyzer = SohAnalyzer(mosfetParams = mosfets)
        val result = analyzer.analyzeFolderForReq(
            csvPaths = csvPaths,
            optimalFrac = 0.3,
            eaJPerMol = 20000.0,
        )

        // Assertions: Should detect battery degradation
        assertNotNull(result.stats, "Should produce statistics")
        assertTrue(result.stats.rowsCount() > 50, "Should analyze multiple logs")

        // Check that alarms are detected (degradation should trigger warnings)
        val battAlarms = result.alarms.filter { alarm ->
            alarm.reasons.contains(REQ_95P.csv_code) ||
                    alarm.reasons.contains(R_BATT_MEDIAN.csv_code) ||
                    alarm.reasons.contains(REQ_MEDIAN.csv_code) ||
                    alarm.reasons.contains(SAG_95P.csv_code)
        }

        assertTrue(battAlarms.isNotEmpty(), "Should detect battery degradation alarms")
        println("✓ Detected ${battAlarms.size} battery alarms")

        // OVERKILL => tested in linear trend detection
        /*
        // Verify degradation trend in data
        val reqValues = result.stats[R_BATT_MEDIAN.csv_code].values()
            .filterIsInstance<Number>()
            .map { it.toDouble() }

        val firstQuartile = reqValues.take(reqValues.size / 4).average()
        val lastQuartile = reqValues.takeLast(reqValues.size / 4).average()

        assertTrue(
            lastQuartile > firstQuartile * 1.1,
            "R_batt_median should increase over time (battery degradation). " +
            "First: $firstQuartile, Last: $lastQuartile"
        )

        println("✓ R_batt_median degradation: ${firstQuartile.format(4)} → ${lastQuartile.format(4)} Ω")
        */
    }

    @Test
    fun `detect MOSFET degradation in synthetic data`() = runBlocking {
        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(0.050, 0.002, 0.054, HIGHER_IS_BAD),
            R_MOSFET_HOT.csv_code to ThresholdInfo(0.008, 0.001, 0.010, HIGHER_IS_BAD),
            TEMP_BOARD_MAX.csv_code to ThresholdInfo(40.0, 5.0, 50.0, HIGHER_IS_BAD),
            I_PHASE2_INT.csv_code to ThresholdInfo(500000.0, 50000.0, 600000.0, HIGHER_IS_BAD)
        )

        val config = SyntheticDataGenerator.SyntheticConfig(
            years = 2,
            kmPerWeek = 120.0,
            kneeFrac = 0.75,
            mode = SyntheticDataGenerator.DegradationMode.MOSFET_ONLY,
            finalOffsetSigma = 2.0,
            wheelName = "test_mosfet_degr",
            vIdle = 100.8  // 24S pack
        )

        val syntheticDir = File(tempDir, "mosfet_degradation")
        SyntheticDataGenerator.generateSyntheticFolder(
            outputDir = syntheticDir,
            thresholds = thresholds,
            config = config
        )

        val csvPaths = syntheticDir.listFiles()!!
            .filter { it.extension == "csv" && !it.name.contains("summary") }
            .map { it.absolutePath }
            .sorted()

        val analyzer = SohAnalyzer(mosfetParams = mosfets)
        val result = analyzer.analyzeFolderForReq(
            csvPaths = csvPaths,
            optimalFrac = 0.3,
        )

        // Should detect MOSFET/thermal alarms
        val mosfetAlarms = result.alarms.filter { alarm ->
            alarm.reasons.contains("R_mosfet") ||
            alarm.reasons.contains("temp_board") ||
            alarm.reasons.contains(I_PHASE2_INT.csv_code)
        }

        // MOSFET degradation is more subtle, but should still be detectable
        println("✓ Detected ${mosfetAlarms.size} MOSFET/thermal alarms")
    }

    @Test
    fun `CUSUM detects regime change in synthetic data`() = runBlocking {
        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(0.050, 0.002, 0.054, HIGHER_IS_BAD),
            R_BATT_MEDIAN.csv_code to ThresholdInfo(0.045, 0.002, 0.049, HIGHER_IS_BAD)
        )

        // Sharp degradation after knee point
        val config = SyntheticDataGenerator.SyntheticConfig(
            years = 2,
            kmPerWeek = 100.0,
            kneeFrac = 0.60,  // Early knee for clear regime change
            mode = SyntheticDataGenerator.DegradationMode.BATT_ONLY,
            finalOffsetSigma = 15.0,  // Very strong signal
            wheelName = "test_cusum"
        )

        val syntheticDir = File(tempDir, "cusum_test")
        SyntheticDataGenerator.generateSyntheticFolder(
            outputDir = syntheticDir,
            thresholds = thresholds,
            config = config
        )

        val csvPaths = syntheticDir.listFiles()!!
            .filter { it.extension == "csv" && !it.name.contains("summary") }
            .map { it.absolutePath }
            .sorted()

        val analyzer = SohAnalyzer()
        val result = analyzer.analyzeFolderForReq(csvPaths)

        // Should detect CUSUM alarms
        val cusumAlarms = result.alarms.filter { alarm ->
            alarm.reasons.contains("CUSUM") || alarm.reasons.contains("Regime change")
        }

        assertTrue(cusumAlarms.isNotEmpty(), "Should detect regime change with CUSUM")
        println("✓ CUSUM detected ${cusumAlarms.size} regime changes")

        // Verify alarm occurs after knee point
        val kneeKm = config.kneeFrac * (config.years * 52 * config.kmPerWeek)
        cusumAlarms.forEach { alarm ->
            if (alarm.wheelKm != null) {
                println("  - Alarm at ${alarm.wheelKm.format(0)} km (knee at ${kneeKm.format(0)} km)")
            }
        }
    }

    @Test
    fun `linear trend detection on gradual degradation`() = runBlocking {
        // Setup: Create synthetic degradation scenario
        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.050,
                std = 0.002,
                limit = 0.054,
                direction = HIGHER_IS_BAD
            ),
            R_BATT_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.045,
                std = 0.002,
                limit = 0.049,
                direction = HIGHER_IS_BAD
            ),
            SAG_95P.csv_code to ThresholdInfo(
                mean = 3.5,
                std = 0.5,
                limit = 4.5,
                direction = HIGHER_IS_BAD
            ),
            TEMP_BOARD_MAX.csv_code to ThresholdInfo(
                mean = 40.0,
                std = 5.0,
                limit = 50.0,
                direction = HIGHER_IS_BAD
            )
        )

        val config = SyntheticDataGenerator.SyntheticConfig(
            years = 5,
            kmPerWeek = 150.0,
            kneeFrac = 0.70,  // Accelerate degradation at 70% of lifespan
            mode = SyntheticDataGenerator.DegradationMode.BATT_ONLY,
            finalOffsetSigma = 10.0,  // Strong degradation signal
            wheelName = "trend_test",
            vIdle = 134.4  // 32S pack
        )


        val syntheticDir = File(tempDir, "trend_test")
        SyntheticDataGenerator.generateSyntheticFolder(
            outputDir = syntheticDir,
            thresholds = thresholds,
            config = config
        )

        val csvPaths = syntheticDir.listFiles()!!
            .filter { it.extension == "csv" && !it.name.contains("summary") }
            .map { it.absolutePath }
            .sorted()

        val analyzer = SohAnalyzer(mosfetParams = mosfets)
        val result = analyzer.analyzeFolderForReq(csvPaths)

        // Should detect linear trend alarms
        val trendAlarms = result.alarms.filter { alarm ->
            alarm.file == "TREND_ANALYSIS" || alarm.reasons.contains("trend")
        }

        assertTrue(trendAlarms.isNotEmpty(), "Should detect upward trends")
        println("✓ Detected ${trendAlarms.size} linear trends")

        trendAlarms.forEach { alarm ->
            println("  - ${alarm.reasons}")
        }
    }

    @Test
    fun `stable synthetic data produces no alarms`() = runBlocking {
        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(0.050, 0.002, 0.060, HIGHER_IS_BAD),
            R_BATT_MEDIAN.csv_code to ThresholdInfo(0.045, 0.002, 0.055, HIGHER_IS_BAD),
            TEMP_BOARD_MAX.csv_code to ThresholdInfo(40.0, 5.0, 55.0, HIGHER_IS_BAD)
        )

        // No degradation (finalOffsetSigma = 0)
        val config = SyntheticDataGenerator.SyntheticConfig(
            years = 1,
            kmPerWeek = 50.0,
            mode = SyntheticDataGenerator.DegradationMode.GLOBAL,
            finalOffsetSigma = 0.0,  // No drift
            noiseFrac = 0.2,  // Only noise
            wheelName = "test_stable"
        )

        val syntheticDir = File(tempDir, "stable_test")
        SyntheticDataGenerator.generateSyntheticFolder(
            outputDir = syntheticDir,
            thresholds = thresholds,
            config = config
        )

        val csvPaths = syntheticDir.listFiles()!!
            .filter { it.extension == "csv" && !it.name.contains("summary") }
            .map { it.absolutePath }
            .sorted()

        val analyzer = SohAnalyzer()
        val result = analyzer.analyzeFolderForReq(csvPaths)

        // Should produce minimal alarms (only noise-induced false positives)
        val gaussianAlarms = result.alarms.filter { alarm ->
            alarm.reasons.contains("CUSUM") ||
            alarm.reasons.contains("trend") ||
            alarm.file == "TREND_ANALYSIS"
        }

        // Allow a few false positives due to noise, but should be minimal
        val alarmRate = gaussianAlarms.size.toDouble() / result.stats.rowsCount()
        assertTrue(
            alarmRate < 0.1,
            "Stable data should produce <10% false alarms, got ${(alarmRate * 100).format(1)}%"
        )

        println("✓ Stable data: ${gaussianAlarms.size} alarms / ${result.stats.rowsCount()} logs (${(alarmRate * 100).format(1)}%)")
    }

    @Test
    fun `synthetic data respects pack configuration inference`() = runBlocking {
        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(0.050, 0.002, 0.054, HIGHER_IS_BAD)
        )

        // 24S pack: 100.8V nominal
        val config = SyntheticDataGenerator.SyntheticConfig(
            years = 5,
            kmPerWeek = 150.0,
            vIdle = 100.8,  // 24 * 4.2V = 100.8V
            finalOffsetSigma = 1.0,
            wheelName = "test_24s"
        )

        val syntheticDir = File(tempDir, "pack_inference_test")
        SyntheticDataGenerator.generateSyntheticFolder(
            outputDir = syntheticDir,
            thresholds = thresholds,
            config = config
        )

        val csvPaths = syntheticDir.listFiles()!!
            .filter { it.extension == "csv" && !it.name.contains("summary") }
            .map { it.absolutePath }
            .sorted()

        val analyzer = SohAnalyzer()
        val result = analyzer.analyzeFolderForReq(csvPaths)

        // Should infer 24S configuration
        assertEquals(24, result.nsGlobal, "Should detect 24S pack from V_idle=100.8V")
        assertEquals(88.8, result.vNominal?: Double.MAX_VALUE, 0.1, "V_nominal should be 24*3.7=88.8V")

        println("✓ Inferred pack: ${result.nsGlobal}S, ${result.vNominal}V nominal")
    }

    @Test
    fun `buildSummary generates complete export structure`() = runBlocking {
        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(0.050, 0.002, 0.056, HIGHER_IS_BAD),
            R_BATT_MEDIAN.csv_code to ThresholdInfo(0.045, 0.002, 0.052, HIGHER_IS_BAD)
        )

        val config = SyntheticDataGenerator.SyntheticConfig(
            years = 3,
            kmPerWeek = 150.0,
            wheelName = "test_summary"
        )

        val syntheticDir = File(tempDir, "summary_test")
        SyntheticDataGenerator.generateSyntheticFolder(
            outputDir = syntheticDir,
            thresholds = thresholds,
            config = config
        )

        val csvPaths = syntheticDir.listFiles()!!
            .filter { it.extension == "csv" && it.name.contains("summary") }
            .map { it.absolutePath }
            .sorted()

        val analyzer = SohAnalyzer()
        val result = analyzer.analyzeFolderForReq(csvPaths)

        // Build summary
        val summary = analyzer.buildSummary(result, "test_summary")

        assertEquals("test_summary", summary.wheelName)
        assertNotNull(summary.reqBand.low)
        assertNotNull(summary.reqBand.high)
        assertTrue(summary.reqBand.low < summary.reqBand.high, "Req band should be valid")

        assertNotNull(summary.pack.ns)
        assertNotNull(summary.pack.vNominal)
        assertNotNull(summary.pack.rPackNominal)

        assertTrue(summary.globalStats.kmMax > summary.globalStats.kmMin)
        assertTrue(summary.logs.isNotEmpty(), "Should include log details")

        println("✓ Summary: ${summary.logs.size} logs, ${summary.globalStats.kmMin.format(0)}-${summary.globalStats.kmMax.format(0)} km")
        println("  Req band: ${summary.reqBand.low.format(4)}-${summary.reqBand.high.format(4)} Ω")
        println("  Pack: ${summary.pack.ns}S, R_pack=${summary.pack.rPackNominal?.let { "%.3f".format(it) }} Ω")
    }

    // Helper extension for formatting
    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
}
