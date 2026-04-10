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
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [DarknessBotParser] against the real sample archive.
 *
 * Sample file: euc-soh-core/src/test/resources/real_logs/09.04.2026.dbb
 *
 * Archive contents (as of 09.04.2026.dbb):
 *   187A3E9C56FC_27.03.2026.csv   — 723 rows,  1 trip  (static, speed=0)
 *   DemoDeviceID_28.03.2026.csv   — 1377 rows         (must be ignored)
 *   187A3E9C7401_30.03.2026.csv   — 21 774 rows, 1 trip
 *   882584F038D3_31.03.2026.csv   — 49 055 rows, 1 trip
 *   C00EBB6A9016_04.04.2026.csv   — 278 rows,  1 trip  (static, speed=0)
 *   EB56EB9A4EF3_09.04.2026.csv   — 5 214 rows, 2 trips (gap ≈ 23.8 min at row 76)
 *
 * All tests skip gracefully if the .dbb file is not present.
 */
class DarknessBotIntegrationTest {

    private val dbbFile = File("src/test/resources/real_logs/09.04.2026.dbb")

    /** Parse every entry in the archive; return entry-name → result (null for rejected). */
    private fun parseAll(): Map<String, DarknessBotTripResult?> {
        val results = mutableMapOf<String, DarknessBotTripResult?>()
        ZipInputStream(dbbFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = File(entry.name).name
                    val bytes = zip.readBytes()
                    results[entryName] = DarknessBotParser.parse(entryName, bytes.inputStream())
                }
                entry = zip.nextEntry
            }
        }
        return results
    }

    // -------------------------------------------------------------------------
    // Archive structure
    // -------------------------------------------------------------------------

    @Test
    fun `archive contains exactly 6 entries`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        var count = 0
        ZipInputStream(dbbFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) count++
                entry = zip.nextEntry
            }
        }
        assertEquals(6, count, "Archive must contain exactly 6 entries")
    }

    @Test
    fun `DemoDeviceID entry is rejected`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val results = parseAll()
        val demoResult = results["DemoDeviceID_28.03.2026.csv"]
        // Key must exist (entry was processed) but value must be null (rejected)
        assertTrue(results.containsKey("DemoDeviceID_28.03.2026.csv"),
            "DemoDeviceID entry must be present in the archive")
        assertEquals(null, demoResult,
            "DemoDeviceID entry must be rejected by the parser")
    }

    @Test
    fun `5 valid entries are parsed successfully`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val results = parseAll()
        val accepted = results.values.filterNotNull()
        assertEquals(5, accepted.size,
            "Exactly 5 non-Demo entries must produce non-null results")
    }

    // -------------------------------------------------------------------------
    // Per-entry trip count
    // -------------------------------------------------------------------------

    @Test
    fun `187A3E9C56FC_27_03_2026 produces exactly 1 trip`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["187A3E9C56FC_27.03.2026.csv"]
        assertNotNull(result, "187A3E9C56FC entry must parse successfully")
        assertEquals(1, result.trips.size,
            "187A3E9C56FC (723 rows, no gap) must produce exactly 1 trip")
    }

    @Test
    fun `187A3E9C7401_30_03_2026 produces exactly 1 trip`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["187A3E9C7401_30.03.2026.csv"]
        assertNotNull(result, "187A3E9C7401 entry must parse successfully")
        assertEquals(1, result.trips.size,
            "187A3E9C7401 (21 774 rows, no gap) must produce exactly 1 trip")
    }

    @Test
    fun `882584F038D3_31_03_2026 produces exactly 1 trip`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["882584F038D3_31.03.2026.csv"]
        assertNotNull(result, "882584F038D3 entry must parse successfully")
        assertEquals(1, result.trips.size,
            "882584F038D3 (49 055 rows, no gap) must produce exactly 1 trip")
    }

    @Test
    fun `EB56EB9A4EF3_09_04_2026 produces exactly 2 trips`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["EB56EB9A4EF3_09.04.2026.csv"]
        assertNotNull(result, "EB56EB9A4EF3 entry must parse successfully")
        assertEquals(2, result.trips.size,
            "EB56EB9A4EF3 has a ~23.8 min gap at row 76 and must produce exactly 2 trips")
    }

    @Test
    fun `C00EBB6A9016_04_04_2026 produces exactly 1 trip`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["C00EBB6A9016_04.04.2026.csv"]
        assertNotNull(result, "C00EBB6A9016 entry must parse successfully")
        assertEquals(1, result.trips.size,
            "C00EBB6A9016 (278 rows, no gap) must produce exactly 1 trip")
    }

    // -------------------------------------------------------------------------
    // MAC address extraction
    // -------------------------------------------------------------------------

    @Test
    fun `MAC addresses are extracted in WheelLog underscore format`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val results = parseAll()

        // MAC underscore format: pairs of 2 hex chars joined by '_'
        val macPattern = Regex("^([0-9A-F]{2}_){5}[0-9A-F]{2}$")

        results.values.filterNotNull().forEach { result ->
            assertTrue(
                macPattern.matches(result.macAddress),
                "MAC '${result.macAddress}' must match XX_XX_XX_XX_XX_XX format"
            )
        }
    }

    @Test
    fun `187A3E9C56FC MAC is correctly formatted`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["187A3E9C56FC_27.03.2026.csv"]
        assertNotNull(result)
        assertEquals("18_7A_3E_9C_56_FC", result.macAddress,
            "187A3E9C56FC must be formatted as 18_7A_3E_9C_56_FC")
    }

    // -------------------------------------------------------------------------
    // Output CSV format
    // -------------------------------------------------------------------------

    @Test
    fun `all trips contain mandatory WheelLog columns`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val requiredCols = listOf(
            WheelLogColumns.DATE.csv_code,
            WheelLogColumns.TIME.csv_code,
            CommonColumns.SPEED.csv_code,
            CommonColumns.VOLTAGE.csv_code,
            WheelLogColumns.PWM.csv_code,
            CommonColumns.CURRENT.csv_code,
            WheelLogColumns.SOC.csv_code,
            WheelLogColumns.DISTANCE_TOTAL.csv_code,
            WheelLogColumns.BOARD_TEMPERATURE.csv_code
        )

        parseAll().values.filterNotNull().flatMap { it.trips }.forEach { trip ->
            val header = trip.csvContent.lines().first { it.isNotBlank() }.split(",")
            requiredCols.forEach { col ->
                assertTrue(col in header,
                    "Trip CSV header must contain '$col'. Got: $header")
            }
        }
    }

    @Test
    fun `every trip CSV has at least one data row`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        parseAll().values.filterNotNull().flatMap { it.trips }.forEach { trip ->
            val dataLines = trip.csvContent.lines().filter { it.isNotBlank() }.drop(1) // skip header
            assertTrue(dataLines.isNotEmpty(),
                "Trip ${trip.tripIndex} of date ${trip.date} must have at least one data row")
        }
    }

    @Test
    fun `882584F038D3 last totaldistance is approx 8264559 metres`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["882584F038D3_31.03.2026.csv"]
        assertNotNull(result)

        val trip = result.trips[0]
        val lines = trip.csvContent.lines().filter { it.isNotBlank() }
        val header = lines[0].split(",")
        val distIdx = header.indexOf(WheelLogColumns.DISTANCE_TOTAL.csv_code)
        assertTrue(distIdx >= 0, "totaldistance column must exist")

        // Find last non-empty totaldistance value
        val lastDist = lines.drop(1).mapNotNull { line ->
            val cells = line.split(",")
            cells.getOrNull(distIdx)?.trim()?.toLongOrNull()
        }.lastOrNull()

        assertNotNull(lastDist, "At least one totaldistance value must be non-empty")
        // 8264.56 km × 1000 = 8264560 m — allow ±5 m for floating-point rounding
        assertTrue(
            lastDist in 8264554L..8264564L,
            "Last totaldistance must be ≈ 8264559 m (8264.56 km × 1000), got: $lastDist"
        )
    }

    @Test
    fun `EB56EB9A4EF3 trip 2 has more rows than trip 1`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["EB56EB9A4EF3_09.04.2026.csv"]
        assertNotNull(result)
        assertEquals(2, result.trips.size)

        val trip1Lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }.size - 1
        val trip2Lines = result.trips[1].csvContent.lines().filter { it.isNotBlank() }.size - 1

        println("  EB56 trip1=$trip1Lines rows, trip2=$trip2Lines rows")
        assertTrue(trip2Lines > trip1Lines,
            "Trip 2 (5138 rows) must be larger than trip 1 (76 rows)")
    }

    // -------------------------------------------------------------------------
    // Data sanity checks on a known entry
    // -------------------------------------------------------------------------

    @Test
    fun `187A3E9C56FC voltage values are within expected range`() {
        if (!dbbFile.exists()) { println("⚠️ Skipping: 09.04.2026.dbb not found"); return }

        val result = parseAll()["187A3E9C56FC_27.03.2026.csv"]
        assertNotNull(result)

        val lines = result.trips[0].csvContent.lines().filter { it.isNotBlank() }
        val header = lines[0].split(",")
        val voltIdx = header.indexOf(CommonColumns.VOLTAGE.csv_code)
        assertTrue(voltIdx >= 0, "voltage column must exist")

        val voltages = lines.drop(1).mapNotNull { line ->
            line.split(",").getOrNull(voltIdx)?.trim()?.toDoubleOrNull()
        }

        assertTrue(voltages.isNotEmpty(), "Must have voltage values")
        // Known range from sample inspection: 78.38 – 125.40 V
        assertTrue(voltages.min() >= 78.0,
            "Min voltage must be ≥ 78.0 V, got: ${voltages.min()}")
        assertTrue(voltages.max() <= 126.0,
            "Max voltage must be ≤ 126.0 V, got: ${voltages.max()}")
    }
}
