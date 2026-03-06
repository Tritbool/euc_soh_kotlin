package io.github.eucsoh.analysis

import io.github.eucsoh.CsvSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Integration tests with real CSV files from WheelLog and EUC World.
 * 
 * Tests validate:
 * - CSV parsing for both formats
 * - Column detection and normalization
 * - Req computation (must be positive for valid files)
 * - Source auto-detection
 */
class RealLogsParsingTest : FunSpec({

    val resourcesDir = File("src/test/resources/real_logs")
    val wheellogFile = File(resourcesDir, "wheellog.csv")
    val eucWorldFile = File(resourcesDir, "euc_world.csv")

    context("WheelLog format parsing") {
        test("should parse wheellog.csv and compute positive Req") {
            // Given
            if (!wheellogFile.exists()) {
                println("⚠️ Skipping: wheellog.csv not found")
                return@test
            }

            // When
            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = wheellogFile.absolutePath,
                csvSource = null,  // Auto-detect
                speedThr = 20.0,
                curThr = 5.0
            )

            // Then
            result shouldNotBe null
            result!!.apply {
                println("🐞 [WheelLog] File: $file")
                println("  Source detected: $source")
                println("  Points analyzed: $nPoints")
                println("  Req median: $reqMedian Ω")
                println("  Req mean: $reqMean Ω")
                println("  V_idle: $vIdle V")
                println("  Ns estimated: $ns")
                println("  Wheel km: $wheelKm km ($wheelKmSource)")
                println("  Datetime first: $datetimeFirst")

                // Critical validations
                source shouldBe "wheellog"
                nPoints shouldBeGreaterThan 50
                reqMedian shouldBeGreaterThan 0.0
                reqMean shouldBeGreaterThan 0.0
                req95p shouldBeGreaterThan 0.0
                vIdle shouldBeGreaterThan 0.0
                ns shouldNotBe null
                ns!! shouldBeGreaterThan 10  // Sanity: typical EUC has 16-24s

                // WheelLog-specific fields
                wheelKmSource shouldContain "wl"  // Should use WheelLog's totaldistance
            }
        }

        test("should handle WheelLog column names correctly") {
            if (!wheellogFile.exists()) {
                println("⚠️ Skipping: wheellog.csv not found")
                return@test
            }

            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = wheellogFile.absolutePath
            )

            result shouldNotBe null
            result!!.apply {
                // WheelLog should have datetime components
                datetimeFirst shouldNotBe null
                datetimeFirst!! shouldContain " "  // Should be "date time" format
            }
        }
    }

    context("EUC World format parsing") {
        test("should parse euc_world.csv and compute positive Req") {
            // Given
            if (!eucWorldFile.exists()) {
                println("⚠️ Skipping: euc_world.csv not found")
                return@test
            }

            // When
            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = eucWorldFile.absolutePath,
                csvSource = null,  // Auto-detect
                speedThr = 20.0,
                curThr = 5.0
            )

            // Then
            result shouldNotBe null
            result!!.apply {
                println("🐞 [EUC World] File: $file")
                println("  Source detected: $source")
                println("  Points analyzed: $nPoints")
                println("  Req median: $reqMedian Ω")
                println("  Req mean: $reqMean Ω")
                println("  V_idle: $vIdle V")
                println("  Ns estimated: $ns")
                println("  Wheel km: $wheelKm km ($wheelKmSource)")
                println("  Datetime first: $datetimeFirst")

                // 🐞 BUG REPRODUCTION: This MUST pass (currently fails in Android)
                source shouldBe "euc_world"
                nPoints shouldBeGreaterThan 50
                
                // ⚠️ CRITICAL: These assertions will FAIL if the bug exists
                reqMedian shouldBeGreaterThan 0.0  // Currently returns negative!
                reqMean shouldBeGreaterThan 0.0    // Currently returns negative!
                req95p shouldBeGreaterThan 0.0     // Currently returns negative!
                
                vIdle shouldBeGreaterThan 0.0
                ns shouldNotBe null
                ns!! shouldBeGreaterThan 10

                // EUC World-specific fields
                wheelKmSource shouldContain "euc"  // Should use EUC World's distance_total
            }
        }

        test("should handle EUC World column names correctly") {
            if (!eucWorldFile.exists()) {
                println("⚠️ Skipping: euc_world.csv not found")
                return@test
            }

            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = eucWorldFile.absolutePath
            )

            result shouldNotBe null
            result!!.apply {
                // EUC World should have single datetime column
                datetimeFirst shouldNotBe null
            }
        }
    }

    context("Source auto-detection") {
        test("should correctly distinguish WheelLog from EUC World") {
            if (!wheellogFile.exists() || !eucWorldFile.exists()) {
                println("⚠️ Skipping: missing test files")
                return@test
            }

            val wheellogResult = ReqStatsComputer.computeReqStatsForFile(wheellogFile.absolutePath)
            val eucWorldResult = ReqStatsComputer.computeReqStatsForFile(eucWorldFile.absolutePath)

            wheellogResult shouldNotBe null
            eucWorldResult shouldNotBe null

            wheellogResult!!.source shouldBe "wheellog"
            eucWorldResult!!.source shouldBe "euc_world"
        }
    }

    context("Regression tests for Android bug") {
        test("🐞 should NOT return negative Req for any file") {
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
                result shouldNotBe null

                result!!.apply {
                    println("📊 [$label] Req median: $reqMedian, mean: $reqMean, 95p: $req95p")

                    // 🐞 CRITICAL BUG CHECK: Negative Req is ALWAYS invalid
                    if (reqMedian < 0.0) {
                        throw AssertionError(
                            "🐞 BUG DETECTED: $label returned negative Req!\n" +
                            "  File: ${file.name}\n" +
                            "  Req median: $reqMedian Ω (INVALID)\n" +
                            "  This indicates a parsing/calculation error.\n" +
                            "  Expected: Req > 0 for valid battery resistance measurement"
                        )
                    }

                    reqMedian shouldBeGreaterThan 0.0
                    reqMean shouldBeGreaterThan 0.0
                    req95p shouldBeGreaterThan 0.0
                }
            }
        }

        test("🐞 should NOT return zero Req for any file") {
            val files = listOf(wheellogFile, eucWorldFile)

            files.forEach { file ->
                if (!file.exists()) return@forEach

                val result = ReqStatsComputer.computeReqStatsForFile(file.absolutePath)
                result shouldNotBe null

                result!!.apply {
                    if (reqMedian == 0.0 || reqMean == 0.0) {
                        throw AssertionError(
                            "🐞 BUG DETECTED: ${file.name} returned zero Req!\n" +
                            "  This typically indicates:\n" +
                            "  1. Missing current data (I = 0)\n" +
                            "  2. Failed column mapping\n" +
                            "  3. All points filtered out\n" +
                            "  Points analyzed: $nPoints (should be > 50)"
                        )
                    }

                    reqMedian shouldBeGreaterThan 0.0
                    reqMean shouldBeGreaterThan 0.0
                }
            }
        }
    }

    context("Performance and data quality") {
        test("should analyze files in reasonable time") {
            if (!wheellogFile.exists() && !eucWorldFile.exists()) {
                println("⚠️ Skipping: no test files found")
                return@test
            }

            val files = listOf(wheellogFile, eucWorldFile).filter { it.exists() }

            files.forEach { file ->
                val startTime = System.currentTimeMillis()
                val result = ReqStatsComputer.computeReqStatsForFile(file.absolutePath)
                val duration = System.currentTimeMillis() - startTime

                result shouldNotBe null
                println("⏱️ ${file.name}: ${duration}ms for ${result!!.nPoints} points")

                // Sanity check: should not take unreasonably long
                // (allow 10s for large files in CI environments)
                duration shouldBe { it < 10000 }
            }
        }

        test("should extract reasonable battery pack parameters") {
            val files = listOf(wheellogFile, eucWorldFile).filter { it.exists() }

            files.forEach { file ->
                val result = ReqStatsComputer.computeReqStatsForFile(file.absolutePath)
                result shouldNotBe null

                result!!.apply {
                    // Typical EUC voltage ranges
                    vIdle shouldBe { it in 50.0..120.0 }  // 16s = ~67V, 24s = ~100V
                    
                    // Typical Req ranges for EUCs
                    reqMedian shouldBe { it in 0.001..0.5 }  // 1mΩ to 500mΩ
                    
                    // Ns should be reasonable
                    ns shouldNotBe null
                    ns!! shouldBe { it in 12..30 }  // Typical EUC packs: 16s to 24s
                }
            }
        }
    }
})
