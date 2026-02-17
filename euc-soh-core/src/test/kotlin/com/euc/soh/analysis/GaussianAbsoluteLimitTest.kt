// GaussianAbsoluteLimitTest.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogData
import com.euc.soh.model.LogSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize

class GaussianAbsoluteLimitTest : FunSpec({
    test("detectAlarms should trigger absolute Req alarm when rPackNominal provided") {
        val rPackNominal = 0.4 // ohm
        val absLimit = rPackNominal * 1.8 // from Constants.ABS_REQ_FACTOR

        val logs = listOf(
            LogData(
                fileName = "good.csv",
                source = LogSource.EUC_WORLD,
                datetimeFirst = null,
                wheelKm = 6000.0,
                wheelKmSource = null,
                vIdle = 0.0,
                nsSeries = null,
                socRefOk = false,
                socRefVFull = null,
                nPoints = 10,
                reqMean = 0.1,
                reqMedian = 0.3,
                reqMedian25C = 0.3,
                req95p = 0.3,
                sag95p = 0.0,
                sagMax = 0.0,
                vMinStrong = 0.0,
                iMax = 0.0,
                i95p = 0.0,
                tempBoardMax = null,
                tempMotorMax = null,
                iPhase2Int = null,
                iPhaseMax = null,
                iPhase95p = null,
                rBattMedian = null,
                rBattMedian25C = null,
                rMosfetHot = null
            ),
            LogData(
                fileName = "bad.csv",
                source = LogSource.EUC_WORLD,
                datetimeFirst = null,
                wheelKm = 6000.0,
                wheelKmSource = null,
                vIdle = 0.0,
                nsSeries = null,
                socRefOk = false,
                socRefVFull = null,
                nPoints = 10,
                reqMean = 1.0,
                reqMedian = absLimit + 0.01, // slightly above computed abs limit
                reqMedian25C = absLimit + 0.01,
                req95p = absLimit + 0.01,
                sag95p = 0.0,
                sagMax = 0.0,
                vMinStrong = 0.0,
                iMax = 0.0,
                i95p = 0.0,
                tempBoardMax = null,
                tempMotorMax = null,
                iPhase2Int = null,
                iPhaseMax = null,
                iPhase95p = null,
                rBattMedian = null,
                rBattMedian25C = null,
                rMosfetHot = null
            )
        )

        val thresholds = emptyMap<String, com.euc.soh.model.Threshold>()
        val alarms = GaussianAlarmDetector.detectAlarms(logs, thresholds, checkAbsoluteLimit = true, rPackNominal = rPackNominal)

        // Expect at least one alarm (the bad.csv)
        alarms.shouldHaveSize(1)
    }
})
