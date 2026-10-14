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

class TuxWheelSourceDetectionTest {

    @Test
    fun `TuxWheel source is detected from filesystem and SAF folders`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-01"),
            "time" to listOf("10:00:00"),
            "totaldistance" to listOf(100500)
        )
        val paths = listOf(
            "/storage/emulated/0/Download/TuxWheel/wheel/log.csv",
            "/storage/emulated/0/Download/tuxwheel/wheel/log.csv",
            "content://provider/tree/primary%3ADownload%2FTuxWheel%2Fwheel"
        )

        paths.forEach { path ->
            assertEquals("tuxwheel", SourceDetection.detectSource(df, path), path)
        }
    }

    @Test
    fun `TuxWheel name outside a folder segment does not change the source`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-01"),
            "time" to listOf("10:00:00"),
            "totaldistance" to listOf(100500)
        )

        assertEquals("wheellog", SourceDetection.detectSource(df, "/logs/TuxWheel.csv"))
        assertEquals("wheellog", SourceDetection.detectSource(df, "/logs/NotTuxWheel/log.csv"))
    }

    @Test
    fun `TuxWheel totaldistance is converted from meters to kilometers`() {
        val df = dataFrameOf(
            "totaldistance" to listOf(1235200, 1234500)
        )

        val (wheelKm, source) = SourceDetection.normalizeDistanceTotal(df, "tuxwheel")

        assertEquals(1235.2, wheelKm)
        assertEquals("totaldistance_m_wl", source)
    }

    @Test
    fun `TuxWheel distance fallback is already in kilometers`() {
        val df = dataFrameOf(
            "distance" to listOf(5.0, 5.7)
        )

        val (wheelKm, source) = SourceDetection.normalizeDistanceTotal(df, "tuxwheel")

        assertEquals(5.7, wheelKm)
        assertEquals("distance_log_km_wl", source)
    }

    @Test
    fun `TuxWheel missing distance columns return no distance`() {
        val df = dataFrameOf("voltage" to listOf(83.5))

        assertEquals(null to null, SourceDetection.normalizeDistanceTotal(df, "tuxwheel"))
    }

    @Test
    fun `TuxWheel first datetime combines date and time`() {
        val df = dataFrameOf(
            "date" to listOf("2024-01-15", "2024-01-15"),
            "time" to listOf("14:30:25", "14:30:26")
        )

        assertEquals("2024-01-15 14:30:25", SourceDetection.getFirstDatetime(df, "tuxwheel"))
    }

    @Test
    fun `TuxWheel first datetime falls back to date only`() {
        val df = dataFrameOf("date" to listOf("2024-01-15", "2024-01-16"))

        assertEquals("2024-01-15", SourceDetection.getFirstDatetime(df, "tuxwheel"))
    }

    @Test
    fun `TuxWheel missing datetime columns return no datetime`() {
        val df = dataFrameOf("voltage" to listOf(83.5, 83.4))

        assertEquals(null, SourceDetection.getFirstDatetime(df, "tuxwheel"))
    }
}
