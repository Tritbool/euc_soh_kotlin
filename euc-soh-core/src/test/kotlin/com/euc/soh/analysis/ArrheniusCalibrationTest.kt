// ArrheniusCalibrationTest.kt
package com.euc.soh.analysis

import com.euc.soh.model.LogData
import com.euc.soh.model.LogSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class ArrheniusCalibrationTest : FunSpec({

    test("calibrateEaFromLogs should recover Ea from synthetic Arrhenius data") {
        // Prepare synthetic data with ln(R) = slope*(1/T) + intercept
        val slope = 2500.0 // arbitrary
        val intercept = -5.0

        val tempsC = listOf(10.0, 15.0, 20.0, 25.0, 30.0, 35.0)

        val logs = tempsC.mapIndexed { idx, tC ->
            val tK = tC + 273.15
            val r = kotlin.math.exp(slope * (1.0 / tK) + intercept)
            LogData(
                fileName = "log_$idx.csv",
                source = LogSource.EUC_WORLD,
                datetimeFirst = null,
                wheelKm = 1000.0 + idx * 100.0,
                wheelKmSource = null,
                vIdle = 0.0,
                nsSeries = null,
                socRefOk = false,
                socRefVFull = null,
                nPoints = 10,
                reqMean = r,
                reqMedian = r,
                reqMedian25C = r,
                req95p = r,
                sag95p = 0.0,
                sagMax = 0.0,
                vMinStrong = 0.0,
                iMax = 0.0,
                i95p = 0.0,
                tempBoardMax = tC,
                tempMotorMax = null,
                iPhase2Int = null,
                iPhaseMax = null,
                iPhase95p = null,
                rBattMedian = null,
                rBattMedian25C = null,
                rMosfetHot = null
            )
        }

        val ea = ArrheniusNormalizer.calibrateEaFromLogs(logs)
        val expectedEa = slope * 8.314

        // Allow some tolerance
        ea shouldBe (expectedEa plusOrMinus 10.0)
    }
})
