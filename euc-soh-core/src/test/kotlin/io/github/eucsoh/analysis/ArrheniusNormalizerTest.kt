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

package io.github.eucsoh.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.abs

/**
 * Unit tests for ArrheniusNormalizer.
 * Validates thermal normalization using Arrhenius law for Li-ion batteries.
 */
class ArrheniusNormalizerTest {

    private val EPSILON = 1e-6

    @Test
    fun `normalizeRBattTo25C at 25C returns same value`() {
        val rMeasured = 0.050 // 50 mΩ
        val tempC = 25.0
        val ea = 20000.0 // 20 kJ/mol

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, ea)

        // At reference temperature, normalized value should equal measured value
        assertEquals(rMeasured, result, EPSILON, "Resistance at 25°C should not change")
    }

    @Test
    fun `normalizeRBattTo25C at higher temperature reduces resistance`() {
        val rMeasured = 0.060 // 60 mΩ at 40°C
        val tempC = 40.0
        val ea = 20000.0

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, ea)

        // Higher temperature -> measured R is lower -> normalized R@25C should be higher
        assertTrue(result > rMeasured, "Normalized R@25C should be higher than measured R@40C")
    }

    @Test
    fun `normalizeRBattTo25C at lower temperature increases resistance`() {
        val rMeasured = 0.080 // 80 mΩ at 10°C
        val tempC = 10.0
        val ea = 20000.0

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, ea)

        // Lower temperature -> measured R is higher -> normalized R@25C should be lower
        assertTrue(result < rMeasured, "Normalized R@25C should be lower than measured R@10C")
    }

    @Test
    fun `normalizeRBattTo25C with null temperature returns measured value`() {
        val rMeasured = 0.055
        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, null, 20000.0)

        assertEquals(rMeasured, result, EPSILON, "Null temperature should return measured value")
    }

    @Test
    fun `normalizeRBattTo25C is symmetric`() {
        // Test: normalize from T1 to 25C, then denormalize back to T1
        val rAt25C = 0.050
        val tempTest = 35.0
        val ea = 20000.0

        // Simulate measurement at tempTest
        val rGas = 8.314
        val t25K = 25.0 + 273.15
        val tTestK = tempTest + 273.15
        val exponent = (ea / rGas) * (1.0 / tTestK - 1.0 / t25K)
        val rAtTemp = rAt25C * kotlin.math.exp(exponent)

        // Normalize back
        val normalized = ArrheniusNormalizer.normalizeRBattTo25C(rAtTemp, tempTest, ea)

        assertEquals(rAt25C, normalized, 1e-4, "Normalization should be symmetric")
    }

    @Test
    fun `normalizeRBattTo25C handles extreme cold`() {
        val rMeasured = 0.150 // Very high resistance at -10°C
        val tempC = -10.0
        val ea = 20000.0

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, ea)

        // Should normalize to much lower value
        assertTrue(result > 0.0, "Result should be positive")
        assertTrue(result < rMeasured, "Normalized R should be much lower than cold measurement")
    }

    @Test
    fun `normalizeRBattTo25C handles high temperature`() {
        val rMeasured = 0.035 // Low resistance at 60°C
        val tempC = 60.0
        val ea = 20000.0

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, ea)

        // Should normalize to higher value
        assertTrue(result > rMeasured, "Normalized R@25C should be higher than hot measurement")
    }

    @Test
    fun `normalizeRBattTo25C different Ea values change sensitivity`() {
        val rMeasured = 0.060
        val tempC = 40.0

        val resultLowEa = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, 10000.0)
        val resultHighEa = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, 40000.0)

        // Higher Ea = stronger temperature dependence
        val deltaLow = abs(resultLowEa - rMeasured)
        val deltaHigh = abs(resultHighEa - rMeasured)

        assertTrue(deltaHigh > deltaLow, "Higher Ea should produce larger normalization correction")
    }

    @Test
    fun `normalizeRBattTo25C returns zero for zero input`() {
        val result = ArrheniusNormalizer.normalizeRBattTo25C(0.0, 30.0, 20000.0)
        assertEquals(0.0, result, EPSILON, "Zero input should return zero")
    }

    @Test
    fun `normalizeRBattTo25C clamps negative results to zero`() {
        // Edge case: if formula somehow produces negative (shouldn't happen in practice)
        val result = ArrheniusNormalizer.normalizeRBattTo25C(-0.01, 25.0, 20000.0)
        assertTrue(result >= 0.0, "Result should never be negative")
    }

    @Test
    fun `normalizeRBattTo25C typical EUC scenario`() {
        // Real-world scenario: battery measured at 35°C with R=52mΩ
        val rMeasured = 0.052
        val tempC = 35.0
        val ea = 20000.0

        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, tempC, ea)

        // Expected: around 55-58 mΩ normalized to 25°C
        assertTrue(result in rMeasured..rMeasured*1.3,
            "Normalized R@25C should be in realistic range, got $result")
    }
}
