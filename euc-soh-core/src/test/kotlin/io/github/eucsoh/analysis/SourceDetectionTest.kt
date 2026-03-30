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

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for SourceDetection.
 * Validates CSV format detection for EUC World vs WheelLog.
 */
class SourceDetectionTest {

    @Test
    fun `detectSource identifies EUC World format`() {
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-01 10:00:00", "2024-01-01 10:00:01"),
            "distance_total" to listOf(100.5, 100.6),
            "voltage" to listOf(83.5, 83.4),
            "current" to listOf(5.2, 5.3)
        )

        val source = SourceDetection.detectSource(df)

        assertEquals("euc_world", source, "Should detect EUC World format")
    }

    @Test
    fun `detectSource identifies WheelLog format`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-01", "2024-01-01"),
            "time" to listOf("10:00:00", "10:00:01"),
            "totaldistance" to listOf(100500, 100510),  // meters
            "voltage" to listOf(83.5, 83.4),
            "current" to listOf(5.2, 5.3)
        )

        val source = SourceDetection.detectSource(df)

        assertEquals("wheellog", source, "Should detect WheelLog format")
    }

    @Test
    fun `detectSource defaults to euc_world if datetime present`() {
        // Has datetime but not distance_total
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-01 10:00:00"),
            "voltage" to listOf(83.5),
            "current" to listOf(5.2)
        )

        val source = SourceDetection.detectSource(df)

        assertEquals("euc_world", source, "datetime column should indicate EUC World")
    }

    @Test
    fun `detectSource defaults to wheellog if date and time present`() {
        // Has date/time but not totaldistance
        val df = dataFrameOf(
            "date" to listOf("2024-01-01"),
            "time" to listOf("10:00:00"),
            "voltage" to listOf(83.5)
        )

        val source = SourceDetection.detectSource(df)

        assertEquals("wheellog", source, "date+time columns should indicate WheelLog")
    }

    @Test
    fun `normalizeDistanceTotal for EUC World distance_total in km`() {
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-01 10:00:00", "2024-01-01 10:05:00"),
            "distance_total" to listOf(1234.5, 1235.2)
        )

        val (wheelKm, source) = SourceDetection.normalizeDistanceTotal(df, "euc_world")

        assertEquals(1235.2, wheelKm, "Should return max distance_total in km")
        assertEquals("distance_total_km_euc", source, "Should identify source correctly")
    }

    @Test
    fun `normalizeDistanceTotal for WheelLog totaldistance in meters`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-01", "2024-01-01"),
            "time" to listOf("10:00:00", "10:05:00"),
            "totaldistance" to listOf(1234500, 1235200)  // meters
        )

        val (wheelKm, source) = SourceDetection.normalizeDistanceTotal(df, "wheellog")

        assertEquals(1235.2, wheelKm?: Double.MAX_VALUE, 0.1, "Should convert meters to km")
        assertEquals("totaldistance_m_wl", source, "Should identify WheelLog source")
    }

    @Test
    fun `normalizeDistanceTotal fallback to distance column for EUC World`() {
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-01 10:00:00", "2024-01-01 10:05:00"),
            "distance" to listOf(5.0, 5.7)  // Log distance (km per session)
        )

        val (wheelKm, source) = SourceDetection.normalizeDistanceTotal(df, "euc_world")

        assertEquals(5.7, wheelKm, "Should use distance column as fallback")
        assertEquals("distance_log_km_euc", source, "Should indicate fallback source")
    }

    @Test
    fun `normalizeDistanceTotal fallback to distance column for WheelLog`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-01", "2024-01-01"),
            "time" to listOf("10:00:00", "10:05:00"),
            "distance" to listOf(5.0, 5.7)  // Already in km
        )

        val (wheelKm, source) = SourceDetection.normalizeDistanceTotal(df, "wheellog")

        assertEquals(5.7, wheelKm, "Should use distance column as fallback")
        assertEquals("distance_log_km_wl", source, "Should indicate WheelLog fallback")
    }

    @Test
    fun `normalizeDistanceTotal returns null when no distance columns`() {
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-01 10:00:00"),
            "voltage" to listOf(83.5)
        )

        val (wheelKm, source) = SourceDetection.normalizeDistanceTotal(df, "euc_world")

        assertEquals(null, wheelKm, "Should return null when no distance available")
        assertEquals(null, source, "Source should be null")
    }

    @Test
    fun `getFirstDatetime for EUC World`() {
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-15 14:30:25", "2024-01-15 14:30:26")
        )

        val firstDt = SourceDetection.getFirstDatetime(df, "euc_world")

        assertEquals("2024-01-15 14:30:25", firstDt, "Should extract first datetime")
    }

    @Test
    fun `getFirstDatetime for WheelLog combines date and time`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-15", "2024-01-15"),
            "time" to listOf("14:30:25", "14:30:26")
        )

        val firstDt = SourceDetection.getFirstDatetime(df, "wheellog")

        assertEquals("2024-01-15 14:30:25", firstDt, "Should combine date and time")
    }

    @Test
    fun `getFirstDatetime for WheelLog with only date`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-15", "2024-01-16")
        )

        val firstDt = SourceDetection.getFirstDatetime(df, "wheellog")

        assertEquals("2024-01-15", firstDt, "Should use date only if time missing")
    }

    @Test
    fun `getFirstDatetime returns null when no datetime columns`() {
        val df = dataFrameOf(
            "voltage" to listOf(83.5, 83.4)
        )

        val firstDt = SourceDetection.getFirstDatetime(df, "euc_world")

        assertEquals(null, firstDt, "Should return null when no datetime info")
    }

    @Test
    fun `getFirstDatetime prefers datetime over gps_datetime`() {
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-15 14:30:25"),
            "gps_datetime" to listOf("2024-01-15 14:30:20")  // GPS might be slightly different
        )

        val firstDt = SourceDetection.getFirstDatetime(df, "euc_world")

        assertEquals("2024-01-15 14:30:25", firstDt, "Should prefer datetime over gps_datetime")
    }

    @Test
    fun `getFirstDatetime uses gps_datetime as fallback`() {
        val df = dataFrameOf(
            "gps_datetime" to listOf("2024-01-15 14:30:20", "2024-01-15 14:30:21")
        )

        val firstDt = SourceDetection.getFirstDatetime(df, "euc_world")

        assertEquals("2024-01-15 14:30:20", firstDt, "Should use gps_datetime as fallback")
    }

    @Test
    fun `detectSource mixed format prefers EUC World indicators`() {
        // Edge case: has both datetime and date/time
        val df = dataFrameOf(
            "datetime" to listOf("2024-01-01 10:00:00"),
            "date" to listOf("2024-01-01"),
            "time" to listOf("10:00:00"),
            "distance_total" to listOf(100.5)
        )

        val source = SourceDetection.detectSource(df)

        assertEquals("euc_world", source, "Should prefer EUC World when both indicators present")
    }
}
