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
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TuxWheel v2 (enriched) log schema regressions — see TuxWheel docs/log_format.md.
 * The v2 schema self-describes through its leading `schema` column ("tuxwheel-2"),
 * distances are in km, `timestamp_ms` (epoch ms) is the authoritative time and
 * `cell_voltages_v` exposes the pack's cell count.
 */
class TuxWheelV2ParsingTest {

    private companion object {
        const val NS = 16
        const val V_IDLE = 67.15
        const val V_RIDE = 66.05
        const val CURRENT_RIDE = 20.0
        const val SPEED_RIDE = 30.0
        const val TOTAL_DISTANCE_KM = 123.456
        const val BASE_TIMESTAMP_MS = 1732350000000L
        const val N_IDLE = 60
        const val N_RIDE = 60
    }

    private fun writeV2Csv(file: File) {
        file.bufferedWriter().use { w ->
            w.write(
                "schema,timestamp_ms,date,time,speed_kmh,voltage_v,phase_current_a,current_a," +
                    "power_w,torque_nm,pwm_pct,battery_pct,distance_km,total_distance_km," +
                    "ride_time_s,total_ride_time_s,total_power_on_time_s,mainboard_temp_c," +
                    "motor_temperature_c,mosfet_temperature_c,board_temperature_c," +
                    "imu_temperature_c,cpu_load_pct,tire_pressure_kpa,pitch_deg,roll_deg," +
                    "top_speed_kmh,speed_limit_kmh,dynamic_speed_limit_kmh,wheel_max_speed_kmh," +
                    "alarm1_speed_kmh,alarm2_speed_kmh,alarm3_speed_kmh,mode,charging," +
                    "fan_status,led_mode,light_mode,pedals_mode,alert_flags,wheel_alarm," +
                    "uses_miles,alert,manufacturer,model,serial_number,firmware_version," +
                    "cell_voltages_v\n"
            )
            // Idle rows: low current, no timestamps consumed for the Req filter.
            repeat(N_IDLE) { i ->
                writeV2Row(w, i, speed = 0.0, voltage = V_IDLE, current = 1.0, phase = 1.7)
            }
            // Ride rows: the points the Req pipeline keeps.
            repeat(N_RIDE) { i ->
                writeV2Row(w, N_IDLE + i, speed = SPEED_RIDE, voltage = V_RIDE, current = CURRENT_RIDE, phase = 34.6)
            }
        }
    }

