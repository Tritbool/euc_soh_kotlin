package io.github.tritbool.euc_soh.core.analysis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class ThermalNormalizationTest : FunSpec({

    test("normalizeRBattTo25C returns same value at 25°C") {
        val r25 = normalizeRBattTo25C(0.1, 25.0)
        abs(r25 - 0.1) shouldBeLessThan 0.001
    }

    test("normalizeRBattTo25C increases resistance for cold temperature") {
        val rCold = 0.15 // Measured at 0°C
        val r25 = normalizeRBattTo25C(rCold, 0.0)
        // At cold temp, battery has higher R -> normalized to 25°C should be lower
        r25 shouldBeLessThan rCold
    }

    test("normalizeRBattTo25C decreases resistance for hot temperature") {
        val rHot = 0.08 // Measured at 50°C
        val r25 = normalizeRBattTo25C(rHot, 50.0)
        // At hot temp, battery has lower R -> normalized to 25°C should be higher
        r25 shouldBeGreaterThan rHot
    }

    test("normalizeRBattTo25C handles null values") {
        normalizeRBattTo25C(null, 25.0) shouldBe 0.0
        normalizeRBattTo25C(0.1, null) shouldBe 0.1
    }

    test("normalizeRBattTo25C handles NaN") {
        normalizeRBattTo25C(Double.NaN, 25.0).isNaN() shouldBe true
        normalizeRBattTo25C(0.1, Double.NaN).isNaN() shouldBe true
    }

    test("calibrateEaFromData returns default for insufficient data") {
        val r = doubleArrayOf(0.1, 0.11)
        val t = doubleArrayOf(20.0, 25.0)
        calibrateEaFromData(r, t) shouldBe 20000.0
    }

    test("calibrateEaFromData returns default for narrow temperature span") {
        val r = doubleArrayOf(0.1, 0.101, 0.102, 0.103, 0.104)
        val t = doubleArrayOf(24.0, 24.5, 25.0, 25.5, 26.0)
        calibrateEaFromData(r, t) shouldBe 20000.0
    }

    test("calibrateEaFromData calibrates from valid data") {
        // Simulate Arrhenius behavior: R increases with lower T
        val temps = doubleArrayOf(0.0, 10.0, 20.0, 30.0, 40.0, 50.0)
        val baseR = 0.1
        val trueEa = 25000.0
        val resistances = temps.map { t ->
            val rNorm = baseR
            val tK = t + 273.15
            val tRefK = 25.0 + 273.15
            val factor = kotlin.math.exp((trueEa / 8.314) * (1.0 / tK - 1.0 / tRefK))
            rNorm * factor
        }.toDoubleArray()

        val calibrated = calibrateEaFromData(resistances, temps)
        // Should be close to trueEa (within 10 kJ/mol tolerance)
        abs(calibrated - trueEa) shouldBeLessThan 10000.0
    }
})
