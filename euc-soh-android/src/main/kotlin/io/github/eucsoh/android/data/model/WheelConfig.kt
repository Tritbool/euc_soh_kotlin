package io.github.eucsoh.android.data.model

import io.github.eucsoh.model.MOSFETParams

/**
 * Configuration persistante pour une roue.
 * Stockée par MAC address dans SharedPreferences.
 */
data class WheelConfig(
    val macAddress: String,
    val mosfetParams: MOSFETParams? = null,
    val lastModified: Long = System.currentTimeMillis()
) {
    /**
     * Vérifie si la roue a une config MOSFET.
     */
    fun hasMosfetConfig(): Boolean = mosfetParams != null
    
    /**
     * Crée une copie avec nouveaux paramètres MOSFET.
     */
    fun withMosfetParams(params: MOSFETParams): WheelConfig {
        return copy(
            mosfetParams = params,
            lastModified = System.currentTimeMillis()
        )
    }
    
    /**
     * Crée une copie sans paramètres MOSFET.
     */
    fun clearMosfetParams(): WheelConfig {
        return copy(
            mosfetParams = null,
            lastModified = System.currentTimeMillis()
        )
    }
}
