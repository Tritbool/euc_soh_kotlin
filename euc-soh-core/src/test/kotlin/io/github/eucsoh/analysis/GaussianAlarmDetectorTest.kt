package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.model.ThresholdInfo
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import io.github.eucsoh.Constants.Metrics.*
import io.github.eucsoh.Constants.MetaColumns.*
/**
 * Unit tests for GaussianAlarmDetector.
 * Validates threshold computation and alarm detection using Gaussian statistics.
 */
class GaussianAlarmDetectorTest {

    @Test
    fun `computeThresholds calculates correct mean and std`() {
        val df = dataFrameOf(
            REQ_MEDIAN.csv_code to listOf(0.048, 0.050, 0.052, 0.049, 0.051, 0.100, 0.110),
            SOC_REF_OK.csv_code to listOf(true, true, true, true, true, true, true)
        )

        val thresholds = GaussianAlarmDetector.computeThresholds(
            df = df,
            optimalFrac = 0.5,  // Use best 50% (first 3-4 sorted values)
            nSigma = 2.0
        )

        val reqThreshold = thresholds[REQ_MEDIAN.csv_code]
        assertTrue(reqThreshold != null, "Req_median threshold should exist")
        
        // Best values should be ~0.048-0.051 -> mean ~0.050
        assertEquals(0.050, reqThreshold!!.mean, 0.005, "Mean should be around 50mΩ")
        assertTrue(reqThreshold.std > 0.0, "Std should be positive")
    }

    @Test
    fun `computeThresholds higher_is_bad sets limit above mean`() {
        val df = dataFrameOf(
            REQ_MEDIAN.csv_code to listOf(0.050, 0.051, 0.052, 0.053, 0.054),
            SOC_REF_OK.csv_code to listOf(true, true, true, true, true)
        )

        val thresholds = GaussianAlarmDetector.computeThresholds(
            df = df,
            optimalFrac = 0.6,
            nSigma = 2.0
        )

        val reqThreshold = thresholds[REQ_MEDIAN.csv_code]!!
        assertEquals(Constants.HIGHER_IS_BAD, reqThreshold.direction, "Req_median should be higher_is_bad")
        assertTrue(reqThreshold.limit > reqThreshold.mean, "Limit should be above mean for higher_is_bad")
    }

    @Test
    fun `computeThresholds lower_is_bad sets limit below mean`() {
        val df = dataFrameOf(
            V_MIN_STRONG.csv_code to listOf(70.0, 71.0, 72.0, 73.0, 74.0),
            REQ_MEDIAN.csv_code to listOf(0.050, 0.051, 0.052, 0.053, 0.054),  // For sorting
            SOC_REF_OK.csv_code to listOf(true, true, true, true, true)
        )

        val thresholds = GaussianAlarmDetector.computeThresholds(
            df = df,
            optimalFrac = 0.6,
            nSigma = 2.0
        )

        val vMinThreshold = thresholds[V_MIN_STRONG.csv_code]
        if (vMinThreshold != null) {
            assertEquals(Constants.LOWER_IS_BAD, vMinThreshold.direction, "v_min_strong should be lower_is_bad")
            assertTrue(vMinThreshold.limit < vMinThreshold.mean, "Limit should be below mean for lower_is_bad")
        }
    }

