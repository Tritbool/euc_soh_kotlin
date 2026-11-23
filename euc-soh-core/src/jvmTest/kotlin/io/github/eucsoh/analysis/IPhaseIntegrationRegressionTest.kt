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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Deterministic regression tests for I²dt (iPhase2Int) computation.
 *
 * These tests fix exact expected values so that any change in timestamp parsing
 * (e.g. DateTimeFormatter producing the dt=0.1 fallback more often) will be
 * caught immediately.  The tolerance is ±1.0 A²·s — tight enough to detect a
 * factor-of-~10 drift between real-timestamp and fallback-timestamp paths.
 *
 * Fixture design (WheelLog, 6 "riding" rows, phase_current in A, 1 s apart):
 *   phase_current = [30, 40, 50, 60, 50, 40]
 *
 * Trapezoidal integral (real timestamps, dt = 1 s each step):
 *   step 0→1: 0.5*(900+1600)*1  = 1250
 *   step 1→2: 0.5*(1600+2500)*1 = 2050
 *   step 2→3: 0.5*(2500+3600)*1 = 3050
 *   step 3→4: 0.5*(3600+2500)*1 = 3050
 *   step 4→5: 0.5*(2500+1600)*1 = 2050
 *   total = 11450.0 A²·s
 *
 * Fallback integral (dt = 0.1 s, no timestamps):
 *   (900+1600+2500+3600+2500+1600) * 0.1 = 1270.0 A²·s
 *
 * tTotal = 5 s < 10 s → no normalization in either case.
 */
class IPhaseIntegrationRegressionTest {

    companion object {
        private const val TOLERANCE = 1.0

        // 20S pack: 84 V nominal.  PackInference window → i_Min ≈ 12, i_Max = 150.
        // Use phase_current = 30..60 A so all rows pass the [12, 150] filter.
        // Speed = 25 km/h (> 20 thr), battery = 50% (in [20, 90] window).
        private val PHASE_CURRENTS = listOf(30.0, 40.0, 50.0, 60.0, 50.0, 40.0)
        private const val VOLTAGE_V = 84.0  // 20S ~ 4.2 V/cell
        private const val SPEED_KMH = 25.0
        private const val BATTERY_PCT = 50

        /** Expected I²dt with real timestamps (trapezoidal, dt=1 s). */
        const val EXPECTED_REAL_TS = 11450.0

        /** Expected I²dt with fallback dt=0.1 s. */
        const val EXPECTED_FALLBACK = 1270.0

        // ── fixture writers ──────────────────────────────────────────────────

        /**
         * Writes a WheelLog-format CSV to [file].
         * [timestamps] is a list of "date,time" string pairs; if null or empty
         * every row gets an unparseable timestamp so the fallback is forced.
         */
        fun writeWheelLogCsv(file: File, timestamps: List<Pair<String, String>>?) {
            // WheelLog header (subset of real columns — only the ones ReqStatsComputer reads)
            file.bufferedWriter().use { w ->
                w.write("date,time,speed,voltage,phase_current,current,battery_level,totaldistance,latitude,longitude,gps_speed,gps_heading,system_temp,pitch,roll,mode,alert\n")
                PHASE_CURRENTS.forEachIndexed { i, iphase ->
                    val (date, time) = if (!timestamps.isNullOrEmpty() && i < timestamps.size)
                        timestamps[i] else Pair("BADDATE", "BADTIME")
                    // current (battery) = iphase / 1.73 so the ratio stays in range
                    val ibatt = iphase / 1.73
                    w.write("$date,$time,$SPEED_KMH,$VOLTAGE_V,$iphase,$ibatt,$BATTERY_PCT,100,0.0,0.0,0.0,0.0,30.0,0.0,0.0,0,\n")
                }
            }
        }

        /**
         * Writes an EUC World-format CSV to [file].
         * [useColonOffset] selects between "+0200" and "+02:00" offset format.
         */
        fun writeEucWorldCsv(file: File, useColonOffset: Boolean) {
            val offsetStr = if (useColonOffset) "+02:00" else "+0200"
            file.bufferedWriter().use { w ->
                w.write("datetime,speed,voltage,current,current_phase,motor_power,battery_level,totaldistance,latitude,longitude\n")
                PHASE_CURRENTS.forEachIndexed { i, iphase ->
                    val ts = "2024-06-01T10:00:0${i}.000${offsetStr}"
                    val ibatt = iphase / 1.73
                    w.write("$ts,$SPEED_KMH,$VOLTAGE_V,$ibatt,$iphase,1000,$BATTERY_PCT,100,0.0,0.0\n")
                }
            }
        }

        /** Builds a list of 1-second-apart WheelLog timestamp pairs. */
        fun buildWheelLogTimestamps(): List<Pair<String, String>> =
            PHASE_CURRENTS.indices.map { i -> Pair("2024-06-01", "10:00:0${i}") }
    }

