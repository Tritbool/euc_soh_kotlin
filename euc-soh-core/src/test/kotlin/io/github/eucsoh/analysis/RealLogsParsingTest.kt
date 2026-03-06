package io.github.eucsoh.analysis

import io.github.eucsoh.Constants.KNOWN_SERIES
import io.github.eucsoh.Constants.MAXIIMAL_CELL_V
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests with real CSV files from WheelLog and EUC World.
 * 
 * Tests validate:
 * - CSV parsing for both formats
 * - Column detection and normalization
 * - Req computation (must be positive for valid files)
 * - Source auto-detection
 */
class RealLogsParsingTest {

    private val resourcesDir = File("src/test/resources/real_logs")
    private val wheellogFile = File(resourcesDir, "wheellog.csv")
    private val eucWorldFile = File(resourcesDir, "euc_world.csv")

    @Test
    fun `WheelLog file should parse and compute positive Req`() {
        if (!wheellogFile.exists()) {
            println("⚠️ Skipping: wheellog.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(
            csvPath = wheellogFile.absolutePath,
            csvSource = null,
            speedThr = 20.0,
            curThr = 5.0
        )

        assertNotNull(result, "WheelLog file should parse successfully")
        
        println("🐞 [WheelLog] File: ${result.file}")
        println("  Source detected: ${result.source}")
        println("  Points analyzed: ${result.nPoints}")
        println("  Req median: ${result.reqMedian} Ω")
        println("  Req mean: ${result.reqMean} Ω")
        println("  V_idle: ${result.vIdle} V")
        println("  Ns estimated: ${result.ns}")
        println("  Wheel km: ${result.wheelKm} km (${result.wheelKmSource})")

        assertEquals("wheellog", result.source, "Should detect WheelLog format")
        assertTrue(result.nPoints > 50, "Should have sufficient data points")
        assertTrue(result.reqMedian > 0.0, "Req median must be positive")
        assertTrue(result.reqMean > 0.0, "Req mean must be positive")
        assertTrue(result.req95p > 0.0, "Req 95p must be positive")
        assertTrue(result.vIdle > 0.0, "V_idle must be positive")
        assertNotNull(result.ns, "Ns should be detected")
        assertTrue(result.ns!! > 10, "Ns should be reasonable for EUC (>10)")
        assertNotNull(result.wheelKmSource, "Distance source should be identified")
        assertTrue(result.wheelKmSource!!.contains("wl"), "Should use WheelLog distance column")
    }

    @Test
    fun `EUC World file should parse and compute positive Req`() {
        if (!eucWorldFile.exists()) {
            println("⚠️ Skipping: euc_world.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(
            csvPath = eucWorldFile.absolutePath,
            csvSource = null,
            speedThr = 20.0,
            curThr = 5.0
        )

        assertNotNull(result, "EUC World file should parse successfully")
        
        println("🐞 [EUC World] File: ${result.file}")
        println("  Source detected: ${result.source}")
        println("  Points analyzed: ${result.nPoints}")
        println("  Req median: ${result.reqMedian} Ω")
        println("  Req mean: ${result.reqMean} Ω")
        println("  V_idle: ${result.vIdle} V")
        println("  Ns estimated: ${result.ns}")
        println("  Wheel km: ${result.wheelKm} km (${result.wheelKmSource})")

        // 🐞 CRITICAL: These assertions will FAIL if the bug exists
        assertEquals("euc_world", result.source, "Should detect EUC World format")
        assertTrue(result.nPoints > 50, "Should have sufficient data points")
        assertTrue(result.reqMedian > 0.0, "Req median must be positive (currently FAILS for EUC World!)")
        assertTrue(result.reqMean > 0.0, "Req mean must be positive (currently FAILS for EUC World!)")
        assertTrue(result.req95p > 0.0, "Req 95p must be positive (currently FAILS for EUC World!)")
        assertTrue(result.vIdle > 0.0, "V_idle must be positive")
        assertNotNull(result.ns, "Ns should be detected")
        assertTrue(result.ns!! > 10, "Ns should be reasonable for EUC (>10)")
        assertNotNull(result.wheelKmSource, "Distance source should be identified")
        assertTrue(result.wheelKmSource!!.contains("euc"), "Should use EUC World distance column")
    }

    @Test
    fun `should correctly auto-detect source format`() {
        if (!wheellogFile.exists() || !eucWorldFile.exists()) {
            println("⚠️ Skipping: missing test files")
            return
        }

        val wheellogResult = ReqStatsComputer.computeReqStatsForFile(wheellogFile.absolutePath)
        val eucWorldResult = ReqStatsComputer.computeReqStatsForFile(eucWorldFile.absolutePath)

        assertNotNull(wheellogResult, "WheelLog should parse")
        assertNotNull(eucWorldResult, "EUC World should parse")
        
        assertEquals("wheellog", wheellogResult.source, "WheelLog should be detected")
        assertEquals("euc_world", eucWorldResult.source, "EUC World should be detected")
    }

    @Test
    fun `should NOT return negative Req for WheelLog`() {
        if (!wheellogFile.exists()) {
            println("⚠️ Skipping: wheellog.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(wheellogFile.absolutePath)
        assertNotNull(result, "WheelLog should parse")

        assertTrue(
            result.reqMedian > 0.0,
            "WheelLog returned negative Req: ${result.reqMedian} Ω. " +
            "This indicates a parsing or calculation error."
        )
        assertTrue(
            result.reqMean > 0.0,
            "WheelLog returned negative mean Req: ${result.reqMean} Ω"
        )
        assertTrue(
            result.req95p > 0.0,
            "WheelLog returned negative 95p Req: ${result.req95p} Ω"
        )
    }

    @Test
    fun `should NOT return negative Req for EUC World`() {
        if (!eucWorldFile.exists()) {
            println("⚠️ Skipping: euc_world.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(eucWorldFile.absolutePath)
        assertNotNull(result, "EUC World should parse")

        // 🐞 BUG REPRODUCTION: This will FAIL if the Android bug exists
        assertTrue(
            result.reqMedian > 0.0,
            "🐞 BUG DETECTED: EUC World returned negative Req: ${result.reqMedian} Ω. " +
            "This indicates a parsing or calculation error for EUC World format."
        )
        assertTrue(
            result.reqMean > 0.0,
            "🐞 BUG DETECTED: EUC World returned negative mean Req: ${result.reqMean} Ω"
        )
        assertTrue(
            result.req95p > 0.0,
            "🐞 BUG DETECTED: EUC World returned negative 95p Req: ${result.req95p} Ω"
        )
    }

    @Test
    fun `should NOT return zero Req for valid files`() {
        val files = listOf(
            wheellogFile to "WheelLog",
            eucWorldFile to "EUC World"
        )

        files.forEach { (file, label) ->
            if (!file.exists()) {
                println("⚠️ Skipping $label: file not found")
                return@forEach
            }

            val result = ReqStatsComputer.computeReqStatsForFile(file.absolutePath)
            assertNotNull(result, "$label should parse")

            assertTrue(
                result.reqMedian != 0.0,
                "$label returned zero Req (median). This typically indicates: " +
                "1) Missing current data, 2) Failed column mapping, 3) All points filtered out. " +
                "Points analyzed: ${result.nPoints}"
            )
            assertTrue(
                result.reqMean != 0.0,
                "$label returned zero mean Req"
            )
        }
    }

    @Test
    fun `WheelLog should have reasonable datetime format`() {
        if (!wheellogFile.exists()) {
            println("⚠️ Skipping: wheellog.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(wheellogFile.absolutePath)
        assertNotNull(result, "WheelLog should parse")
        assertNotNull(result.datetimeFirst, "WheelLog should have datetime")
        assertTrue(
            result.datetimeFirst!!.contains(" "),
            "WheelLog datetime should be in 'date time' format, got: ${result.datetimeFirst}"
        )
    }

    @Test
    fun `EUC World should have single datetime field`() {
        if (!eucWorldFile.exists()) {
            println("⚠️ Skipping: euc_world.csv not found")
            return
        }

        val result = ReqStatsComputer.computeReqStatsForFile(eucWorldFile.absolutePath)
        assertNotNull(result, "EUC World should parse")
        assertNotNull(result.datetimeFirst, "EUC World should have datetime")
    }

    @Test
    fun `should extract reasonable battery pack parameters`() {
        val files = listOf(wheellogFile, eucWorldFile)

        files.forEach { file ->
            if (!file.exists()) return@forEach

            val result = ReqStatsComputer.computeReqStatsForFile(file.absolutePath)
            assertNotNull(result, "${file.name} should parse")

            // Typical EUC voltage ranges (16s ≈ 67V, 24s ≈ 100V)
            assertTrue(
                result.vIdle in 50.0..KNOWN_SERIES.last()*MAXIIMAL_CELL_V,
                "${file.name}: V_idle ${result.vIdle}V is outside typical EUC range (50-120V)"
            )

            // Typical Req ranges for EUCs (1mΩ to 500mΩ)
            assertTrue(
                result.reqMedian in 0.001..0.5,
                "${file.name}: Req ${result.reqMedian}Ω is outside typical EUC range (0.001-0.5Ω)"
            )

            // Ns should be typical EUC pack size (16s to 24s)
            assertNotNull(result.ns, "${file.name} should detect Ns")
            assertTrue(
                result.ns!! in KNOWN_SERIES,
                "${file.name}: Ns ${result.ns} is outside typical EUC range (12-30)"
            )
        }
    }

    @Test
    fun `should analyze files in reasonable time`() {
        val files = listOf(wheellogFile, eucWorldFile).filter { it.exists() }
        
        if (files.isEmpty()) {
            println("⚠️ Skipping: no test files found")
            return
        }

        files.forEach { file ->
            val startTime = System.currentTimeMillis()
            val result = ReqStatsComputer.computeReqStatsForFile(file.absolutePath)
            val duration = System.currentTimeMillis() - startTime

            assertNotNull(result, "${file.name} should parse")
            println("⏱️ ${file.name}: ${duration}ms for ${result.nPoints} points")

            // Should not take unreasonably long (allow 10s for large files in CI)
            assertTrue(
                duration < 10000,
                "${file.name} took ${duration}ms, which exceeds 10s threshold"
            )
        }
    }
}
