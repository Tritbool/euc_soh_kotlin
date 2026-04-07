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

package io.github.eucsoh

/**
 * Constants
 */
object Constants {
    const val DEBUG = true  // Activé pour débogage Android

    const val EUC_WORLD = "euc_world"
    const val WHEELLOG = "wheellog"


    const val DARKNESS_BOT = "darkness_bot"

    // ANALYSIS PHASES
    const val ANALYZING = "Analyzing"
    const val CALIBRATING = "Calibrating"
    const val DONE = "Done"

    // Absolute limits
    const val LOWER_REQ = 0.0 // Ω
    const val MIN_POINTS = 50 // count
    const val ABS_REQ_LIMIT = 0.8  // Ω
    const val ABS_KM_LIMIT = 5000.0  // km
    const val ABS_REQ_FACTOR = 1.8

    // Battery parameters
    const val NOMINAL_CELL_V = 3.7  // V, nominal Li-ion voltage

    const val MAXIIMAL_CELL_V = 4.2  // V, maximal Li-ion voltage
    const val MINIIMAL_CELL_V = 3.0  // V, maximal Li-ion voltage
    val KNOWN_SERIES = listOf(16, 20, 24, 28, 30, 32, 40, 48, 52, 60, 64, 72)
    const val NS_MIN = 8
    const val NS_MAX = 80

    const val HIGHER_IS_BAD = "higher_is_bad"

    const val LOWER_IS_BAD = "lower_is_bad"

    enum class CommonColumns(val csv_code: String) {
        VOLTAGE("voltage"),
        CURRENT("current"),
        SPEED("speed"),
        DISTANCE("distance")
    }

    enum class WheelLogColumns(val csv_code: String) {
        SOC("battery_level"),
        BOARD_TEMPERATURE("temp"),
        MOTOR_TEMPERATURE("temp2"),
        CURRENT_PHASE("phase_current"),
        DATE("date"),
        TIME("time"),
        DISTANCE_TOTAL("totaldistance"),
        PWM("pwm")
    }

    /**
     * Native DarknessBot CSV column names (inside .dbb archives).
     * Date format: ISO 8601 with microseconds (2026-03-27T20:56:46.948611)
     * Distance unit: km (Total mileage)
     */
    enum class DarknessBotColumns(val csv_code: String) {
        TIMESTAMP("Date"),
        SPEED("Speed"),
        VOLTAGE("Voltage"),
        PWM("PWM"),
        CURRENT("Current"),
        POWER("Power"),
        SOC("Battery level"),
        DISTANCE_TOTAL("Total mileage"),
        TEMPERATURE("Temperature"),
        PITCH("Pitch"),
        ROLL("Roll"),
        LATITUDE("Latitude"),
        LONGITUDE("Longitude"),
        ALTITUDE("Altitude")
    }

    enum class EUCWorldColumns(val csv_code: String) {
        BOARD_TEMPERATURE("system_temp"),
        MOTOR_TEMPERATURE("temp_motor"),
        CURRENT_PHASE("current_phase"),
        SOC("battery"),
        TIMESTAMP("datetime"),
        GPS_TIMESTAMP("gps_datetime"),
        DISTANCE_TOTAL("distance_total"),
        PWM("safety_margin"),

        DURATION("duration")
    }

    enum class MetaColumns(val csv_code: String) {
        FILE("file"),
        DATETIME_FIRST("datetime_first"),
        WHEEL_KM("wheel_km"),
        WHEEL_KM_SOURCE("wheel_km_source"),
        SOC_REF_OK("soc_ref_ok"),
        SOC_REF_V_FULL("soc_ref_v_full"),
        NS("Ns"),
        NS_GLOBAL("ns_global"),
        V_NOMINAL("v_nominal"),
        V_IDLE("v_idle"),
        R_PACK_NOMINAL("R_pack_nominal"),
        SOURCE("source"),
        N_POINTS("n_points"),
        CSV_FILE("csv_file")


    }

    enum class Metrics(
        val csv_code: String,
        val higher_is_bad: Boolean = true,
        val label: String? = null
    ) {
        REQ_MEDIAN_25C("Req_median_25C", true, "Equivalent resistance median @25°C (Ω)"),
        REQ_MEDIAN("Req_median", true, "Equivalent resistance median (Ω)"),
        R_BATT_MEDIAN_25C("R_batt_median_25C", true, "R_batt median @25°C (Ω)"),
        R_BATT_MEDIAN("R_batt_median", true, "R_batt median (Ω)"),
        R_MOSFET_HOT("R_mosfet_hot", true, "R_MOSFET hot (Ω)"),
        SAG_95P("sag_95p", true, "Sag 95th percentile (V)"),
        SAG_MAX("sag_max", true, "Sag max (V)"),

        SAG_MEDIAN("sag_median", true, "Sag median (V)"),
        TEMP_BOARD_MAX("temp_board_max", true, "Max board temperature (°C)"),
        TEMP_MOTOR_MAX("temp_motor_max", true, "Max motor temperature (°C)"),
        I_MAX("i_max", true, "Max battery current (A)"),
        I_95P("i_95p", true, "Battery current 95th percentile (A)"),
        I_PHASE_MAX("i_phase_max", true, "Max phase current (A)"),
        I_PHASE_95P("i_phase_95p", true, "Phase current 95th percentile (A)"),
        I_PHASE2_INT("I_phase2_int", true, "Phase I² dose – ∫ I_phase² dt (A²·s)"),
        REQ_MEAN("Req_mean", true, "Equivalent resistance mean (Ω)"),
        REQ_95P("Req_95p", true, "Equivalent resistance 95th percentile (Ω)"),
        V_MIN_STRONG("v_min_strong", false, "Maximum voltage collapse under load (V)"),
        PWM_95P("pwm_95p", true, "PWM 95th percentile (%)"),
        PWM_MAX("pwm_max", true, "PWM max (%)")
    }

    // Metrics for CUSUM and trend detection
    val CUSUM_METRICS: Set<Metrics> = setOf(
        Metrics.R_BATT_MEDIAN_25C,
        Metrics.R_BATT_MEDIAN,
        Metrics.R_MOSFET_HOT,
        Metrics.REQ_MEDIAN,
        Metrics.REQ_MEDIAN_25C,
        Metrics.TEMP_MOTOR_MAX,
        Metrics.TEMP_BOARD_MAX,
        Metrics.SAG_95P
    )

    val TREND_METRICS: Set<Metrics> = setOf(
        Metrics.R_BATT_MEDIAN_25C,
        Metrics.R_BATT_MEDIAN,
        Metrics.R_MOSFET_HOT,
        Metrics.REQ_MEDIAN,
        Metrics.REQ_MEDIAN_25C,
        Metrics.TEMP_MOTOR_MAX,
        Metrics.TEMP_BOARD_MAX,
        Metrics.SAG_95P
    )
}