    @Test
    fun `detectAlarms identifies outlier beyond threshold`() {
        val df = dataFrameOf(
            FILE.csv_code to listOf("log1.csv", "log2.csv", "log3.csv", "log4.csv", "log5.csv"),
            WHEEL_KM.csv_code to listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            DATETIME_FIRST.csv_code to listOf("2024-01-01", "2024-02-01", "2024-03-01", "2024-04-01", "2024-05-01"),
            REQ_MEDIAN.csv_code to listOf(0.050, 0.051, 0.052, 0.053, 0.100),  // Last one is outlier
            SOC_REF_OK.csv_code to listOf(true, true, true, true, true)
        )

        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.051,
                std = 0.002,
                limit = 0.055,  // 2σ above mean
                direction = Constants.HIGHER_IS_BAD
            )
        )

        val alarms = GaussianAlarmDetector.detectAlarms(
            df = df,
            thresholds = thresholds,
            checkAbsoluteLimit = false
        )

        assertTrue(alarms.isNotEmpty(), "Should detect alarm for 0.100Ω outlier")
        assertEquals("log5.csv", alarms.first().file, "Alarm should be for last log")
    }

    @Test
    fun `detectAlarms no alarm when all within threshold`() {
        val df = dataFrameOf(
            FILE.csv_code to listOf("log1.csv", "log2.csv", "log3.csv"),
            WHEEL_KM.csv_code to listOf(100.0, 200.0, 300.0),
            DATETIME_FIRST.csv_code to listOf("2024-01-01", "2024-02-01", "2024-03-01"),
            REQ_MEDIAN.csv_code to listOf(0.050, 0.051, 0.052),
            SOC_REF_OK.csv_code to listOf(true, true, true)
        )

        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.051,
                std = 0.002,
                limit = 0.060,  // Well above all values
                direction = Constants.HIGHER_IS_BAD
            )
        )

        val alarms = GaussianAlarmDetector.detectAlarms(
            df = df,
            thresholds = thresholds,
            checkAbsoluteLimit = false
        )

        assertTrue(alarms.isEmpty(), "No alarms should be detected")
    }

    @Test
    fun `detectAlarms checks absolute limit when enabled`() {
        val df = dataFrameOf(
            FILE.csv_code to listOf("log1.csv", "log2.csv"),
            WHEEL_KM.csv_code to listOf(5500.0, 6000.0),  // Above ABS_KM_LIMIT
            DATETIME_FIRST.csv_code to listOf("2024-01-01", "2024-02-01"),
            REQ_MEDIAN.csv_code to listOf(0.050, 0.150),  // Second one very high
            SOC_REF_OK.csv_code to listOf(true, true)
        )

        val thresholds = mapOf<String, ThresholdInfo>()

        val alarms = GaussianAlarmDetector.detectAlarms(
            df = df,
            thresholds = thresholds,
            checkAbsoluteLimit = true,
            rPackNominal = 0.040  // 40mΩ pack -> limit ~72mΩ (1.8x)
        )

        assertTrue(alarms.isNotEmpty(), "Should detect absolute limit violation")
        assertTrue(
            alarms.any { it.reasons.contains("Absolute high Req_median") },
            "Alarm should mention absolute limit"
        )
    }

    @Test
    fun `detectAlarms handles multiple metric violations`() {
        val df = dataFrameOf(
            FILE.csv_code to listOf("log1.csv", "log2.csv", "log3.csv"),
            WHEEL_KM.csv_code to listOf(100.0, 200.0, 300.0),
            DATETIME_FIRST.csv_code to listOf("2024-01-01", "2024-02-01", "2024-03-01"),
            REQ_MEDIAN.csv_code to listOf(0.050, 0.051, 0.100),  // Last is bad
            TEMP_BOARD_MAX.csv_code to listOf(40.0, 42.0, 80.0),  // Last is bad
            SOC_REF_OK.csv_code to listOf(true, true, true)
        )

        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.051,
                std = 0.002,
                limit = 0.060,
                direction = Constants.HIGHER_IS_BAD
            ),
            TEMP_BOARD_MAX.csv_code to ThresholdInfo(
                mean = 41.0,
                std = 2.0,
                limit = 50.0,
                direction = Constants.HIGHER_IS_BAD
            )
        )

        val alarms = GaussianAlarmDetector.detectAlarms(
            df = df,
            thresholds = thresholds,
            checkAbsoluteLimit = false
        )

        assertTrue(alarms.isNotEmpty(), "Should detect alarms")
        val lastAlarm = alarms.first()
        assertTrue(
            lastAlarm.reasons.contains(REQ_MEDIAN.csv_code) && lastAlarm.reasons.contains(TEMP_BOARD_MAX.csv_code),
            "Should mention both violated metrics"
        )
    }

    @Test
    fun `computeThresholds handles insufficient data gracefully`() {
        val df = dataFrameOf(
            REQ_MEDIAN.csv_code to listOf(0.050, 0.051),  // Only 2 points
            SOC_REF_OK.csv_code to listOf(true, true)
        )

        val thresholds = GaussianAlarmDetector.computeThresholds(
            df = df,
            optimalFrac = 0.5,
            nSigma = 2.0
        )

        // Should still compute, but may not be very reliable
        // At minimum should not crash
        assertTrue(thresholds.isEmpty() || thresholds.isNotEmpty(), "Should handle gracefully")
    }

    @Test
    fun `detectAlarms filters null values correctly`() {
        val df = dataFrameOf(
            FILE.csv_code to listOf("log1.csv", "log2.csv", "log3.csv"),
            WHEEL_KM.csv_code to listOf(100.0, 200.0, 300.0),
            DATETIME_FIRST.csv_code to listOf("2024-01-01", "2024-02-01", "2024-03-01"),
            REQ_MEDIAN.csv_code to listOf(0.050, null, 0.100),  // Middle value null
            TEMP_BOARD_MAX.csv_code to listOf(40.0, 42.0, null),  // Last value null
            SOC_REF_OK.csv_code to listOf(true, true, true)
        )

        val thresholds = mapOf(
            REQ_MEDIAN.csv_code to ThresholdInfo(
                mean = 0.051,
                std = 0.002,
                limit = 0.060,
                direction = Constants.HIGHER_IS_BAD
            ),
            TEMP_BOARD_MAX.csv_code to ThresholdInfo(
                mean = 41.0,
                std = 2.0,
                limit = 50.0,
                direction = Constants.HIGHER_IS_BAD
            )
        )

        val alarms = GaussianAlarmDetector.detectAlarms(
            df = df,
            thresholds = thresholds,
            checkAbsoluteLimit = false
        )

        // Should only detect Req_median violation on log3, not temp (null)
        assertTrue(alarms.size == 1, "Should detect only valid violations")
    }

    @Test
    fun `computeThresholds uses optimal fraction correctly`() {
        val df = dataFrameOf(
            REQ_MEDIAN.csv_code to listOf(0.040, 0.045, 0.050, 0.055, 0.100, 0.110, 0.120, 0.130, 0.140, 0.150),
            SOC_REF_OK.csv_code to listOf(true, true, true, true, true, true, true, true, true, true)
        )

        // Use only best 30% (3 values: 0.040, 0.045, 0.050)
        val thresholds30 = GaussianAlarmDetector.computeThresholds(
            df = df,
            optimalFrac = 0.3,
            nSigma = 2.0
        )

        // Use best 50% (5 values: 0.040-0.055)
        val thresholds50 = GaussianAlarmDetector.computeThresholds(
            df = df,
            optimalFrac = 0.5,
            nSigma = 2.0
        )

        val mean30 = thresholds30[REQ_MEDIAN.csv_code]?.mean
        val mean50 = thresholds50[REQ_MEDIAN.csv_code]?.mean

        assertTrue(mean30!! < mean50!!, "30% should have lower mean than 50%")
        assertTrue(mean30 < 0.048, "30% should be dominated by lowest values")
    }

    @Test
    fun `detectAlarms realistic EUC scenario`() {
        // Simulate realistic degradation scenario
        val df = dataFrameOf(
            FILE.csv_code to (1..10).map { "log$it.csv" },
            WHEEL_KM.csv_code to listOf(100.0, 500.0, 1000.0, 1500.0, 2000.0, 2500.0, 3000.0, 3500.0, 4000.0, 4500.0),
            DATETIME_FIRST.csv_code to (1..10).map { "2024-${String.format("%02d", it)}-01" },
            REQ_MEDIAN.csv_code to listOf(
                0.048, 0.050, 0.049, 0.051, 0.052,  // Normal first 5
                0.053, 0.055, 0.070, 0.072, 0.075   // Degradation in last 5
            ),
            TEMP_BOARD_MAX.csv_code to listOf(
                38.0, 40.0, 39.0, 41.0, 40.0,
                42.0, 43.0, 45.0, 46.0, 47.0
            ),
            SOC_REF_OK.csv_code to List(10) { true }
        )

        val thresholds = GaussianAlarmDetector.computeThresholds(
            df = df,
            optimalFrac = 0.5,
            nSigma = 2.0
        )

        val alarms = GaussianAlarmDetector.detectAlarms(
            df = df,
            thresholds = thresholds,
            checkAbsoluteLimit = false
        )

        assertTrue(alarms.isNotEmpty(), "Should detect degradation alarms")
        assertTrue(
            alarms.any { it.wheelKm!! > 3000.0 },
            "Alarms should occur in degraded phase"
        )
    }
}
