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

package io.github.eucsoh.analysis

import io.github.eucsoh.Constants
import io.github.eucsoh.Constants.EUCWorldColumns
import io.github.eucsoh.Constants.EUC_WORLD
import io.github.eucsoh.Constants.WHEELLOG
import io.github.eucsoh.Constants.WheelLogColumns
import io.github.eucsoh.Constants.CommonColumns
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.*

/**
 * CSV source type detection (EUC World vs WheelLog).
 */
object SourceDetection {

    /**
     * Detects source format from DataFrame columns.
     * @return "euc_world" or "wheellog"
     */
    fun detectSource(df: DataFrame<*>): String {
        val cols = df.columnNames().toSet()

        if (EUCWorldColumns.TIMESTAMP.csv_code in cols && EUCWorldColumns.DISTANCE_TOTAL.csv_code in cols) {
            return EUC_WORLD
        }
        if (WheelLogColumns.DATE.csv_code in cols && WheelLogColumns.TIME.csv_code in cols && WheelLogColumns.DISTANCE_TOTAL.csv_code in cols) {
            return WHEELLOG
        }
        return if (EUCWorldColumns.TIMESTAMP.csv_code in cols) EUC_WORLD else WHEELLOG
    }

    /**
     * Extracts total wheel distance in km from DataFrame.
     * @return Pair(wheel_km, source_description)
     */
    fun normalizeDistanceTotal(df: DataFrame<*>, source: String): Pair<Double?, String?> {
        val cols = df.columnNames().toSet()

        if (source == EUC_WORLD) {
            if (EUCWorldColumns.DISTANCE_TOTAL.csv_code in cols) {
                val maxDist = df[EUCWorldColumns.DISTANCE_TOTAL.csv_code].values()
                    .filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() }
                return maxDist to "distance_total_km_euc"
            }
            if (CommonColumns.DISTANCE.csv_code in cols) {
                val maxDist = df[CommonColumns.DISTANCE.csv_code].values()
                    .filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() }
                return maxDist to "distance_log_km_euc"
            }
        } else {
            // WheelLog: totaldistance in meters
            if (WheelLogColumns.DISTANCE_TOTAL.csv_code in cols) {
                val maxDist = df[WheelLogColumns.DISTANCE_TOTAL.csv_code].values()
                    .filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() }
                return (maxDist?.div(1000.0)) to "totaldistance_m_wl"
            }
            if (CommonColumns.DISTANCE.csv_code in cols) {
                val maxDist = df[CommonColumns.DISTANCE.csv_code].values()
                    .filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() }
                return maxDist to "distance_log_km_wl"
            }
        }

        return null to null
    }

    /**
     * Gets first datetime string for log sorting.
     */
    fun getFirstDatetime(df: DataFrame<*>, source: String): String? {
        if (df.rowsCount() == 0) return null

        if (source == EUC_WORLD) {
            for (col in listOf(EUCWorldColumns.TIMESTAMP.csv_code, EUCWorldColumns.GPS_TIMESTAMP.csv_code)) {
                if (col in df.columnNames()) {
                    return df[col][0]?.toString()
                }
            }
            return null
        } else {
            // WheelLog
            val date = if (WheelLogColumns.DATE.csv_code in df.columnNames()) df[WheelLogColumns.DATE.csv_code][0]?.toString() else null
            val time = if (WheelLogColumns.TIME.csv_code in df.columnNames()) df[WheelLogColumns.TIME.csv_code][0]?.toString() else null

            return when {
                date != null && time != null -> "$date $time"
                date != null -> date
                time != null -> time
                else -> null
            }
        }
    }
}
