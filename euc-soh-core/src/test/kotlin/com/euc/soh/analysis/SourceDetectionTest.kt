// SourceDetectionTest.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SourceDetectionTest : FunSpec({

    test("detectSource identifies EUC World format with datetime and distance_total") {
        val cols = setOf("datetime", "distance_total", "voltage", "current", "speed")
        SourceDetector.detectSource(cols) shouldBe LogSource.EUC_WORLD
    }

    test("detectSource identifies WheelLog format with date, time and totaldistance") {
        val cols = setOf("date", "time", "totaldistance", "voltage", "current", "speed")
        SourceDetector.detectSource(cols) shouldBe LogSource.WHEELLOG
    }

    test("detectSource returns EUC_WORLD when only datetime column present") {
        val cols = setOf("datetime", "voltage", "current", "speed")
        SourceDetector.detectSource(cols) shouldBe LogSource.EUC_WORLD
    }

    test("detectSource falls back to WHEELLOG for unknown columns") {
        val cols = setOf("voltage", "current", "speed")
        SourceDetector.detectSource(cols) shouldBe LogSource.WHEELLOG
    }

    test("detectSource is case-insensitive") {
        val cols = setOf("DateTime", "Distance_Total", "Voltage")
        SourceDetector.detectSource(cols) shouldBe LogSource.EUC_WORLD
    }

    test("detectSourceString returns python-compatible string for EUC World") {
        val cols = setOf("datetime", "distance_total", "voltage")
        SourceDetector.detectSourceString(cols) shouldBe "euc_world"
    }

    test("detectSourceString returns python-compatible string for WheelLog") {
        val cols = setOf("date", "time", "totaldistance", "voltage")
        SourceDetector.detectSourceString(cols) shouldBe "wheellog"
    }

    test("detectSource from list works same as from set") {
        val cols = listOf("datetime", "distance_total", "voltage")
        SourceDetector.detectSource(cols) shouldBe LogSource.EUC_WORLD
    }
})
