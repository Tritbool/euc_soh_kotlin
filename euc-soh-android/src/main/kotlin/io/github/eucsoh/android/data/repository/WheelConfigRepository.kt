package io.github.eucsoh.android.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.github.eucsoh.android.data.model.WheelConfig
import io.github.eucsoh.model.MOSFETParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository pour la gestion des configurations de roues.
 * Persistence via SharedPreferences avec format JSON simple.
 */
class WheelConfigRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "wheel_configs",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFIX_MOSFET_RDS = "mosfet_rds_"
        private const val PREFIX_MOSFET_TEMP_COEFF = "mosfet_temp_coeff_"
        private const val PREFIX_MOSFET_WIRING = "mosfet_wiring_"

        private const val PREFIX_N_PARALLEL = "n_parallel"
        private const val PREFIX_LAST_MODIFIED = "last_modified_"
    }
    
    /**
     * Récupère la config d'une roue par MAC address.
     */
    suspend fun getConfig(macAddress: String): WheelConfig = withContext(Dispatchers.IO) {
        val rdsOn = prefs.getFloat(PREFIX_MOSFET_RDS + macAddress, -1f)
        val tempCoeff = prefs.getFloat(PREFIX_MOSFET_TEMP_COEFF + macAddress, 0.01f)
        val wiring = prefs.getFloat(PREFIX_MOSFET_WIRING + macAddress, 0f)
        val nparallel = prefs.getInt(PREFIX_N_PARALLEL + macAddress,1)
        val lastModified = prefs.getLong(PREFIX_LAST_MODIFIED + macAddress, 0L)
        
        val mosfetParams = if (rdsOn > 0) {
            MOSFETParams(
                rDsOn25cTotal = rdsOn.toDouble(),
                tempCoeffRel = tempCoeff.toDouble(),
                rWiring = wiring.toDouble(),
                nParallel = nparallel
            )
        } else {
            null
        }
        
        WheelConfig(
            macAddress = macAddress,
            mosfetParams = mosfetParams,
            lastModified = lastModified
        )
    }
    
    /**
     * Sauvegarde la config d'une roue.
     */
    suspend fun saveConfig(config: WheelConfig): Unit = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (config.mosfetParams != null) {
                if(config.mosfetParams.rDsOn25cTotal != null){
                    putFloat(
                        PREFIX_MOSFET_RDS + config.macAddress,
                        config.mosfetParams.rDsOn25cTotal!!.toFloat()
                    )
                }
                putFloat(
                    PREFIX_MOSFET_TEMP_COEFF + config.macAddress,
                    config.mosfetParams.tempCoeffRel.toFloat()
                )
                putFloat(
                    PREFIX_MOSFET_WIRING + config.macAddress,
                    config.mosfetParams.rWiring.toFloat()
                )
                putInt(
                    PREFIX_N_PARALLEL + config.macAddress,
                config.mosfetParams.nParallel
                )
            } else {
                // Effacer si null
                remove(PREFIX_MOSFET_RDS + config.macAddress)
                remove(PREFIX_MOSFET_TEMP_COEFF + config.macAddress)
                remove(PREFIX_MOSFET_WIRING + config.macAddress)
                remove(PREFIX_N_PARALLEL + config.macAddress)
            }
            putLong(PREFIX_LAST_MODIFIED + config.macAddress, config.lastModified)
            apply()
        }
    }
    
    /**
     * Sauvegarde les paramètres MOSFET pour une roue.
     */
    suspend fun saveMosfetParams(
        macAddress: String,
        rDsOn25cTotal: Double?,
        tempCoeffRel: Double = 0.01,
        rWiring: Double = 0.0005,
        nParallel:Int =1
    ) {
        val params = MOSFETParams(rDsOn25cTotal, tempCoeffRel, rWiring,nParallel)
        val config = WheelConfig(macAddress, params)
        saveConfig(config)
    }
    
    /**
     * Efface la config MOSFET d'une roue.
     */
    suspend fun clearMosfetParams(macAddress: String) {
        val config = getConfig(macAddress).clearMosfetParams()
        saveConfig(config)
    }
    
    /**
     * Vérifie si une roue a une config MOSFET.
     */
    suspend fun hasMosfetConfig(macAddress: String): Boolean {
        return getConfig(macAddress).hasMosfetConfig()
    }
    
    /**
     * Liste toutes les roues avec config MOSFET.
     */
    suspend fun listConfiguredWheels(): List<String> = withContext(Dispatchers.IO) {
        prefs.all.keys
            .filter { it.startsWith(PREFIX_MOSFET_RDS) }
            .map { it.removePrefix(PREFIX_MOSFET_RDS) }
    }
}
