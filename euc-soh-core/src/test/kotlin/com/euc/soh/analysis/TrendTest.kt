// TrendTest.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogData
import com.euc.soh.model.LogSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TrendTest : FunSpec({

    fun makeLog(km: Double, reqMedian: Double) = LogData(
        fileName = "log_${km.toInt()}.csv",
        source = LogSource.EUC_WORLD,
        datetimeFirst = null,
        wheelKm = km,
        wheelKmSource = null,
        vIdle = 84.0,
        nsSeries = null,
        socRefOk = false,
        socRefVFull = null,
        nPoints = 100,
        reqMean = reqMedian,
        reqMedian = reqMedian,
        reqMedian25C = reqMedian,
        req95p = reqMedian + 0.01,
        sag95p = 2.0,
        sagMax = 3.0,
        vMinStrong = 70.0,
        iMax = 50.0,
        i95p = 40.0,
        tempBoardMax = 30.0,
        tempMotorMax = null,
        iPhase2Int = null,
        iPhaseMax = null,
        iPhase95p = null,
        rBattMedian = null,
        rBattMedian25C = null,
        rMosfetHot = null
    )

    test("detectTrendLinear returns not significant for too few logs") {
        val logs = listOf(makeLog(1000.0, 0.10), makeLog(2000.0, 0.12))
        val result = TrendAnalyzer.detectTrendLinear(logs, "Req_median")
        result.slope shouldBe null
        result.pValue shouldBe null
        result.isSignificant shouldBe false
    }

    test("detectTrendLinear returns not significant when km span is too small") {
        // All logs within 500 km span (< 1000 km threshold)
        val logs = (1..10).map { i -> makeLog(100.0 + i * 50.0, 0.10 + i * 0.005) }
        val result = TrendAnalyzer.detectTrendLinear(logs, "Req_median", kmMinSpan = 1000.0)
        result.slope shouldBe null
        result.isSignificant shouldBe false
    }

    test("detectTrendLinear detects positive significant slope on artificial upward data") {
        // Simulated degradation: Req increases linearly with km
        // slope = 0.00001 Ω/km -> at 10000 km span, Req increases by 0.1 Ω
        val logs = (0..19).map { i ->
            val km = i * 500.0         // 0, 500, 1000, ..., 9500 km
            val req = 0.10 + km * 1e-5  // 0.10 at 0 km, 0.195 at 9500 km
            makeLog(km, req)
        }
        val result = TrendAnalyzer.detectTrendLinear(logs, "Req_median")
        result.slope shouldNotBe null
        result.slope!! shouldBeGreaterThan 0.0
        result.isSignificant shouldBe true
    }

    test("detectTrendLinear returns non-significant for flat data") {
        // Flat Req: no drift
        val logs = (0..19).map { i ->
            makeLog(i * 500.0, 0.10)
        }
        val result = TrendAnalyzer.detectTrendLinear(logs, "Req_median")
        // slope near 0, p-value near 1 → not significant
        result.isSignificant shouldBe false
    }

    test("detectTrendLinear handles metric not available in logs") {
        val logs = (0..10).map { i -> makeLog(i * 500.0, 0.10 + i * 1e-5) }
        // "R_batt_median" not set in LogData (null), so no valid points
        val result = TrendAnalyzer.detectTrendLinear(logs, "R_batt_median")
        result.slope shouldBe null
        result.isSignificant shouldBe false
    }
})
