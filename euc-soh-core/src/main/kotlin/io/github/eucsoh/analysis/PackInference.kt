package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.Constants.MetaColumns.*
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * Battery pack configuration inference from logs.
 */
object PackInference {

    /**
     * Estimates nominal cell resistance (mΩ) at 25°C based on pack voltage.
     */
    fun estimateCellResistanceMohm(vNom: Double?): Double {
        if (vNom == null) return 18.0
        return when {
            vNom < 80.0 -> 22.0
            vNom < 110.0 -> 18.0
            vNom < 150.0 -> 14.0
            else -> 12.0
        }
    }

    /**
     * Computes pack nominal resistance (Ω) from series count and nominal voltage.
     */
    fun computePackNominalResistance(nsGlobal: Int?, vNom: Double?): Double? {
        if (nsGlobal == null) return null
        val rCellMohm = estimateCellResistanceMohm(vNom)
        return nsGlobal * rCellMohm / 1000.0
    }

    /**
     * Infers global pack config (Ns, V_nominal) from DataFrame with "Ns" and "soc_ref_ok" columns.
     */
    fun inferPackConfig(df: DataFrame<*>): Pair<Int?, Double?> {
        if (NS.csv_code !in df.columnNames() || SOC_REF_OK.csv_code !in df.columnNames()) {
            return null to null
        }

        val dfOk = df.filter { it[SOC_REF_OK.csv_code] == true }
        val nsSeries = dfOk[NS.csv_code].values()
            .filterNotNull()
            .map { (it as Number).toDouble() }

        if (nsSeries.isEmpty()) {
            return null to null
        }

        val nsMedian = nsSeries.sorted().let { it[it.size / 2] }.toInt()

        if (nsMedian !in Constants.NS_MIN..Constants.NS_MAX) {
            return null to null
        }

        val nsGlobal = Constants.KNOWN_SERIES.minByOrNull { kotlin.math.abs(it - nsMedian) }
        val vNom = nsGlobal?.let { it * Constants.NOMINAL_CELL_V }

        return nsGlobal to vNom
    }

    /**
     * Chooses current window (I_min, I_max) for Req calculation based on Ns.
     */
    fun chooseBatteryCurrentWindow(ns: Int?): Pair<Double, Double> {
        if (ns == null) return 10.0 to 80.0
        return when {
            ns <= 16 -> 6.0 to 90.0       // ~400W  when 67.2V
            ns <= 24 -> 12.0 to 150.0     // ~1200W when 100.8V
            ns <= 36 -> 10.0 to 180.0     // ~1500W when 151.2V
            ns <= 56 -> 11.0 to 200.0     // ~2600W when 235.2V
            else -> 3.0 to 200.0
        }
    }
}
