package io.github.eucsoh.analysis

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

        if ("datetime" in cols && "distance_total" in cols) {
            return "euc_world"
        }
        if ("date" in cols && "time" in cols && "totaldistance" in cols) {
            return "wheellog"
        }
        return if ("datetime" in cols) "euc_world" else "wheellog"
    }

    /**
     * Extracts total wheel distance in km from DataFrame.
     * @return Pair(wheel_km, source_description)
     */
    fun normalizeDistanceTotal(df: DataFrame<*>, source: String): Pair<Double?, String?> {
        val cols = df.columnNames().toSet()

        if (source == "euc_world") {
            if ("distance_total" in cols) {
                val maxDist = df["distance_total"].values()
                    .filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() }
                return maxDist to "distance_total_km_euc"
            }
            if ("distance" in cols) {
                val maxDist = df["distance"].values()
                    .filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() }
                return maxDist to "distance_log_km_euc"
            }
        } else {
            // WheelLog: totaldistance in meters
            if ("totaldistance" in cols) {
                val maxDist = df["totaldistance"].values()
                    .filterIsInstance<Number>()
                    .maxOfOrNull { it.toDouble() }
                return (maxDist?.div(1000.0)) to "totaldistance_m_wl"
            }
            if ("distance" in cols) {
                val maxDist = df["distance"].values()
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

        if (source == "euc_world") {
            for (col in listOf("datetime", "gps_datetime")) {
                if (col in df.columnNames()) {
                    return df[col][0]?.toString()
                }
            }
            return null
        } else {
            // WheelLog
            val date = if ("date" in df.columnNames()) df["date"][0]?.toString() else null
            val time = if ("time" in df.columnNames()) df["time"][0]?.toString() else null

            return when {
                date != null && time != null -> "$date $time"
                date != null -> date
                time != null -> time
                else -> null
            }
        }
    }
}
