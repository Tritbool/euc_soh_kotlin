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

package io.github.eucsoh.parser

import io.github.eucsoh.Constants.CommonColumns
import io.github.eucsoh.Constants.DarknessBotColumns
import io.github.eucsoh.Constants.WheelLogColumns

// ---------------------------------------------------------------------------
// Data classes returned to the Android layer
// ---------------------------------------------------------------------------

/**
 * A single trip extracted from a DarknessBot daily CSV.
 *
 * @param date      Date string from the filename, e.g. "27.03.2026"
 * @param tripIndex 1-based trip index within that day
 * @param csvContent Full CSV text, rewritten in WheelLog column format,
 *                   ready to be written as-is to the cache filesystem.
 */
data class TripCsv(
    val date: String,
    val tripIndex: Int,
    val csvContent: String
)

/**
 * Result of parsing one DarknessBot daily CSV file.
 *
 * @param macAddress  MAC address in WheelLog underscore format: "18_7A_3E_9C_56_FC"
 * @param trips       List of trips split on gaps > [DarknessBotParser.TRIP_GAP_MINUTES] minutes
 */
data class DarknessBotTripResult(
    val macAddress: String,
    val trips: List<TripCsv>
)

// ---------------------------------------------------------------------------
// Parser
// ---------------------------------------------------------------------------

/**
 * Parses a single DarknessBot daily CSV (extracted from a .dbb archive).
 *
 * Responsibilities (pure, no platform dependencies):
 * 1. Extract MAC and date from the filename.
 * 2. Read rows, map DarknessBot columns → WheelLog columns.
 * 3. Split into trips on temporal gaps > TRIP_GAP_MINUTES.
 * 4. Serialize each trip as a WheelLog-compatible CSV string.
 *
 * The Android layer (DarknessBotScanner) is responsible for:
 * - Opening the .dbb ZIP archive and extracting entry text
 * - Calling this parser per entry
 * - Writing the resulting TripCsv.csvContent strings to the cache
 */
object DarknessBotParser {

    /** A gap larger than this (minutes) between two consecutive rows = new trip. */
    const val TRIP_GAP_MINUTES = 15L

    /** DarknessBot files named like DemoDeviceID_*.csv must be ignored. */
    private const val DEMO_PREFIX = "DemoDeviceID"

    /**
     * Filename pattern: {12HEX}_{DD.MM.YYYY}.csv
     * The MAC has exactly 12 hex chars, no separators.
     */
    private val FILENAME_PATTERN = Regex(
        """^([0-9A-Fa-f]{12})_(\d{2}\.\d{2}\.\d{4})\.csv$"""
    )

    private val TS_PATTERN = Regex(
        """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?$"""
    )

