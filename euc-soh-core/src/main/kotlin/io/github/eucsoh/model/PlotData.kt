/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

// euc-soh-core/.../model/PlotData.kt
package io.github.eucsoh.model

import io.github.eucsoh.Constants.Metrics

/**
 * Données de visualisation pré-calculées par le core, sans DataFrame.
 * Consommées directement par la couche Android sans recalcul.
 */
data class PlotData(
    /** Données brutes par métrique : metric → liste (wheelKm, value) triée par km */
    val series: Map<Metrics, List<Pair<Double, Double>>>,

    val gaussianResults: Map<Metrics, GaussianPlotResult>,

    /** Résultats CUSUM par métrique */
    val cusumResults: Map<Metrics, CusumPlotResult>,

    /** Résultats Trend par métrique */
    val trendResults: Map<Metrics, TrendPlotResult>,

    /** Résultats Inflexion par métrique */
    val inflexionResults: Map<Metrics, InflexionPlotResult>,

    /** Factory resistance RDS_on 25C */
    val mosfetRdsOn25cRef: Double? = null,

    /** Estimated Nominal Battery Pack Resistance */
    val battPackRNominal: Double? = null
) {

    data class GaussianPlotResult(
        val mu: Double,
        val sigma: Double,
        val higherIsBad: Boolean
    )

    data class CusumPlotResult(
        val alarmKm: Set<Double>,   // indices dans series[metric]
        val muRef: Double,
        val sigmaRef: Double,
        val hSigma: Double            // pour tracer la threshold line
    )

    data class TrendPlotResult(
        val slope: Double,
        val intercept: Double,
        val isSignificant: Boolean,
        val pValue: Double?
    )

    data class InflexionPlotResult(
        val slowIndices: List<Int>,
        val inflexionIndices: List<Int>,
        val dangerLimit: Double
    )
}
