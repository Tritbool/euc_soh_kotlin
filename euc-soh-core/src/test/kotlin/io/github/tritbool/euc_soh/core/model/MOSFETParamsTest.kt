package io.github.tritbool.euc_soh.core.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class MOSFETParamsTest : FunSpec({

    test("rMosfetAtTemp returns base resistance at 25°C") {
        val params = MOSFETParams(rDsOn25cTotal = 0.05, rWiring = 0.01)
        val r25 = params.rMosfetAtTemp(25.0)
        abs(r25 - 0.06) shouldBeLessThan 0.0001
    }

    test("rMosfetAtTemp increases resistance at higher temperature") {
        val params = MOSFETParams(rDsOn25cTotal = 0.05, tempCoeffRel = 0.01, rWiring = 0.0)
        val r50 = params.rMosfetAtTemp(50.0)
        // Delta = 25°C, coeff = 0.01 -> factor = 1 + 0.01*25 = 1.25
        // Expected: 0.05 * 1.25 = 0.0625
        abs(r50 - 0.0625) shouldBeLessThan 0.0001
    }

    test("rMosfetAtTemp decreases resistance at lower temperature") {
        val params = MOSFETParams(rDsOn25cTotal = 0.05, tempCoeffRel = 0.01, rWiring = 0.0)
        val r0 = params.rMosfetAtTemp(0.0)
        // Delta = -25°C, coeff = 0.01 -> factor = 1 + 0.01*(-25) = 0.75
        // Expected: 0.05 * 0.75 = 0.0375
        abs(r0 - 0.0375) shouldBeLessThan 0.0001
    }

    test("rMosfetAtTemp handles null temperature as 25°C") {
        val params = MOSFETParams(rDsOn25cTotal = 0.05, rWiring = 0.01)
        val r = params.rMosfetAtTemp(null)
        r shouldBe 0.06
    }

    test("rMosfetAtTemp never returns negative resistance") {
        val params = MOSFETParams(rDsOn25cTotal = 0.01, tempCoeffRel = 0.01, rWiring = -0.02)
        val r = params.rMosfetAtTemp(25.0)
        r shouldBeGreaterThan 0.0
    }
})