    // WheelLog output header — matches WheelLogColumns + CommonColumns used by the pipeline
    private val WHEELLOG_HEADER = listOf(
        WheelLogColumns.DATE.csv_code,       // "date"
        WheelLogColumns.TIME.csv_code,       // "time"
        CommonColumns.SPEED.csv_code,        // "speed"
        CommonColumns.VOLTAGE.csv_code,      // "voltage"
        WheelLogColumns.PWM.csv_code,        // "pwm"
        CommonColumns.CURRENT.csv_code,      // "current"
        WheelLogColumns.SOC.csv_code,        // "battery_level"
        WheelLogColumns.DISTANCE_TOTAL.csv_code, // "totaldistance" (meters)
        WheelLogColumns.BOARD_TEMPERATURE.csv_code // "temp"
    )

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Returns null if [fileName] is a DemoDeviceID file or doesn't match the expected pattern.
     *
     * @param csvText Full UTF-8 content of the CSV entry inside the .dbb archive.
     */
    fun parse(fileName: String, csvText: String): DarknessBotTripResult? {
        if (fileName.startsWith(DEMO_PREFIX, ignoreCase = true)) return null

        val match = FILENAME_PATTERN.matchEntire(fileName) ?: return null
        val rawMac = match.groupValues[1].uppercase()   // "187A3E9C56FC"
        val date = match.groupValues[2]                  // "27.03.2026"

        // Format MAC as WheelLog underscore style: "18_7A_3E_9C_56_FC"
        val mac = rawMac.chunked(2).joinToString("_")

        val rows = parseRows(csvText)
        if (rows.isEmpty()) return null

        val tripGroups = splitIntoTrips(rows)

        val trips = tripGroups.mapIndexed { idx, tripRows ->
            TripCsv(
                date = date,
                tripIndex = idx + 1,
                csvContent = serializeAsWheelLog(tripRows)
            )
        }

        return DarknessBotTripResult(macAddress = mac, trips = trips)
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    /**
     * Parsed representation of one DarknessBot CSV row (all fields nullable).
     * [timestamp] is null when absent or unparseable.
     */
    private data class RawRow(
        val timestamp: ParsedTimestamp?,
        val speed: String?,
        val voltage: String?,
        val pwm: String?,
        val current: String?,
        val soc: String?,
        val distanceTotalKm: Double?,   // km — will be converted to m for WheelLog
        val temperature: String?
    )

    /** Parsed "yyyy-MM-ddTHH:mm:ss[.frac]" local timestamp (treated as UTC). */
    data class ParsedTimestamp(
        val year: Int,
        val month: Int,
        val day: Int,
        val hour: Int,
        val minute: Int,
        val second: Int,
        val millisecond: Int,
        val epochSeconds: Double
    )

    private fun parseTimestamp(s: String): ParsedTimestamp? {
        val m = TS_PATTERN.matchEntire(s.trim()) ?: return null
        val year = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        val hour = m.groupValues[4].toInt()
        val minute = m.groupValues[5].toInt()
        val second = m.groupValues[6].toInt()
        val fraction = m.groupValues[7]
        val millisecond = if (fraction.isEmpty()) 0 else fraction.padEnd(3, '0').take(3).toInt()
        val days = daysFromCivil(year, month, day)
        val epoch = days * 86400.0 + hour * 3600 + minute * 60 + second + millisecond / 1000.0
        return ParsedTimestamp(year, month, day, hour, minute, second, millisecond, epoch)
    }

    /** Days from 1970-01-01 (Howard Hinnant's days_from_civil algorithm). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Int {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    /** Read and map all rows from the CSV text. */
    private fun parseRows(csvText: String): List<RawRow> {
        val lines = csvText.split('\n').map { it.trimEnd('\r') }
        if (lines.size < 2) return emptyList()

        val header = lines.first().split(",").map { it.trim() }

        fun idx(col: DarknessBotColumns) = header.indexOf(col.csv_code)

        val iTs = idx(DarknessBotColumns.TIMESTAMP)
        val iSpd = idx(DarknessBotColumns.SPEED)
        val iVolt = idx(DarknessBotColumns.VOLTAGE)
        val iPwm = idx(DarknessBotColumns.PWM)
        val iCurr = idx(DarknessBotColumns.CURRENT)
        val iSoc = idx(DarknessBotColumns.SOC)
        val iDist = idx(DarknessBotColumns.DISTANCE_TOTAL)
        val iTemp = idx(DarknessBotColumns.TEMPERATURE)

        return lines.drop(1).mapNotNull { line ->
            val cells = line.split(",").map { it.trim() }
            if (cells.size <= maxOf(iTs, iSpd, iVolt, iPwm, iCurr, iSoc, iDist, iTemp)) return@mapNotNull null

            fun cell(i: Int) = if (i >= 0 && i < cells.size) cells[i].takeIf { it.isNotBlank() } else null

            val tsStr = cell(iTs)
            val ts = tsStr?.let { parseTimestamp(it) }

            RawRow(
                timestamp = ts,
                speed = cell(iSpd),
                voltage = cell(iVolt),
                pwm = cell(iPwm),
                current = cell(iCurr),
                soc = cell(iSoc),
                distanceTotalKm = cell(iDist)?.toDoubleOrNull(),
                temperature = cell(iTemp)
            )
        }
    }

    /**
     * Split rows into trip groups.
     * A gap > TRIP_GAP_MINUTES between two consecutive timestamps = new trip.
     * Rows with null timestamps are attached to the current group.
     */
    private fun splitIntoTrips(rows: List<RawRow>): List<List<RawRow>> {
        val groups = mutableListOf<MutableList<RawRow>>()
        var current = mutableListOf<RawRow>()
        var lastTs: ParsedTimestamp? = null

        for (row in rows) {
            val ts = row.timestamp
            if (ts != null && lastTs != null) {
                val gapMinutes = ((ts.epochSeconds - lastTs.epochSeconds) / 60.0).toLong()
                if (gapMinutes > TRIP_GAP_MINUTES) {
                    if (current.isNotEmpty()) {
                        groups.add(current)
                        current = mutableListOf()
                    }
                }
            }
            current.add(row)
            if (ts != null) lastTs = ts
        }
        if (current.isNotEmpty()) groups.add(current)
        return groups
    }

    /**
     * Serialize a trip as a WheelLog-format CSV string.
     *
     * Column mapping:
     *   DarknessBot Date (ISO)       → date (YYYY-MM-DD) + time (HH:mm:ss.SSS)
     *   Speed                        → speed
     *   Voltage                      → voltage
     *   PWM                          → pwm
     *   Current                      → current
     *   Battery level                → battery_level
     *   Total mileage (km × 1000)   → totaldistance (meters, as WheelLog stores it)
     *   Temperature                  → temp
     */
    private fun serializeAsWheelLog(rows: List<RawRow>): String {
        val sb = StringBuilder()
        sb.appendLine(WHEELLOG_HEADER.joinToString(","))

        for (row in rows) {
            val ts = row.timestamp
            val date = ts?.let {
                listOf(
                    it.year.toString().padStart(4, '0'),
                    it.month.toString().padStart(2, '0'),
                    it.day.toString().padStart(2, '0')
                ).joinToString("-")
            } ?: ""
            val time = ts?.let {
                listOf(
                    it.hour.toString().padStart(2, '0'),
                    it.minute.toString().padStart(2, '0'),
                    it.second.toString().padStart(2, '0')
                ).joinToString(":") + "." + it.millisecond.toString().padStart(3, '0')
            } ?: ""
            val distM = row.distanceTotalKm?.let { (it * 1000.0).toLong().toString() } ?: ""

            val cells = listOf(
                date,
                time,
                row.speed ?: "",
                row.voltage ?: "",
                row.pwm ?: "",
                row.current ?: "",
                row.soc ?: "",
                distM,
                row.temperature ?: ""
            )
            sb.appendLine(cells.joinToString(","))
        }
        return sb.toString()
    }
}
