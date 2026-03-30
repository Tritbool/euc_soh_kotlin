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

import io.github.eucsoh.Constants
import io.github.eucsoh.Constants.Metrics
import io.github.eucsoh.SohAnalyzer
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/**
 * Integration test using real EUC logs.
 * 
 * This test validates the complete analysis pipeline on actual data.
 * It automatically skips if no real logs are found in src/test/resources/real_logs.
 * 
 * To enable this test:
 * 1. Create directory: euc-soh-core/src/test/resources/real_logs
 * 2. Add real CSV log files from EUC World or WheelLog
 * 3. Run tests - this will now execute
 * 
 * Expected folder structure:
 * ```
 * euc-soh-core/src/test/resources/real_logs/
 *   ├── log_001.csv
 *   ├── log_002.csv
 *   └── ...
 * ```
 */
class RealLogsIntegrationTest {

    private val realLogsFolder = File("src/test/resources/real_logs")

    @Test
    fun `real logs analysis pipeline runs end-to-end`() {
        // Check if real logs exist
        val csvFiles = realLogsFolder.listFiles()?.filter { it.extension == "csv" } ?: emptyList()
        
        if (!realLogsFolder.exists() || csvFiles.isEmpty()) {
            println("⊘ Skipping real logs test: no CSV files found in ${realLogsFolder.path}")
            println("  To enable this test, add real EUC log files to this directory")
            return
        }

        println("✓ Found ${csvFiles.size} real log files in ${realLogsFolder.path}")
        println("  Running full analysis pipeline...")

        val csvPaths = csvFiles.map { it.absolutePath }.sorted()
        val analyzer = SohAnalyzer()

        // Run analysis with runBlocking
        val result = runBlocking {
            analyzer.analyzeFolderForReq(
                csvPaths = csvPaths,
                optimalFrac = 0.3
            )
        }

        // Basic assertions
        assertNotNull(result.stats, "Stats should be generated")
        assertTrue(result.stats.rowsCount() > 0, "Should have at least one log analyzed")
        
        println("  ✓ Analyzed ${result.stats.rowsCount()} logs")

        // Pack inference
        assertNotNull(result.nsGlobal, "Should infer battery pack Ns")
        assertNotNull(result.vNominal, "Should infer nominal voltage")
        assertTrue(result.nsGlobal!! in 10..50, "Ns should be realistic (10-50S)")
        
        println("  ✓ Inferred pack: ${result.nsGlobal}S, ${result.vNominal}V nominal")

        // Thresholds computed
        assertTrue(result.thresholds.isNotEmpty(), "Should compute thresholds")
        assertTrue(result.thresholds.containsKey(Metrics.REQ_MEDIAN.csv_code), "Should have Req_median threshold")
        
        val reqThreshold = result.thresholds[Metrics.REQ_MEDIAN.csv_code]!!
        assertTrue(reqThreshold.mean > 0.0, "Req_median mean should be positive")
        assertTrue(reqThreshold.std >= 0.0, "Req_median std should be non-negative")
        
        println("  ✓ Req_median threshold: μ=${"%.4f".format(reqThreshold.mean)}Ω, σ=${"%.4f".format(reqThreshold.std)}Ω")

        // Check that critical metrics are present
        val criticalMetrics = listOf(Metrics.REQ_MEDIAN.csv_code, Constants.MetaColumns.WHEEL_KM.csv_code, Constants.MetaColumns.DATETIME_FIRST.csv_code)
        criticalMetrics.forEach { metric ->
            assertTrue(
                result.stats.columnNames().contains(metric),
                "Stats should contain $metric column"
            )
        }

        println("  ✓ All critical metrics present")

        // Verify km range is realistic
        val kmValues = result.stats[Constants.MetaColumns.WHEEL_KM.csv_code].values()
            .filterIsInstance<Number>()
            .map { it.toDouble() }
        
        if (kmValues.isNotEmpty()) {
            val kmMin = kmValues.minOrNull()!!
            val kmMax = kmValues.maxOrNull()!!
            val kmRange = kmMax - kmMin
            
            assertTrue(kmMin >= 0.0, "Min km should be non-negative")
            assertTrue(kmMax > kmMin, "Max km should be greater than min km")
            
            println("  ✓ Km range: ${"%.0f".format(kmMin)} - ${"%.0f".format(kmMax)} km (Δ=${"%.0f".format(kmRange)} km)")
        }

        // Check alarms (may or may not exist depending on data quality)
        println("  ✓ Detected ${result.alarms.size} alarms")
        
        if (result.alarms.isNotEmpty()) {
            val alarmTypes = result.alarms.groupBy { 
                when {
                    it.file == "CUSUM_ANALYSIS" -> "CUSUM"
                    it.file == "TREND_ANALYSIS" -> "Trend"
                    else -> "Gaussian"
                }
            }
            alarmTypes.forEach { (type, alarms) ->
                println("    - $type: ${alarms.size} alarms")
            }
        }

        // Verify summary can be built
        val summary = analyzer.buildSummary(result, "real_wheel_test")
        assertEquals("real_wheel_test", summary.wheelName)
        assertNotNull(summary.reqBand.low)
        assertNotNull(summary.reqBand.high)
        assertTrue(summary.reqBand.low < summary.reqBand.high, "Req band should be valid")
        
        println("  ✓ Summary built successfully")
        println("    Req band: ${"%.4f".format(summary.reqBand.low)} - ${"%.4f".format(summary.reqBand.high)} Ω")
        println("    Pack: ${summary.pack.ns}S, R_pack=${summary.pack.rPackNominal?.let { "%.3f".format(it) }}Ω")
        
        println("\n✓ Real logs integration test PASSED")
    }

    @Test
    fun `real logs folder structure is correct if it exists`() {
        if (!realLogsFolder.exists()) {
            println("⊘ Skipping folder structure test: ${realLogsFolder.path} does not exist")
            return
        }

        assertTrue(realLogsFolder.isDirectory, "real_logs should be a directory")
        
        val files = realLogsFolder.listFiles() ?: emptyArray()
        val csvFiles = files.filter { it.extension == "csv" }
        
        if (csvFiles.isEmpty()) {
            println("⊘ Warning: real_logs folder exists but contains no CSV files")
            return
        }

        // Verify each CSV has minimum viable size (not empty)
        csvFiles.forEach { file ->
            assertTrue(file.length() > 100, "CSV file ${file.name} should not be empty or corrupted")
        }
        
        println("✓ Found ${csvFiles.size} valid CSV files in real_logs folder")
    }
}
