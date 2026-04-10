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

import io.github.eucsoh.Constants.DarknessBotColumns
import io.github.eucsoh.Constants.WheelLogColumns
import io.github.eucsoh.Constants.CommonColumns
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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
 * @param trips       List of trips split on gaps > [TRIP_GAP_MINUTES] minutes
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
 * Responsibilities (pure, no Android dependencies):
 * 1. Extract MAC and date from the filename.
 * 2. Read rows, map DarknessBot columns → WheelLog columns.
 * 3. Split into trips on temporal gaps > TRIP_GAP_MINUTES.
 * 4. Serialize each trip as a WheelLog-compatible CSV string.
 *
 * The Android layer (DarknessBotScanner) is responsible for:
 * - Opening ZipInputStream on the .dbb file
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

    private val TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private val TS_FORMATTER_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    private val TS_FORMATTER_S = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

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
     */
    fun parse(fileName: String, inputStream: InputStream): DarknessBotTripResult? {
        if (fileName.startsWith(DEMO_PREFIX, ignoreCase = true)) return null

        val match = FILENAME_PATTERN.matchEntire(fileName) ?: return null
        val rawMac = match.groupValues[1].uppercase()   // "187A3E9C56FC"
        val date = match.groupValues[2]                  // "27.03.2026"

        // Format MAC as WheelLog underscore style: "18_7A_3E_9C_56_FC"
        val mac = rawMac.chunked(2).joinToString("_")

        val rows = parseRows(inputStream)
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

    /** Parsed representation of one DarknessBot CSV row (all fields nullable). */
    private data class RawRow(
        val timestamp: LocalDateTime?,
        val speed: String?,
        val voltage: String?,
        val pwm: String?,
        val current: String?,
        val soc: String?,
        val distanceTotalKm: Double?,   // km — will be converted to m for WheelLog
        val temperature: String?
    )

    /** Read and map all rows from the input stream.
     *
     * IMPORTANT: when called from DarknessBotScanner the [inputStream] IS the ZipInputStream
     * positioned on the current entry.  We must NOT close it (that would close the whole ZIP),
     * and we must NOT wrap it in a BufferedReader/BufferedInputStream that reads ahead past the
     * entry boundary.  We use a plain InputStreamReader and collect lines manually.
     */
    private fun parseRows(inputStream: InputStream): List<RawRow> {
        // InputStreamReader with no extra buffering — reads exactly what the ZIP entry exposes.
        // Do NOT call close() on the reader; the ZIP entry is closed by the scanner via closeEntry().
        val reader = java.io.InputStreamReader(inputStream, Charsets.UTF_8)
        val lines = mutableListOf<String>()
        val sb = StringBuilder()
        var ch: Int
        while (reader.read().also { ch = it } != -1) {
            if (ch == '\n'.code) {
                lines.add(sb.toString().trimEnd('\r'))
                sb.clear()
            } else {
                sb.append(ch.toChar())
            }
        }
        if (sb.isNotEmpty()) lines.add(sb.toString().trimEnd('\r'))
        if (lines.size < 2) return emptyList()

        val header = lines[0].split(",").map { it.trim() }

        fun idx(col: DarknessBotColumns) = header.indexOf(col.csv_code)

        val iTs   = idx(DarknessBotColumns.TIMESTAMP)
        val iSpd  = idx(DarknessBotColumns.SPEED)
        val iVolt = idx(DarknessBotColumns.VOLTAGE)
        val iPwm  = idx(DarknessBotColumns.PWM)
        val iCurr = idx(DarknessBotColumns.CURRENT)
        val iSoc  = idx(DarknessBotColumns.SOC)
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

    /** Try multiple timestamp formats (DarknessBot uses microseconds, sometimes fewer digits). */
    private fun parseTimestamp(s: String): LocalDateTime? {
        for (fmt in listOf(TS_FORMATTER, TS_FORMATTER_MS, TS_FORMATTER_S)) {
            try { return LocalDateTime.parse(s, fmt) } catch (_: DateTimeParseException) {}
        }
        return null
    }

    /**
     * Split rows into trip groups.
     * A gap > TRIP_GAP_MINUTES between two consecutive timestamps = new trip.
     * Rows with null timestamps are attached to the current group.
     */
    private fun splitIntoTrips(rows: List<RawRow>): List<List<RawRow>> {
        val groups = mutableListOf<MutableList<RawRow>>()
        var current = mutableListOf<RawRow>()
        var lastTs: LocalDateTime? = null

        for (row in rows) {
            val ts = row.timestamp
            if (ts != null && lastTs != null) {
                val gapMinutes = java.time.Duration.between(lastTs, ts).toMinutes()
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

        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

        for (row in rows) {
            val date = row.timestamp?.format(dateFormatter) ?: ""
            val time = row.timestamp?.format(timeFormatter) ?: ""
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
