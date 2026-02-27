package io.github.tritbool.euc_soh.core.model

import kotlin.math.max

/**
 * MOSFET parameters for resistance calculations.
 * User provides aggregated values for the entire wheel bridge.
 *
 * @property rDsOn25cTotal Total equivalent R_ds(on) of MOSFET bridge at 25°C (Ω)
 * @property tempCoeffRel Relative temperature coefficient (~0.01 = +1%/°C)
 * @property rWiring Fixed additional resistance (shunts/busbars) outside pack (Ω)
 */
data class MOSFETParams(
    val rDsOn25cTotal: Double,
    val tempCoeffRel: Double = 0.01,
    val rWiring: Double = 0.0
) {
    /**
     * Returns equivalent MOSFET+busbar resistance at given temperature.
     * R(T) = R_25C * (1 + tempCoeffRel * (T - 25)) + R_wiring
     *
     * @param tempC Temperature in °C (null = use 25°C)
     * @return Resistance in Ω
     */
    fun rMosfetAtTemp(tempC: Double?): Double {
        if (tempC == null || tempC.isNaN()) {
            return rDsOn25cTotal + rWiring
        }
        val deltaT = tempC - 25.0
        val rHot = rDsOn25cTotal * (1.0 + tempCoeffRel * deltaT)
        return max(0.0, rHot + rWiring)
    }
}
