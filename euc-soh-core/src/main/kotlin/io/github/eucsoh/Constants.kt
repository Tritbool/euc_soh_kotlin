package io.github.eucsoh

/**
 * Constants from soh_core_en.py
 */
object Constants {
    const val DEBUG = true  // Activé pour débogage Android

    const val EUC_WORLD="euc_world"
    const val WHEELLOG="wheellog"

    // ANALYSIS PHASES
    const val ANALYZING = "Analyzing"
    const val CALIBRATING = "Calibrating"

    // Absolute limits
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
        R_PACK_NOMINAL("r_pack_nominal"),
        SOURCE("source"),
        N_POINTS("n_points"),


    }

    enum class Metrics(
        val csv_code: String,
        val higher_is_bad: Boolean = true,
        val label: String? = null
    ) {
        I_MAX("i_max", true, "Max battery current (A)"),
        I_95P("i_95p", true, "Battery current 95th percentile (A)"),
        I_PHASE_MAX("i_phase_max", true, "Max phase current (A)"),
        I_PHASE_95P("i_phase_95p", true, "Phase current 95th percentile (A)"),
        I_PHASE2_INT("I_phase2_int", true, "Phase I² dose – ∫ I_phase² dt (A²·s)"),
        R_BATT_MEDIAN("R_batt_median", true, "R_batt median (Ω)"),
        REQ_MEAN("Req_mean", true, "Equivalent resistance mean (Ω)"),
        REQ_MEDIAN("Req_median", true, "Equivalent resistance median (Ω)"),
        REQ_MEDIAN_25C("Req_median_25C", true, "Equivalent resistance median @25°C (Ω)"),
        REQ_95P("Req_95p", true, "Equivalent resistance 95th percentile (Ω)"),
        V_MIN_STRONG("v_min_strong", false, "Maximum voltage collapse under load (V)"),
        R_BATT_MEDIAN_25C("R_batt_median_25C", true, "R_batt median @25°C (Ω)"),
        R_MOSFET_HOT("R_mosfet_hot", true, "R_MOSFET hot (Ω)"),
        SAG_95P("sag_95p", true, "Sag 95th percentile (V)"),
        SAG_MAX("sag_max", true, "Sag max (V)"),
        TEMP_BOARD_MAX("temp_board_max", true, "Max board temperature (°C)"),
        TEMP_MOTOR_MAX("temp_motor_max", true, "Max motor temperature (°C)"),
        PWM_95P("pwm_95p", true, "PWM 95th percentile (%)"),

        PWM_MAX("pwm_max", true, "PWM max (%)")
    }

    // Metrics for CUSUM and trend detection
    val CUSUM_METRICS = listOf(
        Metrics.R_BATT_MEDIAN_25C.csv_code,
        Metrics.R_MOSFET_HOT.csv_code,
        Metrics.REQ_MEDIAN.csv_code,
        Metrics.REQ_MEDIAN_25C.csv_code,
        Metrics.TEMP_MOTOR_MAX.csv_code,
        Metrics.TEMP_BOARD_MAX.csv_code,
        Metrics.SAG_95P.csv_code
    )

    val TREND_METRICS = listOf(
        Metrics.R_BATT_MEDIAN_25C.csv_code,
        Metrics.R_MOSFET_HOT.csv_code,
        Metrics.REQ_MEDIAN.csv_code,
        Metrics.REQ_MEDIAN_25C.csv_code,
        Metrics.TEMP_MOTOR_MAX.csv_code,
        Metrics.TEMP_BOARD_MAX.csv_code,
        Metrics.SAG_95P.csv_code
    )
}