    // ── WheelLog tests ───────────────────────────────────────────────────────

    @Test
    fun `WheelLog valid timestamps should use trapezoidal integration`() {
        val csv = File.createTempFile("wl_valid_ts_", ".csv")
        try {
            writeWheelLogCsv(csv, buildWheelLogTimestamps())
            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = csv.absolutePath,
                csvSource = null,
                speedThr = 20.0,
                curThr = 5.0
            )
            assertNotNull(result, "Should compute stats for valid WheelLog CSV")
            val iPhase2Int = result.iPhase2Int
            assertNotNull(iPhase2Int, "iPhase2Int should be non-null")
            assertEquals(
                false, result.fallbackDtUsed,
                "fallbackDtUsed must be false when real timestamps are present"
            )
            assertTrue(
                kotlin.math.abs(iPhase2Int - EXPECTED_REAL_TS) <= TOLERANCE,
                "iPhase2Int with real timestamps: expected $EXPECTED_REAL_TS ± $TOLERANCE, got $iPhase2Int"
            )
        } finally {
            csv.delete()
        }
    }

    @Test
    fun `WheelLog broken timestamps should fall back to dt=0,1s`() {
        val csv = File.createTempFile("wl_broken_ts_", ".csv")
        try {
            writeWheelLogCsv(csv, null)   // unparseable timestamps
            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = csv.absolutePath,
                csvSource = null,
                speedThr = 20.0,
                curThr = 5.0
            )
            assertNotNull(result, "Should compute stats even when timestamps are broken")
            val iPhase2Int = result.iPhase2Int
            assertNotNull(iPhase2Int, "iPhase2Int should be non-null even with fallback")
            assertEquals(
                true, result.fallbackDtUsed,
                "fallbackDtUsed must be true when timestamps cannot be parsed"
            )
            assertTrue(
                kotlin.math.abs(iPhase2Int - EXPECTED_FALLBACK) <= TOLERANCE,
                "iPhase2Int with fallback dt=0.1s: expected $EXPECTED_FALLBACK ± $TOLERANCE, got $iPhase2Int"
            )
        } finally {
            csv.delete()
        }
    }

    // ── EUC World tests ──────────────────────────────────────────────────────

    @Test
    fun `EUC World '+0200' offset should parse timestamps without fallback`() {
        val csv = File.createTempFile("ew_nocolon_", ".csv")
        try {
            writeEucWorldCsv(csv, useColonOffset = false)
            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = csv.absolutePath,
                csvSource = null,
                speedThr = 20.0,
                curThr = 5.0
            )
            assertNotNull(result, "Should compute stats for EUC World CSV with '+0200' offset")
            assertEquals(
                false, result.fallbackDtUsed,
                "fallbackDtUsed must be false for EUC World '+0200' timestamps"
            )
        } finally {
            csv.delete()
        }
    }

    @Test
    fun `EUC World '+02_00' colon offset should parse timestamps without fallback`() {
        val csv = File.createTempFile("ew_colon_", ".csv")
        try {
            writeEucWorldCsv(csv, useColonOffset = true)
            val result = ReqStatsComputer.computeReqStatsForFile(
                csvPath = csv.absolutePath,
                csvSource = null,
                speedThr = 20.0,
                curThr = 5.0
            )
            assertNotNull(result, "Should compute stats for EUC World CSV with '+02:00' offset")
            assertEquals(
                false, result.fallbackDtUsed,
                "fallbackDtUsed must be false for EUC World '+02:00' timestamps — " +
                        "if this fails the EUC_WORLD_TS_FMTS list is missing the XXX formatter"
            )
        } finally {
            csv.delete()
        }
    }
}
