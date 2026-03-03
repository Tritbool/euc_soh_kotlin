package io.github.eucsoh.analysis

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*
import kotlin.math.max

/**
 * CUSUM (Cumulative Sum) detector for regime changes.
 * Port of cusum_detection() from soh_core_en.py.
 */
object CUSUMDetector {

    data class CUSUMResult(
        val alarmIndices: List<Int>,
        val muRef: Double?,
        val sigmaRef: Double?
    )

    /**
     * Unilateral CUSUM to detect upward shift in a metric.
     * 
     * @param refKmMax Maximum km for reference regime (e.g. first 30% of logs)
     * @param testKmMin Minimum km to start testing
     * @param kSigma Slack parameter (multiple of sigma)
     * @param hSigma Threshold (multiple of sigma)
     * @param cooldownKm Refractory period after alarm (km)
     * @param relativeJumpMin Minimum relative jump during cooldown to trigger
     * @param hSigmaCooldown Higher threshold during cooldown
     */
    fun detectCUSUM(
        df: DataFrame<*>,
        metric: String,
        refKmMax: Double? = null,
        testKmMin: Double? = null,
        kSigma: Double = 1.0,
        hSigma: Double = 5.0,
        cooldownKm: Double = 500.0,
        relativeJumpMin: Double = 0.3,
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

        // Reference regime
        val dfRef = if (refKmMax != null) {
            dfClean.filter { (it["wheel_km"] as Double) <= refKmMax }
        } else {
            dfClean
        }

        if (dfRef.rowsCount() < 3) {
            return CUSUMResult(emptyList(), null, null)
        }

        val yRef = dfRef[metric].values()
            .filterIsInstance<Number>()
            .map { it.toDouble() }
            .sorted()

        val nRef = maxOf(3, (yRef.size * 0.5).toInt())
        val yRefOpt = yRef.take(nRef)

        val muRef = yRefOpt.average()
        val sigmaRef = kotlin.math.sqrt(
            yRefOpt.sumOf { (it - muRef) * (it - muRef) } / (yRefOpt.size - 1)
        )

        if (sigmaRef == 0.0) {
            return CUSUMResult(emptyList(), muRef, sigmaRef)
        }

        // Test regime
        val dfTest = if (testKmMin != null) {
            dfClean.filter { (it["wheel_km"] as Double) >= testKmMin }
        } else {
            dfClean
        }

        if (dfTest.rowsCount() < 3) {
            return CUSUMResult(emptyList(), muRef, sigmaRef)
        }

        val yTest = dfTest[metric].values().map { (it as Number).toDouble() }
        val kmTest = dfTest["wheel_km"].values().map { (it as Number).toDouble() }

        val k = kSigma * sigmaRef
        val hNormal = hSigma * sigmaRef
        val hCooldown = hSigmaCooldown * sigmaRef

        var s = 0.0
        val alarmIndices = mutableListOf<Int>()
        var inCooldown = false
        var cooldownEndKm: Double? = null
        var regimeMu = muRef

        for (i in yTest.indices) {
            val value = yTest[i]
            val km = kmTest[i]

            // Exit cooldown if past end
            if (inCooldown && cooldownEndKm != null && km > cooldownEndKm) {
                inCooldown = false
                cooldownEndKm = null
                s = 0.0
            }

            val h = if (inCooldown) hCooldown else hNormal

            s = max(0.0, s + (value - regimeMu - k))

            var triggered = s >= h

            // Additional check during cooldown
            if (triggered && inCooldown && regimeMu > 0.0) {
                val relJump = (value - regimeMu) / regimeMu
                if (relJump < relativeJumpMin) {
                    triggered = false
                    s *= 0.5 // Dampen
                }
            }

            if (triggered) {
                alarmIndices.add(i)

                // Update regime
                val j0 = maxOf(0, i - 4)
                val regimeWindow = yTest.subList(j0, i + 1)
                regimeMu = regimeWindow.average()

                inCooldown = true
                cooldownEndKm = km + cooldownKm
                s = 0.0
            }
        }

        return CUSUMResult(alarmIndices, muRef, sigmaRef)
    }
}
