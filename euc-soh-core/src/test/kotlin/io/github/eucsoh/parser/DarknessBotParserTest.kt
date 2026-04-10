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

import io.github.eucsoh.Constants.WheelLogColumns
import io.github.eucsoh.Constants.CommonColumns
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [DarknessBotParser].
 *
 * All tests are self-contained: they build minimal in-memory CSV strings and do
 * not rely on the sample .dbb file on disk, so they always run (no skip logic).
 */
class DarknessBotParserTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** DarknessBot CSV header (exact column names as produced by the app). */
    private val DB_HEADER = "Date,Speed,Voltage,PWM,Current,Power,Battery level,Total mileage,Temperature,Pitch,Roll,Latitude,Longitude,Altitude"

    /**
     * Build a minimal DarknessBot CSV row.
     * [timestamp] format: "2026-03-27T20:56:46.948611"
     */
    private fun row(
        timestamp: String,
        speed: String = "25.0",
        voltage: String = "82.5",
        pwm: String = "40.0",
        current: String = "10.0",
        power: String = "825.0",
        soc: String = "85.0",
        totalMileageKm: String = "1000.0",
        temp: String = "35.0",
        pitch: String = "0.0",
        roll: String = "0.0",
        lat: String = "0.0",
        lon: String = "0.0",
        alt: String = "0.0"
    ) = "$timestamp,$speed,$voltage,$pwm,$current,$power,$soc,$totalMileageKm,$temp,$pitch,$roll,$lat,$lon,$alt"

    /** Build a complete CSV string with the given rows. */
    private fun csv(vararg rows: String) =
        (listOf(DB_HEADER) + rows.toList()).joinToString("\n")

    /** Parse using a filename that matches the expected pattern. */
    private fun parse(fileName: String, csvContent: String): DarknessBotTripResult? =
        DarknessBotParser.parse(fileName, csvContent.byteInputStream(Charsets.UTF_8))

    // -------------------------------------------------------------------------
    // Filename validation
    // -------------------------------------------------------------------------

    @Test
    fun `valid filename is accepted`() {
        val content = csv(row("2026-03-27T20:56:46.948611"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result, "Valid filename should produce a non-null result")
    }

    @Test
    fun `DemoDeviceID filename is rejected`() {
        val content = csv(row("2026-03-28T08:07:23.525410"))
        val result = parse("DemoDeviceID_28.03.2026.csv", content)
        assertNull(result, "DemoDeviceID files must be silently rejected")
    }

    @Test
    fun `filename without MAC prefix is rejected`() {
        val content = csv(row("2026-03-27T20:56:46.000000"))
        val result = parse("log_27.03.2026.csv", content)
        assertNull(result, "Filename not matching {12HEX}_{date}.csv must return null")
    }

    @Test
    fun `filename with wrong date format is rejected`() {
        val content = csv(row("2026-03-27T20:56:46.000000"))
        // YYYY-MM-DD instead of DD.MM.YYYY
        val result = parse("187A3E9C56FC_2026-03-27.csv", content)
        assertNull(result, "Date not in DD.MM.YYYY format must return null")
    }

    @Test
    fun `MAC is extracted in underscore WheelLog format`() {
        val content = csv(row("2026-03-27T20:56:46.948611"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)
        // 187A3E9C56FC → 18_7A_3E_9C_56_FC
        assertEquals("18_7A_3E_9C_56_FC", result.macAddress,
            "MAC should be converted from compact hex to underscore WheelLog format")
    }

    @Test
    fun `lowercase MAC in filename is normalised to uppercase`() {
        val content = csv(row("2026-03-27T20:56:46.948611"))
        val result = parse("187a3e9c56fc_27.03.2026.csv", content)
        assertNotNull(result)
        assertEquals("18_7A_3E_9C_56_FC", result.macAddress,
            "Lowercase hex in filename must be uppercased")
    }

    // -------------------------------------------------------------------------
    // Row parsing and output format
    // -------------------------------------------------------------------------

    @Test
    fun `single row produces one trip with correct WheelLog header`() {
        val content = csv(row("2026-03-27T20:56:46.948611"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)
        assertEquals(1, result.trips.size)

        val outputLines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        val outputHeader = outputLines[0].split(",")

        // Check mandatory WheelLog columns are present
        val expectedCols = listOf(
            WheelLogColumns.DATE.csv_code,       // "date"
            WheelLogColumns.TIME.csv_code,       // "time"
            CommonColumns.SPEED.csv_code,        // "speed"
            CommonColumns.VOLTAGE.csv_code,      // "voltage"
            WheelLogColumns.PWM.csv_code,        // "pwm"
            CommonColumns.CURRENT.csv_code,      // "current"
            WheelLogColumns.SOC.csv_code,        // "battery_level"
            WheelLogColumns.DISTANCE_TOTAL.csv_code, // "totaldistance"
            WheelLogColumns.BOARD_TEMPERATURE.csv_code // "temp"
        )
        expectedCols.forEach { col ->
            assertTrue(outputHeader.contains(col),
                "WheelLog output header must contain '$col', got: $outputHeader")
        }
    }

    @Test
    fun `Total mileage is converted from km to metres`() {
        // 1000.5 km → 1000500 m
        val content = csv(row("2026-03-27T20:56:46.948611", totalMileageKm = "1000.5"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)

        val lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        val header = lines[0].split(",")
        val distIdx = header.indexOf(WheelLogColumns.DISTANCE_TOTAL.csv_code)
        assertTrue(distIdx >= 0, "totaldistance column must exist")

        val dataRow = lines[1].split(",")
        val distMetres = dataRow[distIdx].toLongOrNull()
        assertNotNull(distMetres, "totaldistance value must be a parseable long")
        assertEquals(1000500L, distMetres,
            "1000.5 km must be serialised as 1000500 metres")
    }

    @Test
    fun `empty Total mileage is serialised as empty string`() {
        val content = csv(row("2026-03-27T20:56:46.948611", totalMileageKm = ""))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)

        val lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        val header = lines[0].split(",")
        val distIdx = header.indexOf(WheelLogColumns.DISTANCE_TOTAL.csv_code)
        val dataRow = lines[1].split(",")
        val distVal = dataRow[distIdx]
        assertTrue(distVal.isBlank(),
            "Empty Total mileage must produce an empty totaldistance cell, got: '$distVal'")
    }

    @Test
    fun `date and time are split correctly from ISO timestamp`() {
        val content = csv(row("2026-03-27T20:56:46.948611"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)

        val lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        val header = lines[0].split(",")
        val dateIdx = header.indexOf(WheelLogColumns.DATE.csv_code)
        val timeIdx = header.indexOf(WheelLogColumns.TIME.csv_code)

        val dataRow = lines[1].split(",")
        assertEquals("2026-03-27", dataRow[dateIdx], "date cell must be YYYY-MM-DD")
        assertTrue(dataRow[timeIdx].startsWith("20:56:46"),
            "time cell must start with HH:mm:ss, got: ${dataRow[timeIdx]}")
    }

    @Test
    fun `CSV with only header returns null`() {
        val result = parse("187A3E9C56FC_27.03.2026.csv", DB_HEADER)
        assertNull(result, "A CSV with no data rows must return null")
    }

    // -------------------------------------------------------------------------
    // Trip splitting
    // -------------------------------------------------------------------------

    @Test
    fun `rows within gap threshold produce a single trip`() {
        // Two rows 5 minutes apart — below TRIP_GAP_MINUTES (15)
        val content = csv(
            row("2026-04-09T15:16:00.000000"),
            row("2026-04-09T15:21:00.000000")
        )
        val result = parse("EB56EB9A4EF3_09.04.2026.csv", content)
        assertNotNull(result)
        assertEquals(1, result.trips.size,
            "Two rows 5 minutes apart must stay in the same trip")
    }

    @Test
    fun `gap larger than TRIP_GAP_MINUTES produces two trips`() {
        // Two rows 24 minutes apart — above TRIP_GAP_MINUTES (15)
        val content = csv(
            row("2026-04-09T15:16:00.000000"),
            row("2026-04-09T15:40:00.000000")
        )
        val result = parse("EB56EB9A4EF3_09.04.2026.csv", content)
        assertNotNull(result)
        assertEquals(2, result.trips.size,
            "A 24-minute gap must split the session into 2 trips")
    }

    @Test
    fun `trip indices are 1-based and sequential`() {
        val content = csv(
            row("2026-04-09T15:16:00.000000"),
            row("2026-04-09T15:40:00.000000"),  // gap > 15 min
            row("2026-04-09T16:05:00.000000")   // gap > 15 min
        )
        val result = parse("EB56EB9A4EF3_09.04.2026.csv", content)
        assertNotNull(result)
        assertEquals(3, result.trips.size)
        assertEquals(listOf(1, 2, 3), result.trips.map { it.tripIndex },
            "Trip indices must be 1-based and sequential")
    }

    @Test
    fun `date field in TripCsv matches filename date`() {
        val content = csv(row("2026-04-09T15:16:00.000000"))
        val result = parse("EB56EB9A4EF3_09.04.2026.csv", content)
        assertNotNull(result)
        result.trips.forEach { trip ->
            assertEquals("09.04.2026", trip.date,
                "TripCsv.date must match the date from the filename")
        }
    }

    @Test
    fun `rows with null timestamps are not used as gap boundaries`() {
        // Row 1: valid ts, Row 2: empty ts (null), Row 3: valid ts 1h later
        // Row 2 must be attached to the current group without triggering a split
        val content = listOf(DB_HEADER,
            row("2026-04-09T15:16:00.000000"),
            row("",                            ),  // empty timestamp
            row("2026-04-09T16:20:00.000000")   // 64 min later — would split if row 2 had ts
        ).joinToString("\n")

        val result = parse("EB56EB9A4EF3_09.04.2026.csv", content)
        assertNotNull(result)
        // Gap between row 1 and row 3 is 64 min: there SHOULD be a split
        // but only because rows 1 and 3 both have valid timestamps
        assertEquals(2, result.trips.size,
            "Gap between valid timestamps must still split even if there are null-ts rows in between")
    }

    // -------------------------------------------------------------------------
    // Timestamp format tolerance
    // -------------------------------------------------------------------------

    @Test
    fun `microsecond timestamp format is parsed`() {
        val content = csv(row("2026-03-27T20:56:46.948611"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)
        val lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 2, "Should have header + at least 1 data row")
    }

    @Test
    fun `millisecond timestamp format is parsed`() {
        val content = csv(row("2026-03-27T20:56:46.948"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)
        val lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 2, "Should have header + at least 1 data row")
    }

    @Test
    fun `second-precision timestamp format is parsed`() {
        val content = csv(row("2026-03-27T20:56:46"))
        val result = parse("187A3E9C56FC_27.03.2026.csv", content)
        assertNotNull(result)
        val lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 2, "Should have header + at least 1 data row")
    }
}