    private val cellsLabel = "\"[" + List(NS) { 4.190 + it * 0.001 }
        .joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) } + "]\""

    private fun writeV2Row(
        w: java.io.BufferedWriter,
        index: Int,
        speed: Double,
        voltage: Double,
        current: Double,
        phase: Double
    ) {
        val us = java.util.Locale.US
        val cells = listOf(
            "tuxwheel-2",
            (BASE_TIMESTAMP_MS + index * 100L).toString(),
            "2026-11-23",
            "10:10:00.000",
            String.format(us, "%.3f", speed),
            String.format(us, "%.2f", voltage),
            String.format(us, "%.3f", phase),
            String.format(us, "%.3f", current),
            String.format(us, "%.2f", current * voltage),  // power_w
            "10.00",                                          // torque_nm
            "40.00",                                          // pwm_pct
            "80",                                             // battery_pct
            "1.2",                                            // distance_km
            TOTAL_DISTANCE_KM.toString(),                      // total_distance_km
            "0",                                              // ride_time_s
            "",                                               // total_ride_time_s
            "",                                               // total_power_on_time_s
            "30.00",                                          // mainboard_temp_c
            "45.00",                                          // motor_temperature_c
            "",                                               // mosfet_temperature_c
            "",                                               // board_temperature_c
            "",                                               // imu_temperature_c
            "",                                               // cpu_load_pct
            "",                                               // tire_pressure_kpa
            "",                                               // pitch_deg
            "",                                               // roll_deg
            "",                                               // top_speed_kmh
            "",                                               // speed_limit_kmh
            "",                                               // dynamic_speed_limit_kmh
            "",                                               // wheel_max_speed_kmh
            "",                                               // alarm1_speed_kmh
            "",                                               // alarm2_speed_kmh
            "",                                               // alarm3_speed_kmh
            "\"Drive\"",                                    // mode
            "false",                                          // charging
            "",                                               // fan_status
            "",                                               // led_mode
            "",                                               // light_mode
            "",                                               // pedals_mode
            "",                                               // alert_flags
            "false",                                          // wheel_alarm
            "false",                                          // uses_miles
            "0",                                              // alert
            "\"Gotway\"",                                   // manufacturer
            "\"Begode T4\"",                                // model
            "\"ABC123\"",                                   // serial_number
            "\"2.42\"",                                     // firmware_version
            cellsLabel                                        // cell_voltages_v
        )
        w.write(cells.joinToString(",") + "\n")
    }

    private fun analyze(file: File) =
        ReqStatsComputer.computeReqStatsForFile(csvPath = file.absolutePath)

    @Test
    fun `v2 schema is detected through the schema column without a TuxWheel path`() {
        val root = createTempDirectory("tuxwheel_v2_").toFile()
        try {
            // Deliberately NOT under a "TuxWheel" folder: detection must rely
            // on the self-describing `schema` column.
            val csv = File(root, "session.csv")
            writeV2Csv(csv)

            val result = analyze(csv)

            assertNotNull(result)
            assertEquals("tuxwheel", result.source)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `v2 metrics map onto the Req pipeline`() {
        val root = createTempDirectory("tuxwheel_v2_").toFile()
        try {
            val csv = File(root, "session.csv")
            writeV2Csv(csv)

            val result = analyze(csv)

            assertNotNull(result)
            assertEquals("tuxwheel", result.source)
            assertTrue((result.nPoints ?: 0) >= 50, "Should keep the ride points")
            assertEquals(30.0, assertIsClose(result.tempBoardMax, 30.0), "mainboard_temp_c")
            assertEquals(45.0, assertIsClose(result.tempMotorMax, 45.0), "motor_temperature_c")
            val reqMedian = assertNotNull(result.reqMedian)
            val expectedReq = (V_IDLE - V_RIDE) / CURRENT_RIDE
            assertTrue(
                kotlin.math.abs(reqMedian - expectedReq) < 0.005,
                "Req median should be ~$expectedReq Ω, got $reqMedian"
            )
            assertEquals(NS, result.ns, "Ns should come from cell_voltages_v")
            assertEquals("total_distance_km_tw2", result.wheelKmSource)
            assertEquals(
                TOTAL_DISTANCE_KM,
                assertIsClose(result.wheelKm, TOTAL_DISTANCE_KM),
                "v2 total_distance_km is already km"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `v2 timestamp_ms drives trapezoidal I²dt integration`() {
        val root = createTempDirectory("tuxwheel_v2_").toFile()
        try {
            val csv = File(root, "session.csv")
            writeV2Csv(csv)

            val result = analyze(csv)

            assertNotNull(result)
            assertEquals(false, result.fallbackDtUsed, "timestamp_ms should be used")
            // Ride rows: 60 points, 100 ms apart, |I_phase| = 34.6 A
            // ∑ I²dt = 34.6² × 0.1 × 59
            val expected = 34.6 * 34.6 * 0.1 * (N_RIDE - 1)
            val iPhase2Int = assertNotNull(result.iPhase2Int)
            assertTrue(
                kotlin.math.abs(iPhase2Int - expected) < 50.0,
                "I²dt expected ~$expected, got $iPhase2Int"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertIsClose(actual: Double?, expected: Double): Double {
        val v = assertNotNull(actual)
        assertTrue(kotlin.math.abs(v - expected) < 1e-6, "Expected ~$expected, got $v")
        return v
    }
}
