package io.github.eucsoh

/**
 * Constants from soh_core_en.py
 */
object Constants {
    const val DEBUG = false
    
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
    
    // Metrics for CUSUM and trend detection
    val CUSUM_METRICS = listOf(
        "R_batt_median_25C",
        "R_mosfet_hot",
        "Req_median",
        "temp_motor_max",
        "temp_board_max",
        "I_phase2_int",
        "sag_95p"
    )
    
    val TREND_METRICS = listOf(
        "R_batt_median_25C",
        "R_mosfet_hot",
        "Req_median",
        "temp_motor_max",
        "temp_board_max",
        "I_phase2_int",
        "sag_95p"
    )
    
    val Y_LABELS = mapOf(
        "i_max" to "Max battery current (A)",
        "i_95p" to "Battery current 95th percentile (A)",
        "i_phase_max" to "Max phase current (A)",
        "i_phase_95p" to "Phase current 95th percentile (A)",
        "I_phase2_int" to "Phase I² dose – ∫ I_phase² dt (A²·s)",
        "R_batt_median" to "R_batt median (Ω)",
        "Req_median" to "Equivalent resistance median (Ω)",
        "Req_median_25C" to "Equivalent resistance median @25°C (Ω)",
        "Req_95p" to "Equivalent resistance 95th percentile (Ω)",
        "v_min_strong" to "Maximum voltage collapse under load (V)",
        "R_batt_median_25C" to "R_batt median @25°C (Ω)",
        "R_mosfet_hot" to "R_MOSFET hot (Ω)",
        "sag_95p" to "Sag 95th percentile (V)",
        "sag_max" to "Sag max (V)",
        "temp_board_max" to "Max board temperature (°C)",
        "temp_motor_max" to "Max motor temperature (°C)"
    )
}
