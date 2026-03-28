package io.github.eucsoh.model

import kotlin.math.max

/**
 * MOSFET parameters for one wheel.
 * 
 * @param rDsOn25cTotal Total R_ds(on) at 25°C for the bridge (Ω)
 * @param tempCoeffRel Relative temp coefficient (~0.01 = +1%/°C)
 * @param rWiring Fixed wiring resistance (Ω)
 */
data class MOSFETParams(
    val rDsOn25cTotal: Double?=null,
    val tempCoeffRel: Double = 0.01,
    val rWiring: Double = 0.0005,
    val nParallel: Int = 1
) {
    /**
     * R(T) = R_25C * (1 + tempCoeffRel * (T - 25)) + R_wiring
     */
    fun rMosfetAtTemp(tempC: Double?): Double {
        if (tempC == null || tempC.isNaN()) {
            return rDsOn25cTotal!! + rWiring
        }
        val deltaT = tempC - 25.0
        val rHot = rDsOn25cTotal!! * (1.0 + tempCoeffRel * deltaT)
        return max(0.0, rHot + rWiring)
    }
}
