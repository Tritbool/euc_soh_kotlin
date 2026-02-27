package io.github.eucsoh.analysis

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.max
import kotlin.math.sqrt

/**
 * CUSUM (Cumulative Sum) detector for upward regime shifts.
 * Port of cusum_detection() from soh_core_en.py
 */
object CUSUMDetector {

    data class CUSUMResult(
        val alarmIndices: List<Int>,
        val muRef: Double?,
        val sigmaRef: Double?
    )

    /**
     * Detects upward shift in metric using unilateral CUSUM.
     * 
     * @param metric column name to monitor
     * @param refKmMax reference zone end (km), null = use all data
     * @param testKmMin test zone start (km)
     * @param kSigma drift tolerance (multiples of sigma)
     * @param hSigma alarm threshold (multiples of sigma)
     * @param cooldownKm refractory period after alarm (km)
     * @param relativeJumpMin minimum relative jump during cooldown
     * @param hSigmaCooldown higher threshold during cooldown
     */
    fun detectCUSUM(
        df: DataFrame<*>,
        metric: String,
        refKmMax: Double? = null,
        testKmMin: Double? = null,
        kSigma: Double = 1.0,
        hSigma: Double = 5.0,
        cooldownKm: Double = 500.0,
        relativeJumpMin: Double = 0.05,
        hSigmaCooldown: Double = 6.0
    ): CUSUMResult {
        if (metric !in df.columnNames() || "wheel_km" !in df.columnNames()) {
            return CUSUMResult(emptyList(), null, null)
        }

        val dfClean = df.filter { 
            it[metric] != null && it["wheel_km"] != null &&
            !(it[metric] as Double).isNaN() && !(it["wheel_km"] as Double).isNaN()
        }.sortBy("wheel_km")

        if (dfClean.rowsCount() < 5) {
            return CUSUMResult(emptyList(), null, null)
        }

        // Reference zone
        val refDf = if (refKmMax != null) {
            dfClean.filter { (it["wheel_km"] as Double) <= refKmMax }
        } else {
            dfClean
        }

        if (refDf.rowsCount() < 3) {
            return CUSUMResult(emptyList(), null, null)
        }

        // Use best 50% of reference for baseline
        val yRef = refDf[metric].values()
            .filterIsInstance<Number>()
            .map { it.toDouble() }
            .sorted()
            .take(maxOf(3, (refDf.rowsCount() * 0.5).toInt()))

        val muRef = yRef.average()
        val sigmaRef = if (yRef.size > 1) {
            sqrt(yRef.map { (it - muRef) * (it - muRef) }.average())
        } else {
            0.0
        }

        if (sigmaRef == 0.0) {
            return CUSUMResult(emptyList(), muRef, sigmaRef)
        }

        // Test zone
        val testDf = if (testKmMin != null) {
            dfClean.filter { (it["wheel_km"] as Double) >= testKmMin }
        } else {
            dfClean
        }

        if (testDf.rowsCount() < 3) {
            return CUSUMResult(emptyList(), muRef, sigmaRef)
        }

        val y = testDf[metric].values().filterIsInstance<Number>().map { it.toDouble() }
        val km = testDf["wheel_km"].values().filterIsInstance<Number>().map { it.toDouble() }

        val k = kSigma * sigmaRef
        val hNormal = hSigma * sigmaRef
        val hCooldown = hSigmaCooldown * sigmaRef

        var S = 0.0
        val alarmIndices = mutableListOf<Int>()
        var inCooldown = false
        var cooldownEndKm: Double? = null
        var regimeMu = muRef

        for (i in y.indices) {
            val kmI = km[i]
            val valI = y[i]

            // Exit cooldown?
            if (inCooldown && cooldownEndKm != null && kmI > cooldownEndKm) {
                inCooldown = false
                cooldownEndKm = null
                S = 0.0
            }

            val hCurrent = if (inCooldown) hCooldown else hNormal

            // CUSUM update
            S = max(0.0, S + (valI - regimeMu - k))

            var triggered = S >= hCurrent

            // During cooldown, check relative jump
            if (triggered && inCooldown && regimeMu > 0) {
                val relJump = (valI - regimeMu) / regimeMu
                if (relJump < relativeJumpMin) {
                    triggered = false
                    S = 0.5 * S
                }
            }

            if (triggered) {
                alarmIndices.add(i)

                // Update regime mean from recent window
                val j0 = maxOf(0, i - 4)
                val regimeWindow = y.subList(j0, i + 1)
                regimeMu = regimeWindow.average()

                // Enter cooldown
                inCooldown = true
                cooldownEndKm = kmI + cooldownKm
                S = 0.0
            }
        }

        return CUSUMResult(alarmIndices, muRef, sigmaRef)
    }
}
