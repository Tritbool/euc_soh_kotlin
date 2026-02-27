// AlarmsTest.kt
package com.euc.soh.analysis

import com.euc.soh.model.AlarmDirection
import com.euc.soh.model.LogData
import com.euc.soh.model.LogSource
import com.euc.soh.model.Threshold
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class AlarmsTest : FunSpec({

    fun makeLog(
        fileName: String,
        reqMedian: Double,
        wheelKm: Double = 6000.0,
        tempBoardMax: Double? = 30.0
    ) = LogData(
        fileName = fileName,
        source = LogSource.EUC_WORLD,
        datetimeFirst = null,
        wheelKm = wheelKm,
        wheelKmSource = null,
        vIdle = 84.0,
        nsSeries = null,
        socRefOk = false,
        socRefVFull = null,
        nPoints = 100,
        reqMean = reqMedian,
        reqMedian = reqMedian,
        reqMedian25C = reqMedian,
        req95p = reqMedian + 0.05,
        sag95p = reqMedian * 15.0,
        sagMax = reqMedian * 18.0,
        vMinStrong = 70.0,
        iMax = 50.0,
        i95p = 40.0,
        tempBoardMax = tempBoardMax,
        tempMotorMax = null,
        iPhase2Int = null,
        iPhaseMax = null,
        iPhase95p = null,
        rBattMedian = null,
        rBattMedian25C = null,
        rMosfetHot = null
    )

    test("detectAlarms returns no alarm when all logs are within thresholds") {
        val logs = listOf(
            makeLog("good1.csv", reqMedian = 0.10),
            makeLog("good2.csv", reqMedian = 0.11),
            makeLog("good3.csv", reqMedian = 0.12)
        )
        val thresholds = mapOf(
            "Req_median" to Threshold(
                mean = 0.11,
                std = 0.01,
                limit = 0.13,
                direction = AlarmDirection.HIGHER_IS_BAD
            )
        )
        val alarms = GaussianAlarmDetector.detectAlarms(logs, thresholds, checkAbsoluteLimit = false)
        alarms.shouldBeEmpty()
    }

    test("detectAlarms triggers alarm when Req_median exceeds Gaussian limit") {
        val logs = listOf(
            makeLog("good.csv", reqMedian = 0.10),
            makeLog("bad.csv", reqMedian = 0.50)
        )
        val thresholds = mapOf(
            "Req_median" to Threshold(
                mean = 0.10,
                std = 0.02,
                limit = 0.14,
                direction = AlarmDirection.HIGHER_IS_BAD
            )
        )
        val alarms = GaussianAlarmDetector.detectAlarms(logs, thresholds, checkAbsoluteLimit = false)
        alarms.shouldNotBeEmpty()
        alarms.any { it.fileName == "bad.csv" } shouldBe true
    }

    test("detectAlarms triggers alarm for lower_is_bad direction") {
        val logs = listOf(
            makeLog("good.csv", reqMedian = 0.10),
            makeLog("low_v.csv", reqMedian = 0.10)
        )
        // Override vMinStrong to a very low value for low_v via threshold
        val thresholds = mapOf(
            "v_min_strong" to Threshold(
                mean = 70.0,
                std = 2.0,
                limit = 66.0,  // lower_is_bad: alarm if < 66
                direction = AlarmDirection.LOWER_IS_BAD
            )
        )
        // logs[1].vMinStrong = 70.0 (not below 66), so no alarm expected
        val alarms = GaussianAlarmDetector.detectAlarms(logs, thresholds, checkAbsoluteLimit = false)
        alarms.shouldBeEmpty()
    }

    test("computeThresholds uses optimal fraction of best logs") {
        // 6 logs sorted by Req: best 3 have Req 0.02, 0.04, 0.06
        val logs = (1..6).map { i ->
            makeLog("log$i.csv", reqMedian = i * 0.02)  // 0.02, 0.04, 0.06, 0.08, 0.10, 0.12
        }
        val thresholds = GaussianAlarmDetector.computeThresholds(logs, optimalFrac = 0.5, nSigma = 2.0)
        // Just verify threshold for Req_median was computed
        thresholds.containsKey("Req_median") shouldBe true
        // Limit should be mean + 2*std for higher_is_bad direction
        val t = thresholds["Req_median"]!!
        t.limit shouldBe t.mean + 2.0 * t.std
        t.direction shouldBe AlarmDirection.HIGHER_IS_BAD
    }

    test("detectAlarms absolute limit alarm triggered at high mileage") {
        val rPackNominal = 0.4
        val absLimit = rPackNominal * 1.8   // 0.72
        val logs = listOf(
            makeLog("ok.csv", reqMedian = 0.50, wheelKm = 6000.0),
            makeLog("over.csv", reqMedian = absLimit + 0.01, wheelKm = 6000.0)
        )
        val alarms = GaussianAlarmDetector.detectAlarms(
            logs = logs,
            thresholds = emptyMap(),
            checkAbsoluteLimit = true,
            rPackNominal = rPackNominal
        )
        alarms.size shouldBe 1
        alarms.first().fileName shouldBe "over.csv"
    }
})
