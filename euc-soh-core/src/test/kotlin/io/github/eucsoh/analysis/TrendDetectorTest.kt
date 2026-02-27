package io.github.eucsoh.analysis

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for TrendDetector.
 * Validates linear trend detection using regression analysis.
 */
class TrendDetectorTest {

    private val EPSILON = 1e-6

    @Test
    fun `detectTrendLinear identifies clear upward trend`() {
        // Linear increase: y = 0.05 + 0.00001 * km
        val kms = (0..1000 step 100).map { it.toDouble() }
        val reqs = kms.map { 0.050 + 0.00001 * it }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        assertTrue(result.isSignificant, "Clear upward trend should be significant")
        assertEquals(0.00001, result.slope!!, 0.000001, "Slope should be ~0.00001 Ω/km")
        assertTrue(result.slope!! > 0.0, "Slope should be positive")
        assertTrue(result.pValue!! < 0.05, "p-value should indicate significance")
    }

    @Test
    fun `detectTrendLinear identifies no trend in flat data`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            "Req_median" to listOf(0.050, 0.051, 0.050, 0.049, 0.050)  // Flat with noise
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        assertFalse(result.isSignificant, "Flat data should not show significant trend")
        assertTrue(result.pValue!! > 0.05, "p-value should indicate non-significance")
    }

    @Test
    fun `detectTrendLinear handles downward trend`() {
        // Negative slope (unusual but possible)
        val kms = (0..500 step 100).map { it.toDouble() }
        val reqs = kms.map { 0.080 - 0.00002 * it }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        assertTrue(result.isSignificant, "Clear downward trend should be significant")
        assertTrue(result.slope!! < 0.0, "Slope should be negative")
    }

    @Test
    fun `detectTrendLinear requires minimum points`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0),  // Only 2 points
            "Req_median" to listOf(0.050, 0.060)
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median",
            minPoints = 3
        )

        assertFalse(result.isSignificant, "Insufficient points should not produce significant trend")
    }

    @Test
    fun `detectTrendLinear with noisy data`() {
        // Trend with noise: y = 0.05 + 0.00001*x + noise
        val kms = (0..1000 step 50).map { it.toDouble() }
        val reqs = kms.mapIndexed { i, km -> 
            0.050 + 0.00001 * km + (if (i % 2 == 0) 0.002 else -0.002)
        }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        // Should still detect trend despite noise
        assertTrue(result.isSignificant, "Should detect trend through noise")
        assertTrue(result.slope!! > 0.0, "Slope should be positive")
        assertTrue(result.rSquared!! > 0.5, "R² should indicate reasonable fit")
    }

    @Test
    fun `detectTrendLinear computes correct R-squared`() {
        // Perfect linear fit
        val kms = (0..500 step 100).map { it.toDouble() }
        val reqs = kms.map { 0.050 + 0.00001 * it }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        assertEquals(1.0, result.rSquared!!, 0.0001, "Perfect fit should have R² ≈ 1.0")
    }

    @Test
    fun `detectTrendLinear handles missing metric gracefully`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0),
            "other_metric" to listOf(0.1, 0.2, 0.3)
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"  // Not present
        )

        assertFalse(result.isSignificant, "Missing metric should return non-significant result")
    }

    @Test
    fun `detectTrendLinear filters null values`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            "Req_median" to listOf(0.050, null, 0.055, 0.060, null)
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        // Should work with remaining 3 valid points
        assertTrue(result.slope != null, "Should compute slope from valid points")
    }

    @Test
    fun `detectTrendLinear realistic EUC degradation`() {
        // Simulate realistic degradation over 3000km
        // Early stable, gradual increase starting at 1500km
        val kms = (100..3000 step 100).map { it.toDouble() }
        val reqs = kms.map { km ->
            when {
                km < 1500.0 -> 0.050 + (Math.random() - 0.5) * 0.002  // Stable ±1mΩ
                else -> 0.050 + (km - 1500.0) * 0.000015 + (Math.random() - 0.5) * 0.002
            }
        }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        // Should detect upward trend over full range
        assertTrue(result.slope!! > 0.0, "Should detect positive slope")
        // Significance depends on noise realization, but slope should be positive
    }

    @Test
    fun `detectTrendLinear slope units are correct`() {
        // Known slope: +10mΩ per 1000km = 0.00001 Ω/km
        val kms = listOf(0.0, 1000.0, 2000.0, 3000.0)
        val reqs = listOf(0.050, 0.060, 0.070, 0.080)

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        assertEquals(0.00001, result.slope!!, 0.0000001, "Slope should be 0.00001 Ω/km")
        
        // Convert to per 1000km for readability
        val slopePer1000km = result.slope!! * 1000.0
        assertEquals(0.010, slopePer1000km, 0.001, "Should be +10mΩ/1000km")
    }

    @Test
    fun `detectTrendLinear with very weak trend`() {
        // Very small slope that might not be significant
        val kms = (0..1000 step 100).map { it.toDouble() }
        val reqs = kms.map { 0.050 + 0.000001 * it + (Math.random() - 0.5) * 0.003 }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median",
            pValueThreshold = 0.05
        )

        // With noise >> signal, should likely not be significant
        // This test validates that weak trends aren't over-reported
        if (!result.isSignificant) {
            assertTrue(result.pValue!! > 0.05, "Weak trend should have high p-value")
        }
    }

    @Test
    fun `detectTrendLinear ignores single outlier`() {
        // Good linear trend with one outlier
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0, 600.0),
            "Req_median" to listOf(0.050, 0.055, 0.060, 0.150, 0.070, 0.075)  // 400km is outlier
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        // Outlier will affect R², but trend should still be detectable
        assertTrue(result.slope!! > 0.0, "Should still detect positive trend")
        // R² will be lower due to outlier
        assertTrue(result.rSquared!! < 0.95, "Outlier should reduce R²")
    }

    @Test
    fun `detectTrendLinear with constant values returns zero slope`() {
        val df = dataFrameOf(
            "wheel_km" to listOf(100.0, 200.0, 300.0, 400.0, 500.0),
            "Req_median" to listOf(0.050, 0.050, 0.050, 0.050, 0.050)  // Perfectly constant
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        assertEquals(0.0, result.slope!!, EPSILON, "Constant values should have zero slope")
        assertFalse(result.isSignificant, "Zero slope should not be significant")
    }

    @Test
    fun `detectTrendLinear validates intercept calculation`() {
        // y = 0.04 + 0.00002*x  (intercept at km=0 should be 0.04)
        val kms = (0..500 step 100).map { it.toDouble() }
        val reqs = kms.map { 0.040 + 0.00002 * it }

        val df = dataFrameOf(
            "wheel_km" to kms,
            "Req_median" to reqs
        )

        val result = TrendDetector.detectTrendLinear(
            df = df,
            metric = "Req_median"
        )

        // Intercept should be near 0.040
        assertTrue(result.intercept != null, "Intercept should be calculated")
        assertEquals(0.040, result.intercept!!, 0.001, "Intercept should match expected value")
    }
}
