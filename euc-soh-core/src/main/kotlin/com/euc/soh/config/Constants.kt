// Constants.kt
package com.euc.soh.config

object Constants {
    // Limites absolues
    const val ABS_REQ_LIMIT = 0.8 // Ω
    const val ABS_KM_LIMIT = 5000.0 // km
    const val ABS_REQ_FACTOR = 1.8
    
    // Tension nominale cellule Li-ion
    const val NOMINAL_CELL_V = 3.7 // V
    
    // Séries connues
    val KNOWN_SERIES = listOf(16, 20, 24, 28, 30, 32, 40, 48, 52, 60, 64, 72)
    const val NS_MIN = 8
    const val NS_MAX = 80
    
    // Métriques CUSUM
    val CUSUM_METRICS = listOf(
        "R_batt_median_25C",
        "R_mosfet_hot",
        "Req_median",
        "I_phase2_int",
        "sag_95p"
    )
    
    // Métriques tendance
    val TREND_METRICS = listOf(
        "R_batt_median_25C",
        "R_mosfet_hot",
        "Req_median",
        "I_phase2_int",
        "sag_95p"
    )
    
    // Labels Y pour graphiques
    val Y_LABELS = mapOf(
        "i_max" to "Courant batterie max (A)",
        "i_95p" to "Courant batterie 95ᵉ percentile (A)",
        "i_phase_max" to "Courant de phase max (A)",
        "i_phase_95p" to "Courant de phase 95ᵉ percentile (A)",
        "I_phase2_int" to "Dose I² de phase – ∫ I_phase² dt (A²·s)",
        "R_batt_median" to "R_batt médiane (Ω)",
        "Req_median" to "Résistance équivalente médiane (Ω)",
        "Req_median_25C" to "Résistance équivalente médiane @25°C (Ω)",
        "Req_95p" to "Résistance équivalent 95ᵉ percentile (Ω)",
        "v_min_strong" to "Effondrement maximal de la tension sous charge (V)",
        "R_batt_median_25C" to "R_batt médiane @25°C (Ω)",
        "R_mosfet_hot" to "R_MOSFET à chaud (Ω)",
        "sag_95p" to "Sag 95ᵉ percentile (V)",
        "sag_max" to "Sag max (V)",
        "temp_board_max" to "Température carte max (°C)",
        "temp_motor_max" to "Température moteur max (°C)"
    )
}
