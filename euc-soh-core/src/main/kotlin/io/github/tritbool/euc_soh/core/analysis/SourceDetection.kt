package io.github.tritbool.euc_soh.core.analysis

import org.jetbrains.kotlinx.dataframe.DataFrame

/**
 * Detects CSV source type from DataFrame columns.
 * @return "euc_world" or "wheellog"
 */
fun detectSource(df: DataFrame<*>): String {
    val cols = df.columnNames().toSet()

    // EUC World: has "datetime" AND "distance_total"
    if ("datetime" in cols && "distance_total" in cols) {
        return "euc_world"
    }

    // WheelLog: has "date", "time", "totaldistance"
    if ("date" in cols && "time" in cols && "totaldistance" in cols) {
        return "wheellog"
    }

    // Fallback: if "datetime" present, assume euc_world
    return if ("datetime" in cols) "euc_world" else "wheellog"
}

/**
 * Extracts total distance in km from DataFrame.
 * @return Pair(wheel_km, source_description)
 */
fun normalizeDistanceTotal(df: DataFrame<*>, source: String): Pair<Double?, String?> {
    val cols = df.columnNames().toSet()

    if (source == "euc_world") {
        if ("distance_total" in cols) {
            val distCol = df["distance_total"]
            val maxDist = distCol.values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
            return maxDist to "distance_total_km_euc"
        }
        if ("distance" in cols) {
            val distCol = df["distance"]
            val maxDist = distCol.values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
            return maxDist to "distance_log_km_euc"
        }
    } else {
        // wheellog: totaldistance in meters
        if ("totaldistance" in cols) {
            val distCol = df["totaldistance"]
            val maxDist = distCol.values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
            return (maxDist?.div(1000.0)) to "totaldistance_m_wl"
        }
        if ("distance" in cols) {
            val distCol = df["distance"]
            val maxDist = distCol.values().filterIsInstance<Number>().maxOfOrNull { it.toDouble() }
            return maxDist to "distance_log_km_wl"
        }
    }

    return null to null
}

/**
 * Extracts first datetime string for sorting logs.
 */
fun getFirstDatetime(df: DataFrame<*>, source: String): String? {
    if (df.rowsCount() == 0) return null

    if (source == "euc_world") {
        listOf("datetime", "gps_datetime").forEach { col ->
            if (col in df.columnNames()) {
                return df[col][0]?.toString()
            }
        }
        return null
    } else {
        // wheellog
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
