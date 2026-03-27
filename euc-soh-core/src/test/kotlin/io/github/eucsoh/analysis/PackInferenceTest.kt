package io.github.eucsoh.analysis

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for PackInference.
 * Validates battery pack configuration detection (Ns, V_nominal, R_pack).
 */
class PackInferenceTest {

    @Test
    fun `inferPackConfig detects 20S pack from typical voltages`() {
        val df = dataFrameOf(
            "Ns" to listOf(20, 20, 20, 20),
            "soc_ref_ok" to listOf(true, true, true, true)
        )

        val (ns, vNom) = PackInference.inferPackConfig(df)

        assertEquals(20, ns, "Should detect 20S configuration")
        assertEquals(74.0, vNom, "V_nominal should be 20 * 3.7V = 74V")
    }

    @Test
    fun `inferPackConfig detects 24S pack`() {
        val df = dataFrameOf(
            "Ns" to listOf(24, 24, 24),
            "soc_ref_ok" to listOf(true, true, true)
        )

        val (ns, vNom) = PackInference.inferPackConfig(df)

        assertEquals(24, ns, "Should detect 24S configuration")
        assertEquals(88.8, vNom?: Double.MAX_VALUE, 0.01, "V_nominal should be 24 * 3.7V")
    }

    @Test
    fun `inferPackConfig rounds to nearest known series`() {
        // Simulate slight variation: 19.8, 20.1, 20.0 -> should round to 20S
        val df = dataFrameOf(
            "Ns" to listOf(19.8, 20.1, 20.0, 20.2),
            "soc_ref_ok" to listOf(true, true, true, true)
        )

        val (ns, vNom) = PackInference.inferPackConfig(df)

        assertEquals(20, ns, "Should round to 20S (nearest known series)")
    }

    @Test
    fun `inferPackConfig with empty valid data returns null`() {
        val df = dataFrameOf(
            "Ns" to listOf<Int>(),
            "soc_ref_ok" to listOf<Boolean>()
        )

        val (ns, vNom) = PackInference.inferPackConfig(df)

        assertNull(ns, "Empty data should return null Ns")
        assertNull(vNom, "Empty data should return null V_nominal")
    }

    @Test
    fun `inferPackConfig ignores invalid SoC references`() {
        val df = dataFrameOf(
            "Ns" to listOf(24, 99, 24, 24), // One outlier
            "soc_ref_ok" to listOf(true, false, true, true) // Outlier marked invalid
        )

        val (ns, vNom) = PackInference.inferPackConfig(df)

        assertEquals(24, ns, "Should ignore invalid SoC reference and detect 24S")
    }

    @Test
    fun `inferPackConfig detects 16S small pack`() {
        val df = dataFrameOf(
            "Ns" to listOf(16, 16, 16),
            "soc_ref_ok" to listOf(true, true, true)
        )

        val (ns, vNom) = PackInference.inferPackConfig(df)

        assertEquals(16, ns, "Should detect 16S configuration")
        assertEquals(59.2, vNom?: Double.MAX_VALUE, 0.01, "V_nominal should be 16 * 3.7V")
    }

    @Test
    fun `inferPackConfig detects 30S high-voltage pack`() {
        val df = dataFrameOf(
            "Ns" to listOf(30, 30, 30),
            "soc_ref_ok" to listOf(true, true, true)
        )

        val (ns, vNom) = PackInference.inferPackConfig(df)

        assertEquals(30, ns, "Should detect 30S configuration")
        assertEquals(111.0, vNom?: Double.MAX_VALUE, 0.01, "V_nominal should be 30 * 3.7V")
    }

    @Test
    fun `computePackNominalResistance for 20S pack`() {
        val ns = 20
        val vNom = 74.0

        val rPack = PackInference.computePackNominalResistance(ns, vNom)

        // 20S in range 80-110V -> ~22 mΩ/cell -> 20 * 0.022 = 0.44Ω
        assertEquals(0.44, rPack!!, 0.01, "20S pack should have ~360 mΩ nominal resistance")
    }

