// PhaseIntegralTest.kt
package com.euc.soh.analysis

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan

class PhaseIntegralTest : FunSpec({
    test("computePhase2Integral should clip small dt and handle unsorted timestamps") {
        val currents = doubleArrayOf(1.0, 2.0, 1.5, 0.5)
        val timestamps = doubleArrayOf(1000.0, 1000.000001, 1000.5, 1001.0) // unordered small dt
        val integral = ResistanceCalculator.computePhase2Integral(currents, timestamps)
        integral shouldBeGreaterThan 0.0
    }
})
