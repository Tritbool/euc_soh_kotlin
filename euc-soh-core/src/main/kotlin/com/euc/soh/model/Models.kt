// Models.kt
package com.euc.soh.model

import kotlinx.datetime.Instant

/**
 * Source du fichier log
 */
enum class LogSource {
    EUC_WORLD,
    WHEELLOG
}

/**
 * Données d'un log individuel
 */
data class LogData(
    val fileName: String,
    val source: LogSource,
    val datetimeFirst: String?,
    val wheelKm: Double?,
    val wheelKmSource: String?,
    val vIdle: Double,
    val nsSeries: Int?,
    val socRefOk: Boolean,
    val socRefVFull: Double?,
    val nPoints: Int,
    val reqMean: Double,
    val reqMedian: Double,
    val reqMedian25C: Double,
    val req95p: Double,
    val sag95p: Double,
    val sagMax: Double,
    val vMinStrong: Double,
    val iMax: Double,
    val i95p: Double,
    val tempBoardMax: Double?,
    val tempMotorMax: Double?,
    val iPhase2Int: Double?,
    val iPhaseMax: Double?,
    val iPhase95p: Double?,
    val rBattMedian: Double?,
    val rBattMedian25C: Double?,
    val rMosfetHot: Double?
) {
    /**
     * Récupère la valeur d'une métrique par son nom
     */
    fun getMetric(metricName: String): Double? = when (metricName) {
        "Req_median" -> reqMedian
        "Req_median_25C" -> reqMedian25C
        "Req_95p" -> req95p
        "sag_95p" -> sag95p
        "sag_max" -> sagMax
        "v_min_strong" -> vMinStrong
        "i_max" -> iMax
        "i_95p" -> i95p
        "temp_board_max" -> tempBoardMax
        "temp_motor_max" -> tempMotorMax
        "I_phase2_int" -> iPhase2Int
        "i_phase_max" -> iPhaseMax
        "i_phase_95p" -> iPhase95p
        "R_batt_median" -> rBattMedian
        "R_batt_median_25C" -> rBattMedian25C
        "R_mosfet_hot" -> rMosfetHot
        else -> null
    }
}

/**
 * Point de métriques calculées pour un fichier (résultat de l'analyse d'un CSV)
 * Plus léger et découplé de la structure LogData utilisée pour l'UI/export.
 */
data class FileStats(
    val fileName: String,
    val datetimeFirst: String?,
    val wheelKm: Double?,
    val vIdle: Double,
    val nsSeries: Int?,
    val nPoints: Int,
    val reqMean: Double,
    val reqMedian: Double,
    val reqMedian25C: Double,
    val req95p: Double,
    val sag95p: Double,
    val sagMax: Double,
    val vMinStrong: Double,
    val iMax: Double,
    val i95p: Double,
    val tempBoardMax: Double?,
    val tempMotorMax: Double?
)

/**
 * Statistiques globales d'une roue
 */
data class WheelStatistics(
    val wheelName: String,
    val logs: List<LogData>,
    val nsGlobal: Int?,
    val vNominal: Double?,
    val rPackNominal: Double?,
    val reqBandLow: Double,
    val reqBandHigh: Double,
    val rBattBandLow: Double?,
    val rBattBandHigh: Double?,
    val arrheniusEaKJperMol: Double,
    val arrheniusAutoCalibrated: Boolean,
    val alarms: List<Alarm> = emptyList(),
    val thresholds: Map<String, Threshold> = emptyMap()
)

/**
 * Seuil gaussien pour une métrique
 */
data class Threshold(
    val mean: Double,
    val std: Double,
    val limit: Double,
    val direction: AlarmDirection
)

enum class AlarmDirection {
    HIGHER_IS_BAD,
    LOWER_IS_BAD
}

/**
 * Alarme détectée
 */
data class Alarm(
    val fileName: String,
    val wheelKm: Double?,
    val datetimeFirst: String?,
    val reasons: String
)

/**
 * Paramètres MOSFET utilisateur
 */
data class MOSFETParams(
    val rDsOn25cTotal: Double,
    val tempCoeffRel: Double = 0.01,
    val rWiring: Double = 0.0
) {
    /**
     * Calcule la résistance MOSFET à une température donnée
     * R(T) = R_25C * (1 + temp_coeff_rel * (T - 25))
     */
    fun rMosfetAtTemp(tempC: Double?): Double {
        if (tempC == null || tempC.isNaN()) {
            return rDsOn25cTotal + rWiring
        }
        val deltaT = tempC - 25.0
        val rHot = rDsOn25cTotal * (1.0 + tempCoeffRel * deltaT)
        return maxOf(0.0, rHot + rWiring)
    }
}

/**
 * Résultat de détection CUSUM
 */
data class CUSUMResult(
    val alarmIndices: List<Int>,
    val muRef: Double?,
    val sigmaRef: Double?
)

/**
 * Résultat d'analyse de tendance linéaire
 */
data class TrendResult(
    val slope: Double?,
    val pValue: Double?,
    val isSignificant: Boolean
)

/**
 * Résultat de détection d'inflexions
 */
data class InflexionResult(
    val slowIndices: List<Int>,
    val inflexionIndices: List<Int>,
    val localSlopes: Map<Int, Double>
)

/**
 * Configuration pack batterie
 */
data class PackConfig(
    val nsGlobal: Int?,
    val vNominal: Double?
)

/**
 * Point de données brut CSV
 */
data class RawDataPoint(
    val voltage: Double,
    val current: Double,
    val speed: Double,
    val systemTemp: Double?,
    val motorTemp: Double?,
    val currentPhase: Double?,
    val batteryLevel: Double?,
    val datetime: String?
)

/**
 * Résumé d'analyse pour export/UI
 */
data class AnalysisSummary(
    val wheelName: String,
    val reqBand: Pair<Double, Double>,
    val global: GlobalMetrics,
    val pack: PackConfig?,
    val battReqBand: Pair<Double, Double>?,
    val arrhenius: ArrheniusInfo?,
    val socVoltageAvailable: Boolean
)

data class GlobalMetrics(
    val kmMin: Double,
    val kmMax: Double,
    val reqMedianMin: Double,
    val reqMedianMax: Double,
    val rBattMedianMin: Double?,
    val rBattMedianMax: Double?,
    val rMosfetHotMin: Double?,
    val rMosfetHotMax: Double?
)

data class ArrheniusInfo(
    val eaKJperMol: Double,
    val autoCalibrated: Boolean
)