    @Test
    fun `computePackNominalResistance for 16S small pack`() {
        val ns = 16
        val vNom = 59.2

        val rPack = PackInference.computePackNominalResistance(ns, vNom)

        // 16S < 80V -> ~22 mΩ/cell -> 16 * 0.022 = 0.352Ω
        assertEquals(0.352, rPack!!, 0.01, "16S pack should have ~352 mΩ nominal resistance")
    }

    @Test
    fun `computePackNominalResistance for 30S high-voltage pack`() {
        val ns = 30
        val vNom = 111.0

        val rPack = PackInference.computePackNominalResistance(ns, vNom)

        // 30S in 110-150V range -> ~14 mΩ/cell -> 30 * 0.014 = 0.42Ω
        assertEquals(0.42, rPack!!, 0.01, "30S pack should have ~420 mΩ nominal resistance")
    }

    @Test
    fun `computePackNominalResistance with null Ns returns null`() {
        val rPack = PackInference.computePackNominalResistance(null, 74.0)
        assertNull(rPack, "Null Ns should return null R_pack")
    }

    @Test
    fun `chooseBatteryCurrentWindow for 16S pack`() {
        val (iMin, iMax) = PackInference.chooseBatteryCurrentWindow(16)

        assertEquals(6.0, iMin, "16S should use 6A min current")
        assertEquals(90.0, iMax, "16S should use 90A max current")
    }

    @Test
    fun `chooseBatteryCurrentWindow for 20S pack`() {
        val (iMin, iMax) = PackInference.chooseBatteryCurrentWindow(20)

        assertEquals(12.0, iMin, "20S should use 12A min current")
        assertEquals(150.0, iMax, "20S should use 150A max current")
    }

    @Test
    fun `chooseBatteryCurrentWindow for 24S pack`() {
        val (iMin, iMax) = PackInference.chooseBatteryCurrentWindow(24)

        assertEquals(12.0, iMin, "24S should use 12A min current")
        assertEquals(150.0, iMax, "24S should use 150A max current")
    }

    @Test
    fun `chooseBatteryCurrentWindow for 30S pack`() {
        val (iMin, iMax) = PackInference.chooseBatteryCurrentWindow(30)

        assertEquals(10.0, iMin, "30S should use 10A min current")
        assertEquals(180.0, iMax, "30S should use 180A max current")
    }

    @Test
    fun `chooseBatteryCurrentWindow for 50S high-power pack`() {
        val (iMin, iMax) = PackInference.chooseBatteryCurrentWindow(50)

        assertEquals(11.0, iMin, "50+ should use 11A min current")
        assertEquals(200.0, iMax, "50S should use 200A max current")
    }

    @Test
    fun `chooseBatteryCurrentWindow for 57S+ high-power pack`() {
        val (iMin, iMax) = PackInference.chooseBatteryCurrentWindow(57)

        assertEquals(3.0, iMin, "57S+ should use 3A min current")
        assertEquals(200.0, iMax, "57S+ should use 200A max current")
    }

    @Test
    fun `chooseBatteryCurrentWindow for null Ns uses default`() {
        val (iMin, iMax) = PackInference.chooseBatteryCurrentWindow(null)

        assertEquals(10.0, iMin, "Null Ns should use default 10A min")
        assertEquals(80.0, iMax, "Null Ns should use default 80A max")
    }

    @Test
    fun `estimateCellResistanceMOhm for different voltages`() {
        // Test internal helper via computePackNominalResistance
        
        // Low voltage (< 80V): 22 mΩ/cell
        val r16S = PackInference.computePackNominalResistance(16, 59.2)!!
        assertTrue(r16S > 0.3, "16S should have high resistance per cell")

        // Mid voltage (80-110V): 18 mΩ/cell
        val r20S = PackInference.computePackNominalResistance(20, 74.0)!!
        assertTrue(r20S in 0.4..0.45, "20S should have medium resistance")

        // High voltage (110-150V): 14 mΩ/cell
        val r30S = PackInference.computePackNominalResistance(30, 111.0)!!
        assertTrue(r30S in 0.40..0.45, "30S should have lower per-cell resistance")

        // Very high voltage (>150V): 12 mΩ/cell
        val r50S = PackInference.computePackNominalResistance(50, 185.0)!!
        assertTrue(r50S in 0.58..0.62, "50S should have lowest per-cell resistance")
    }
}
