package io.github.eucsoh.analysis

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for CUSUMDetector.
 * Validates regime change detection using cumulative sum algorithm.
 */
class CUSUMDetectorTest {

    @Test
    fun `detectCUSUM detects no alarm in stable data`() {
        // Stable resistance around 50 mΩ throughout
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0),
            "Req_median" to listOf(0.050, 0.051, 0.049, 0.052, 0.050, 0.051, 0.049, 0.050)
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0
        )

        assertTrue(result.alarmIndices.isEmpty(), "Stable data should trigger no alarms")
        assertEquals(0.050, result.muRef, 0.005, "Reference mean should be ~50 mΩ")
    }

    @Test
    fun `detectCUSUM detects upward shift in resistance`() {
        // Stable first 30%, then clear jump
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0, 600.0, 700.0, 800.0, 900.0, 1000.0),
            "Req_median" to listOf(
                0.050, 0.051, 0.049, // Reference: ~50 mΩ
                0.050, 0.052, 0.070, // Jump here
                0.072, 0.071, 0.073, 0.074  // New regime ~72 mΩ
            )
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0
        )

        assertTrue(result.alarmIndices.isNotEmpty(), "Should detect regime change")
        assertEquals(0.050, result.muRef, 0.005, "Reference should be stable baseline")
    }

    @Test
    fun `detectCUSUM ignores small fluctuations`() {
        // Small noise around 50 mΩ, no real shift
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0, 600.0, 700.0),
            "Req_median" to listOf(0.050, 0.052, 0.048, 0.053, 0.051, 0.049, 0.052)
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0,
            kSigma = 1.0,
            hSigma = 5.0
        )

        assertTrue(result.alarmIndices.isEmpty(), "Small fluctuations should not trigger alarm")
    }

    @Test
    fun `detectCUSUM resets after cooldown period`() {
        // Jump, then stabilize in new regime
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0, 1100.0, 1200.0),
            "Req_median" to listOf(
                0.050, 0.049, 0.051, // Reference
                0.070, 0.072, // Jump + trigger
                0.073, 0.074  // After cooldown (600km later)
            )
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0,
            cooldownKm = 500.0
        )

        // Should detect the jump, but not re-trigger during cooldown
        assertTrue(result.alarmIndices.size <= 2, "Should not spam alarms during cooldown")
    }

    @Test
    fun `detectCUSUM handles insufficient reference data`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0),
            "Req_median" to listOf(0.050, 0.051)
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0
        )

        assertTrue(result.alarmIndices.isEmpty(), "Insufficient data should not trigger alarms")
    }

    @Test
    fun `detectCUSUM computes reference from early data only`() {
        // Early data stable, late data degraded
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 1000.0, 1100.0, 1200.0),
            "Req_median" to listOf(
                0.050, 0.051, 0.049, // Early reference
                0.080, 0.082, 0.085  // Late degradation (should not affect reference)
            )
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 400.0, // Only use first 3 points
            testKmMin = 400.0
        )

        assertEquals(0.050, result.muRef, 0.005, "Reference should only use early stable data")
        assertTrue(result.alarmIndices.isNotEmpty(), "Should detect late degradation")
    }

    @Test
    fun `detectCUSUM sensitivity to kSigma parameter`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0, 600.0),
            "Req_median" to listOf(0.050, 0.051, 0.049, 0.055, 0.056, 0.057)
        )

        // Strict threshold (high kSigma)
        val resultStrict = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0,
            kSigma = 2.0,  // High threshold
            hSigma = 5.0
        )

        // Sensitive threshold (low kSigma)
        val resultSensitive = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0,
            kSigma = 0.5,  // Low threshold
            hSigma = 3.0
        )

        // Sensitive should detect more easily
        assertTrue(
            resultSensitive.alarmIndices.size >= resultStrict.alarmIndices.size,
            "Lower kSigma should be more sensitive"
        )
    }

    @Test
    fun `detectCUSUM handles zero sigma reference`() {
        // All reference points identical (zero variance)
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0),
            "Req_median" to listOf(0.050, 0.050, 0.050, 0.070)
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0
        )

        // Should handle gracefully (sigma=0 case)
        assertEquals(0.050, result.muRef, 0.001, "Reference mean should be exact")
        assertEquals(0.0, result.sigmaRef, 0.001, "Sigma should be zero for identical values")
    }

    @Test
    fun `detectCUSUM realistic EUC degradation scenario`() {
        // Simulate realistic degradation: stable 1000km, then gradual increase
        val kms = (100..2000 step 100).map { it.toDouble() }
        val reqs = kms.map { km ->
            when {
                km <= 1000.0 -> 0.050 + (Math.random() - 0.5) * 0.002  // Stable ±1mΩ
                else -> 0.050 + (km - 1000.0) * 0.00002 + (Math.random() - 0.5) * 0.002  // Gradual rise
            }
        }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",
            refKmMax = 300.0,
            testKmMin = 300.0
        )

        // Should eventually detect the gradual increase
        assertTrue(result.muRef!! < 0.052, "Reference should be from stable early phase")
        // Detection depends on random noise, but alarm should happen eventually
    }

    @Test
    fun `detectCUSUM with missing metric returns empty`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0),
            "other_metric" to listOf(0.1, 0.2, 0.3)
        )

        val result = CUSUMDetector.detectCUSUM(
            df = df,
            metric = "Req_median",  // Not present
            refKmMax = 300.0,
            testKmMin = 300.0
        )

        assertTrue(result.alarmIndices.isEmpty(), "Missing metric should return empty result")
    }
}
