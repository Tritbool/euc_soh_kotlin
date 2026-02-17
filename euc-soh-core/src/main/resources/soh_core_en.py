# soh_core.py - Version 3 with automatic Arrhenius calibration + CUSUM + complete PDF
# + Vidle_local(SoC) with selection of real quasi-idle plateaus

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import io
from pathlib import Path
from matplotlib.backends.backend_pdf import PdfPages
from scipy.stats import linregress

DEBUG = False

# ABSOLUTE LIMITS:
ABS_REQ_LIMIT = 0.8  # Ω (to adjust)
ABS_KM_LIMIT = 5000.0  # km
ABS_REQ_FACTOR = 1.8
NOMINAL_CELL_V = 3.7  # V, nominal Li-ion voltage (NMC/NCA) for Wh calculation = Ah * V
KNOWN_SERIES = [16, 20, 24, 28, 30, 32, 40, 48, 52, 60, 64, 72]
NS_MIN = 8
NS_MAX = 80
CUSUM_METRICS = ["R_batt_median_25C", "R_mosfet_hot", "Req_median",'temp_motor_max','temp_board_max', "I_phase2_int", "sag_95p"]
TREND_METRICS = ["R_batt_median_25C", "R_mosfet_hot", "Req_median",'temp_motor_max','temp_board_max', "I_phase2_int", "sag_95p"]

Y_LABELS = {
    "i_max": "Max battery current (A)",
    "i_95p": "Battery current 95th percentile (A)",
    "i_phase_max": "Max phase current (A)",
    "i_phase_95p": "Phase current 95th percentile (A)",
    "I_phase2_int": "Phase I² dose – ∫ I_phase² dt (A²·s)",
    "R_batt_median": "R_batt median (Ω)",
    "Req_median": "Equivalent resistance median (Ω)",
    "Req_median_25C": "Equivalent resistance median @25°C (Ω)",
    "Req_95p": "Equivalent resistance 95th percentile (Ω)",
    "v_min_strong": "Maximum voltage collapse under load (V)",
    "R_batt_median_25C": "R_batt median @25°C (Ω)",
    "R_mosfet_hot": "R_MOSFET hot (Ω)",
    "sag_95p": "Sag 95th percentile (V)",
    "sag_max": "Sag max (V)",
    "temp_board_max": "Max board temperature (°C)",
    "temp_motor_max": "Max motor temperature (°C)",
}


# ---------- Simple MOSFET model (optional) ----------
class MOSFETParams:
    """
    MOSFET parameters entered by the user.
    We assume the user already enters the aggregated values for 1 wheel:
    - r_ds_on_25c_total: Total equivalent R_ds(on) of the MOSFET bridge @ 25°C (ohms)
      -> includes the number of FETs in parallel, topology, etc.
    - temp_coeff_rel: Relative temperature coefficient (≈ 0.01 -> +1%/°C)
      R(T) = R_25C * (1 + temp_coeff_rel * (T - 25))
    - r_wiring: Fixed additional resistance (shunts/busbars) outside pack
    """

    def __init__(
        self,
        r_ds_on_25c_total: float,
        temp_coeff_rel: float = 0.01,
        r_wiring: float = 0.0,
    ):
        self.r_ds_on_25c_total = float(r_ds_on_25c_total)
        self.temp_coeff_rel = float(temp_coeff_rel)
        self.r_wiring = float(r_wiring)

    def r_mosfet_at_temp(self, temp_c: float | None) -> float:
        """
        Returns the equivalent MOSFET+busbar resistance at the given temperature.
        If temperature is not available, returns the value at 25°C.
        """
        if temp_c is None or np.isnan(temp_c):
            return self.r_ds_on_25c_total + self.r_wiring
        delta_t = temp_c - 25.0
        r_hot = self.r_ds_on_25c_total * (1.0 + self.temp_coeff_rel * delta_t)
        return max(0.0, r_hot + self.r_wiring)


# ---------- Battery thermal normalization (Arrhenius) ----------
def normalize_r_batt_to_25c(
    r_batt_measured: float,
    temp_measured_c: float | None,
    ea_j_per_mol: float = 20000.0,
) -> float:
    """
    Normalizes the measured battery resistance to a reference temperature (25°C)
    using the Arrhenius law for Li-ion batteries.
    """
    # Invalid cases
    if r_batt_measured is None or temp_measured_c is None:
        return r_batt_measured if r_batt_measured is not None else 0.0
    if np.isnan(r_batt_measured) or np.isnan(temp_measured_c):
        return float(np.nan)

    # Constants
    R_gas = 8.314  # J/(mol·K), universal gas constant
    T_ref_c = 25.0  # °C, reference temperature
    T_ref_k = T_ref_c + 273.15  # Kelvin
    T_meas_k = temp_measured_c + 273.15  # Kelvin

    # Safety guards
    if T_meas_k < 263.15 or T_meas_k > 353.15:
        if DEBUG:
            print(f"[WARNING] normalize_r_batt_to_25c: T={temp_measured_c}°C outside reliable range")

    # Arrhenius calculation
    exponent = (ea_j_per_mol / R_gas) * (1.0 / T_meas_k - 1.0 / T_ref_k)
    exponent = np.clip(exponent, -100, 100)
    factor = np.exp(exponent)
    r_batt_25c = r_batt_measured / factor
    return float(max(0.0, r_batt_25c))


# ---------- Automatic Arrhenius calibration from data ----------
def calibrate_ea_from_logs(
    df_stats: pd.DataFrame,
    metric: str = "Req_median",
    temp_col: str = "temp_board_max",
) -> float:
    """
    Calibrates the activation energy E_a by fitting an Arrhenius law
    to the actual data (resistance vs temperature).
    """
    R_gas = 8.314  # J/(mol·K)
    default_ea = 20000.0  # 20 kJ/mol

    df_clean = df_stats.dropna(subset=[metric, temp_col]).copy()
    if "soc_voltage" in df_clean.columns:
        df_clean = df_clean[
            (df_clean["soc_voltage"] >= 20.0) & (df_clean["soc_voltage"] <= 90.0)
        ]

    if len(df_clean) < 5:
        if DEBUG:
            print(
                f"[calibrate_ea_from_logs] Insufficient points ({len(df_clean)} < 5), "
                f"using default E_a {default_ea/1000:.1f} kJ/mol"
            )
        return default_ea

    t_min = df_clean[temp_col].min()
    t_max = df_clean[temp_col].max()
    t_span = t_max - t_min

    if t_span < 5.0:
        if DEBUG:
            print(
                f"[calibrate_ea_from_logs] Low temperature spread ({t_span:.1f}°C < 5°C), "
                f"using default E_a {default_ea/1000:.1f} kJ/mol"
            )
        return default_ea

    R_values = df_clean[metric].values.astype(float)
    T_c = df_clean[temp_col].values.astype(float)
    T_k = T_c + 273.15

    mask = R_values > 0.0
    R_values = R_values[mask]
    T_k = T_k[mask]

    if len(R_values) < 5:
        if DEBUG:
            print(
                "[calibrate_ea_from_logs] After filtering, insufficient points, "
                f"using default E_a {default_ea/1000:.1f} kJ/mol"
            )
        return default_ea

    ln_r = np.log(R_values)
    inv_t = 1.0 / T_k

    coeffs = np.polyfit(inv_t, ln_r, 1)
    ea_over_r_slope = coeffs[0]
    E_a_calibrated = ea_over_r_slope * R_gas
    E_a_calibrated = np.clip(E_a_calibrated, 10000.0, 50000.0)

    if DEBUG:
        r_squared = 1.0 - (
            np.sum((ln_r - np.polyval(coeffs, inv_t)) ** 2)
            / np.sum((ln_r - np.mean(ln_r)) ** 2)
        )
        print(
            f"[calibrate_ea_from_logs] Calibrated E_a = {E_a_calibrated/1000:.1f} kJ/mol "
            f"(T range: {t_min:.1f}–{t_max:.1f}°C, n={len(R_values)}, R²={r_squared:.3f})"
        )

    return float(E_a_calibrated)


# ---------- BATTERY PACK INFERENCE ----------
def estimate_cell_resistance_mohm(v_nom: float | None) -> float:
    """
    Estimates the nominal DC resistance of a cell (mΩ) around 25°C
    for an EUC pack, based on the nominal pack voltage.
    """
    if v_nom is None:
        return 18.0
    if v_nom < 80.0:
        return 22.0
    elif v_nom < 110.0:
        return 18.0
    elif v_nom < 150.0:
        return 14.0
    else:
        return 12.0


def compute_pack_nominal_resistance(Ns_global: int | None, v_nom: float | None) -> float | None:
    """
    Returns R_pack_nominal (battery only) in ohms.
    """
    if Ns_global is None:
        return None
    r_cell_mohm = estimate_cell_resistance_mohm(v_nom)
    return Ns_global * r_cell_mohm / 1000.0


def infer_pack_config(df_stats: pd.DataFrame):
    """
    Guesses the global pack configuration of a wheel from all logs.
    Returns (Ns_global, v_nominal_approx).
    """
    d = df_stats.loc[df_stats.soc_ref_ok]
    Ns_series = d["Ns"].dropna().astype(float)

    Ns_global = None
    v_nom = None

    if not Ns_series.empty:
        Ns_global = int(round(Ns_series.median()))
        if NS_MIN <= Ns_global <= NS_MAX:
            Ns_global = min(KNOWN_SERIES, key=lambda n: abs(n - Ns_global))
            v_nom = Ns_global * NOMINAL_CELL_V

    return Ns_global, v_nom


def choose_battery_current_window(Ns: int | None):
    """
    Returns (I_min, I_max) in A for Req calculation.
    """
    if Ns is None:
        return 10.0, 80.0
    if Ns <= 16:
        return 8.0, 60.0
    elif Ns <= 24:
        return 15.0, 100.0
    elif Ns <= 32:
        return 20.0, 150.0
    else:
        return 30.0, 200.0


# ---------- Source detection ----------
def detect_source(df: pd.DataFrame) -> str:
    """
    'euc_world': EUC World header
    'wheellog': WheelLog header (date,time,...,totaldistance,...)
    """
    cols = set(df.columns)
    if "datetime" in cols and "distance_total" in cols:
        return "euc_world"
    if "date" in cols and "time" in cols and "totaldistance" in cols:
        return "wheellog"
    if "datetime" in cols:
        return "euc_world"
    return "wheellog"


def normalize_distance_total(df: pd.DataFrame, source: str):
    """
    Returns (wheel_km, source_str), always in km.
    - EUC World: distance_total in km
    - WheelLog: totaldistance in m
    """
    wheel_km = None
    src = None

    if source == "euc_world":
        if "distance_total" in df.columns and df["distance_total"].notna().any():
            wheel_km = float(df["distance_total"].max())
            src = "distance_total_km_euc"
        elif "distance" in df.columns and df["distance"].notna().any():
            wheel_km = float(df["distance"].max())
            src = "distance_log_km_euc"
    else:
        if "totaldistance" in df.columns and df["totaldistance"].notna().any():
            wheel_km = float(df["totaldistance"].max()) / 1000.0
            src = "totaldistance_m_wl"
        elif "distance" in df.columns and df["distance"].notna().any():
            wheel_km = float(df["distance"].max())
            src = "distance_log_km_wl"

    return wheel_km, src


def get_first_datetime(df: pd.DataFrame, source: str) -> str | None:
    """
    Returns a textual timestamp to sort logs.
    """
    if source == "euc_world":
        for cand in ["datetime", "gps_datetime"]:
            if cand in df.columns:
                return str(df[cand].iloc[0])
        return None
    else:
        date = df["date"].iloc[0] if "date" in df.columns else None
        time = df["time"].iloc[0] if "time" in df.columns else None
        if date is not None and time is not None:
            return f"{date} {time}"
        if date is not None:
            return str(date)
        if time is not None:
            return str(time)
        return None


# ---------- New Vidle_local(SoC) logic ----------
def _estimate_dt_series(df: pd.DataFrame) -> pd.Series | None:
    """
    Tries to build a dt series (in s) indexed like df.
    Uses 'datetime' if available, otherwise fallback ~10 Hz on index.
    """
    if "datetime" in df.columns:
        t = pd.to_datetime(df["datetime"], errors="coerce")
        if t.notna().sum() >= 2:
            t = t.sort_index()
            t_sec = (t - t.iloc[0]).dt.total_seconds().values
            if len(t_sec) >= 2:
                dt = np.diff(t_sec)
                dt = np.clip(dt, 0.01, 10.0)
                # align to index[1:]
                dt_series = pd.Series(dt, index=df.index[1:])
                return dt_series

    # fallback: frequency ~10 Hz on entire index
    if len(df) < 2:
        return None
    dt_est = 0.1
    dt = np.full(len(df) - 1, dt_est, dtype=float)
    dt_series = pd.Series(dt, index=df.index[1:])
    return dt_series


def build_vidle_profile(
    df: pd.DataFrame,
    v_col: str,
    i_col: str,
    soc_volt_col: str | None,
    idle_current_abs: float = 3.0,
    min_idle_duration_s: float = 5.0,
    max_dvdt_abs: float = 0.5,
) -> pd.Series:
    """
    Builds a V_idle_local series aligned to df.index.
    Logic:
    - detect quasi-idle segments (|I| < idle_current_abs)
    - keep only those with duration >= min_idle_duration_s
    - within these segments, keep only points with |dV/dt| <= max_dvdt_abs
    - for each retained segment, estimate V_idle_seg (high quantile of V)
      + average SoC if soc_volt_col provided
    - fit V_idle(SoC) if possible, otherwise global fallback (global quantile).
    """
    # dt per point (between i-1 and i)
    dt_series = _estimate_dt_series(df)

    if dt_series is None or dt_series.empty:
        # No notion of duration -> fall back to old global logic
        low = df[df[i_col].abs() < idle_current_abs]
        if not low.empty:
            v_idle_global = float(low[v_col].quantile(0.95))
        else:
            v_idle_global = float(df[v_col].max())
        return pd.Series(v_idle_global, index=df.index, name="V_idle_local")

    # low current mask
    low_mask = df[i_col].abs() < idle_current_abs

    if not low_mask.any():
        # no quasi-idle at all
        low = df[df[i_col].abs() < idle_current_abs]
        if not low.empty:
            v_idle_global = float(low[v_col].quantile(0.95))
        else:
            v_idle_global = float(df[v_col].max())
        return pd.Series(v_idle_global, index=df.index, name="V_idle_local")

    # detection of consecutive segments of low_mask
    idx = df.index.to_numpy()
    low_idx = idx[low_mask.values]

    # if only one or two points -> short duration, we'll check with dt below
    segments: list[tuple[int, int]] = []

    if len(low_idx) > 0:
        start = low_idx[0]
        prev = low_idx[0]
        for k in low_idx[1:]:
            if k == prev + 1:
                prev = k
            else:
                segments.append((start, prev))
                start = k
                prev = k
        segments.append((start, prev))

    # build dt aligned to df.index (index[1:])
    # for each segment (i0, i1), estimate its total duration
    idle_points = []
    soc_idle = []
    v_idle_seg_list = []

    for (i0, i1) in segments:
        seg_idx = df.index[(df.index >= i0) & (df.index <= i1)]
        if len(seg_idx) < 2:
            continue

        # duration = sum dt between points
        dt_seg = dt_series.reindex(seg_idx, fill_value=np.nan)
        # dt_series is indexed from second point, first dt of seg_idx will be NaN
        dt_seg = dt_seg.bfill()
        duration = float(dt_seg.sum())

        if duration < min_idle_duration_s:
            continue

        # dV/dt on this segment
        v_seg = df.loc[seg_idx, v_col].astype(float)

        # approx dV/dt point by point in the segment (excluding first)
        dv = v_seg.diff()
        dt_loc = dt_seg
        dvdt = dv / dt_loc
        dvdt = dvdt.replace([np.inf, -np.inf], np.nan)

        # we consider "stable" points where |dV/dt| <= max_dvdt_abs
        stable_mask = dvdt.abs() <= max_dvdt_abs
        stable_idx = seg_idx[stable_mask.fillna(False)]

        if len(stable_idx) < 3:
            # segment too agitated
            continue

        v_stable = df.loc[stable_idx, v_col].astype(float)
        v_idle_seg = float(v_stable.quantile(0.95))
        v_idle_seg_list.append(v_idle_seg)

        if soc_volt_col is not None and soc_volt_col in df.columns:
            soc_vals = df.loc[stable_idx, soc_volt_col].astype(float)
            soc_idle.append(float(soc_vals.mean()))
        else:
            soc_idle.append(None)

        idle_points.extend(list(stable_idx))

    if not v_idle_seg_list:
        # no exploitable quasi-idle plateau -> global fallback
        low = df[df[i_col].abs() < idle_current_abs]
        if not low.empty:
            v_idle_global = float(low[v_col].quantile(0.95))
        else:
            v_idle_global = float(df[v_col].max())
        return pd.Series(v_idle_global, index=df.index, name="V_idle_local")

    # If no reliable SoC, use a global Vidle
    if soc_volt_col is None or all(s is None for s in soc_idle):
        v_idle_global = float(np.mean(v_idle_seg_list))
        return pd.Series(v_idle_global, index=df.index, name="V_idle_local")

    # Build V_idle(SoC)
    soc_array = np.array([s for s in soc_idle if s is not None], dtype=float)
    v_array = np.array([v for v, s in zip(v_idle_seg_list, soc_idle) if s is not None], dtype=float)

    if len(soc_array) < 2:
        # not enough points for real interpolation
        v_idle_global = float(np.mean(v_array)) if len(v_array) > 0 else float(df[v_col].max())
        return pd.Series(v_idle_global, index=df.index, name="V_idle_local")

    # sort by SoC
    order = np.argsort(soc_array)
    soc_array = soc_array[order]
    v_array = v_array[order]

    # impose gentle monotonicity (not strictly necessary, but avoids oscillations)
    # here we can simply smooth slightly via a median filter or moving average
    if len(v_array) >= 3:
        v_smooth = v_array.copy()
        for i in range(1, len(v_array) - 1):
            v_smooth[i] = np.median(v_array[max(0, i - 1):min(len(v_array), i + 2)])
        v_array = v_smooth

    # linear interpolation function over the range [soc_min, soc_max]
    soc_min = float(soc_array.min())
    soc_max = float(soc_array.max())
    v_min = float(v_array[0])
    v_max = float(v_array[-1])

    def _vidle_from_soc(soc_val: float) -> float:
        if np.isnan(soc_val):
            # fallback to average
            return float(np.mean(v_array))
        if soc_val <= soc_min:
            # low extrapolation: bounded slope
            return v_min
        if soc_val >= soc_max:
            # high extrapolation
            return v_max
        # linear interpolation
        return float(np.interp(soc_val, soc_array, v_array))

    # apply to df
    if soc_volt_col in df.columns:
        soc_series = df[soc_volt_col].astype(float)
        v_idle_local = soc_series.apply(_vidle_from_soc)
    else:
        # safety, should not arrive here normally
        v_idle_local = pd.Series(float(np.mean(v_array)), index=df.index)

    v_idle_local.name = "V_idle_local"
    return v_idle_local


# ---------- Calculate stats per file (R_eq + SoH) ----------
def compute_req_stats_for_file(
    csv_path,
    speed_thr: float = 20.0,
    cur_thr: float = 5.0,
    mosfet_params: MOSFETParams | None = None,
    ea_j_per_mol: float | None = None,
):
    """
    Calculates R_eq and SoH metrics for a CSV file.
    """
    df = pd.read_csv(csv_path, on_bad_lines="skip",low_memory=False)
    source = detect_source(df)

    v_col = "voltage"
    i_col = "current"
    s_col = "speed"
    temp_board_col = "system_temp" if "system_temp" in df.columns else "temp"

    # phase current: several possible names
    if "current_phase" in df.columns:
        i_phase_col = "current_phase"
    elif "phase_current" in df.columns:
        i_phase_col = "phase_current"
    else:
        i_phase_col = None

    if "temp_motor" in df.columns:
        temp_motor_col = "temp_motor"
    elif "temp2" in df.columns:
        temp_motor_col = "temp2"
    else:
        temp_motor_col = None

    battery_col_candidates = ["battery_level", "battery", "soc"]
    soc_col = next((c for c in battery_col_candidates if c in df.columns), None)

    Ns = None
    soc_ref_ok = False
    soc_ref_v_idle = None

    if soc_col is not None:
        v_idle_max = float(df[v_col].max())
        Ns_est = int(round(v_idle_max / 4.2))
        if NS_MIN <= Ns_est <= NS_MAX:
            Ns = Ns_est

    if Ns is not None:
        full_mask = (df[soc_col] >= 98) & (df[i_col].abs() < 2)
        if full_mask.any():
            v_full = float(df.loc[full_mask, v_col].max())
            v_cell_full = v_full / Ns
            if 4.05 <= v_cell_full <= 4.25:
                soc_ref_ok = True
                soc_ref_v_idle = v_full

    for c in (v_col, i_col, s_col):
        if c not in df.columns:
            raise ValueError(f"{csv_path}: missing column {c}")

    wheel_km, km_source = normalize_distance_total(df, source)

    # SoC based on voltage (if possible)
    soc_volt_col = None
    if Ns is not None and soc_ref_ok and soc_ref_v_idle is not None:
        v_cell_full = soc_ref_v_idle / Ns
        v_cell_min = 3.0
        span = v_cell_full - v_cell_min
        if span > 0.5:
            v_cell_idle = df[v_col] / Ns
            soc_volt = (v_cell_idle - v_cell_min) / span
            soc_volt = soc_volt.clip(0.0, 1.0) * 100.0
            df["soc_voltage"] = soc_volt
            soc_volt_col = "soc_voltage"

    # Build Vidle_local (new feature)
    v_idle_local = build_vidle_profile(
        df=df,
        v_col=v_col,
        i_col=i_col,
        soc_volt_col=soc_volt_col,
        idle_current_abs=3.0,
        min_idle_duration_s=5.0,
        max_dvdt_abs=0.5,
    )
    df["V_idle_local"] = v_idle_local

    # For compatibility with the rest of the code (global stats), we keep a "global" v_idle
    # as average of local values on quasi-idle points,
    # and fallback to the global average of V_idle_local if necessary.
    low_for_global = df[df[i_col].abs() < 3]
    if not low_for_global.empty:
        v_idle_global = float(df.loc[low_for_global.index, "V_idle_local"].mean())
    else:
        v_idle_global = float(df["V_idle_local"].mean())

    v_idle = v_idle_global  # used only for export, no longer for Req calculation

    # Current window for Req
    I_min, I_max = choose_battery_current_window(Ns)
    I_min = max(I_min, cur_thr)

    d = df[
        (df[s_col] > speed_thr)
        & (df[i_col].abs() >= I_min)
        & (df[i_col].abs() <= I_max)
    ].copy()

    if len(d) < 50:
        I_min *= 0.7
        I_max *= 1.3
        d = df[
            (df[s_col] > speed_thr)
            & (df[i_col].abs() >= I_min)
            & (df[i_col].abs() <= I_max)
        ].copy()

    if soc_volt_col is not None:
        d = d[(d[soc_volt_col] > 20.0) & (d[soc_volt_col] < 90.0)]
    elif soc_col is not None:
        d = d[(d[soc_col] > 20.0) & (d[soc_col] < 90.0)]

    if d.empty:
        return None

    # New sag calculation: V_idle_local - V_measured
    if "V_idle_local" in d.columns:
        d["sag"] = d["V_idle_local"] - d[v_col]
    else:
        # theoretical fallback (should not happen)
        d["sag"] = v_idle - d[v_col]

    d["Req"] = d["sag"] / d[i_col].abs()

    Req_mean = float(d["Req"].mean())
    Req_median = float(d["Req"].median())
    Req_95p = float(d["Req"].quantile(0.95))
    sag_95p = float(d["sag"].quantile(0.95))
    sag_max = float(d["sag"].max())
    v_min_strong = float(d[v_col].min())
    i_max = float(d[i_col].abs().max())
    i_95p = float(d[i_col].abs().quantile(0.95))

    # ---- Phase current: intensity and I²dt dose (MOSFET stress) ----
    I_phase2_int = None
    i_phase_max = None
    i_phase_95p = None

    if i_phase_col is not None and i_phase_col in d.columns and not d[i_phase_col].isna().all():
        i_phase = d[i_phase_col].astype(float).values
        i_phase_abs = np.abs(i_phase)

        # build time in s if possible
        t = None
        if "datetime" in df.columns:
            t_all = pd.to_datetime(df["datetime"], errors="coerce")
            t = t_all.loc[d.index]
        else:
            # fallback: artificial time based on index, ~10 Hz
            n = len(d)
            if n > 1:
                dt_est = 0.1
                t = pd.to_datetime(
                    pd.Series(np.arange(n) * dt_est, index=d.index),
                    unit="s",
                    origin="unix",
                )

        if t is not None and t.notna().sum() >= 2:
            t = t.sort_index()
            t_sec = (t - t.iloc[0]).dt.total_seconds().values
            if len(t_sec) >= 2:
                dt = np.diff(t_sec)
                dt = np.clip(dt, 0.01, 1.0)
                I2 = i_phase_abs**2
                I_phase2_int = float(np.sum(0.5 * (I2[1:] + I2[:-1]) * dt))

        # fallback if no exploitable time
        if I_phase2_int is None:
            dt = 0.1
            I_phase2_int = float(np.sum(i_phase_abs**2) * dt)

        i_phase_max = float(i_phase_abs.max())
        i_phase_95p = float(np.quantile(i_phase_abs, 0.95))

    temp_board_max = (
        float(df[temp_board_col].max()) if temp_board_col in df.columns else None
    )

    if temp_motor_col is not None and temp_motor_col in df.columns:
        temp_motor_max = float(df[temp_motor_col].max())
    else:
        temp_motor_max = None

    first_dt = get_first_datetime(df, source)

    stats = {
        "file": Path(csv_path).name,
        "source": source,
        "datetime_first": first_dt,
        "wheel_km": wheel_km,
        "wheel_km_source": km_source,
        "v_idle": v_idle,  # global "average", for compatibility / info
        "Ns": Ns,
        "soc_ref_ok": bool(soc_ref_ok),
        "soc_ref_v_full": float(soc_ref_v_idle) if soc_ref_v_idle is not None else None,
        "n_points": int(len(d)),
        "Req_mean": Req_mean,
        "Req_median": Req_median,
        "Req_95p": Req_95p,
        "sag_95p": sag_95p,
        "sag_max": sag_max,
        "v_min_strong": v_min_strong,
        "i_max": i_max,
        "i_95p": i_95p,
        "temp_board_max": temp_board_max,
        "temp_motor_max": temp_motor_max,
        # new phase current metrics
        "I_phase2_int": I_phase2_int,
        "i_phase_max": i_phase_max,
        "i_phase_95p": i_phase_95p,
    }

    if ea_j_per_mol is None:
        ea_j_per_mol = 20000.0

    R_eq_25c = normalize_r_batt_to_25c(
        r_batt_measured=Req_median,
        temp_measured_c=temp_board_max,
        ea_j_per_mol=ea_j_per_mol,
    )
    stats["Req_median_25C"] = float(R_eq_25c if R_eq_25c is not None else Req_median)

    if mosfet_params is not None and temp_board_max is not None:
        r_mosfet_hot = mosfet_params.r_mosfet_at_temp(temp_board_max)
        r_batt_median = Req_median - r_mosfet_hot
        if r_batt_median < 0:
            r_batt_median = 0.0

        r_batt_25c = normalize_r_batt_to_25c(
            r_batt_measured=r_batt_median,
            temp_measured_c=temp_board_max,
            ea_j_per_mol=ea_j_per_mol,
        )

        stats["R_mosfet_hot"] = float(r_mosfet_hot)
        stats["R_batt_median"] = float(r_batt_median)
        stats["R_batt_median_25C"] = float(
            r_batt_25c if r_batt_25c is not None else r_batt_median
        )

    return stats


# ---------- Folder aggregation + automatic R_eq band ----------
def analyze_folder_for_req(
    folder_path,
    optimal_frac: float = 0.3,
    mosfet_params: MOSFETParams | None = None,
    ea_j_per_mol: float | None = None,
):
    """
    Analyzes all CSVs in a folder (one wheel).
    """
    folder = Path(folder_path)
    rows = []

    # First pass: E_a if None
    if ea_j_per_mol is None:
        for csv_path in sorted(folder.glob("*.csv")):
            try:
                stats = compute_req_stats_for_file(
                    csv_path,
                    mosfet_params=mosfet_params,
                    ea_j_per_mol=None,
                )
            except Exception as e:
                if DEBUG:
                    print(f"An error occurred: {e}")
                continue
            if stats is not None:
                rows.append(stats)

        if not rows:
            raise RuntimeError("No exploitable log for R_eq in this folder.")

        df_stats_temp = pd.DataFrame(rows)
        ea_j_per_mol = calibrate_ea_from_logs(
            df_stats_temp,
            metric="Req_median",
            temp_col="temp_board_max",
        )

    rows_final = []
    for csv_path in sorted(folder.glob("*.csv")):
        try:
            stats = compute_req_stats_for_file(
                csv_path,
                mosfet_params=mosfet_params,
                ea_j_per_mol=ea_j_per_mol,
            )
        except Exception as e:
            if DEBUG:
                print(f"An error occurred: {e}")
            continue
        if stats is not None:
            rows_final.append(stats)

    if not rows_final:
        raise RuntimeError("No exploitable log for R_eq in this folder (pass 2).")

    df_stats = pd.DataFrame(rows_final)

    if df_stats["datetime_first"].notna().any():
        df_stats["datetime_first_parsed"] = pd.to_datetime(
            df_stats["datetime_first"], errors="coerce", utc=True
        ).dt.tz_localize(None)
        df_stats = (
            df_stats.sort_values("datetime_first_parsed")
            .drop(columns="datetime_first_parsed")
        )
    else:
        df_stats = df_stats.sort_values("file")

    if "R_batt_median_25C" in df_stats.columns and df_stats["R_batt_median_25C"].notna().any():
        df_stats["R_batt_median_eff"] = df_stats["R_batt_median_25C"]
    elif "R_batt_median" in df_stats.columns and df_stats["R_batt_median"].notna().any():
        df_stats["R_batt_median_eff"] = df_stats["R_batt_median"]

    df_sorted = df_stats.sort_values("Req_median_25C")
    n_opt = max(1, int(len(df_sorted) * optimal_frac))
    df_opt = df_sorted.head(n_opt)

    req_band_low = float(df_opt["Req_median_25C"].quantile(0.10))
    req_band_high = float(df_opt["Req_median_25C"].quantile(0.90))

    df_stats["Req_band_low"] = req_band_low
    df_stats["Req_band_high"] = req_band_high

    if "R_batt_median_eff" in df_stats.columns and df_stats["R_batt_median_eff"].notna().any():
        df_sorted_b = df_stats.sort_values("R_batt_median_eff")
        n_opt_b = max(1, int(len(df_sorted_b) * optimal_frac))
        df_opt_b = df_sorted_b.head(n_opt_b)

        r_batt_low = float(df_opt_b["R_batt_median_eff"].quantile(0.10))
        r_batt_high = float(df_opt_b["R_batt_median_eff"].quantile(0.90))

        df_stats["R_batt_band_low"] = r_batt_low
        df_stats["R_batt_band_high"] = r_batt_high

    Ns_global, v_nom = infer_pack_config(df_stats)

    df_stats["Ns_global"] = Ns_global
    df_stats["v_nominal"] = v_nom

    r_pack_nom = compute_pack_nominal_resistance(Ns_global, v_nom)
    df_stats["R_pack_nominal"] = r_pack_nom

    # Arrhenius info for reuse (PDF / UI)
    df_stats["arrhenius_ea_j_per_mol"] = float(ea_j_per_mol)
    df_stats["arrhenius_ea_kj_per_mol"] = float(ea_j_per_mol) / 1000.0
    df_stats["arrhenius_auto_calibrated"] = ea_j_per_mol is None or True  # auto if pass 1

    if DEBUG:
        print(df_stats)

    return df_stats


def build_summary_dict(df_stats: pd.DataFrame, wheel_name: str):
    """
    Global summary to feed a UI (Kivy, JSON, etc.).
    """
    summary = {
        "wheel_name": wheel_name,
        "req_band": {
            "low": float(df_stats["Req_band_low"].iloc[0]),
            "high": float(df_stats["Req_band_high"].iloc[0]),
        },
        "global": {
            "km_min": float(df_stats["wheel_km"].min()),
            "km_max": float(df_stats["wheel_km"].max()),
            "Req_median_min": float(df_stats["Req_median"].min()),
            "Req_median_max": float(df_stats["Req_median"].max()),
        },
        "logs": df_stats.to_dict(orient="records"),
    }

    summary["soc_voltage_available"] = bool(
        df_stats.get("soc_ref_ok", pd.Series([False])).any()
    )

    Ns_global = df_stats["Ns_global"].iloc[0]
    v_nom = df_stats["v_nominal"].iloc[0]
    r_pack_nom = df_stats["R_pack_nominal"].iloc[0]

    summary["pack"] = {
        "Ns": int(Ns_global) if Ns_global is not None else None,
        "v_nominal": float(v_nom) if v_nom is not None else None,
        "R_pack_nominal": float(r_pack_nom) if r_pack_nom is not None else None,
    }

    if "R_batt_median_eff" in df_stats.columns:
        summary["batt_req_band"] = {
            "low": float(df_stats.get("R_batt_band_low", df_stats["R_batt_median_eff"]).min()),
            "high": float(df_stats.get("R_batt_band_high", df_stats["R_batt_median_eff"]).max()),
        }
        summary["global"]["R_batt_median_min"] = float(df_stats["R_batt_median_eff"].min())
        summary["global"]["R_batt_median_max"] = float(df_stats["R_batt_median_eff"].max())

    if "R_mosfet_hot" in df_stats.columns:
        summary["global"]["R_mosfet_hot_min"] = float(df_stats["R_mosfet_hot"].min())
        summary["global"]["R_mosfet_hot_max"] = float(df_stats["R_mosfet_hot"].max())

    if "arrhenius_ea_kj_per_mol" in df_stats.columns:
        summary["arrhenius"] = {
            "E_a_kJ_per_mol": float(df_stats["arrhenius_ea_kj_per_mol"].iloc[0]),
            "auto_calibrated": bool(df_stats["arrhenius_auto_calibrated"].iloc[0]),
        }

    return summary


# ---------- LINEAR DRIFT DETECTION ----------
def detect_trend_linear(
    df_stats: pd.DataFrame,
    metric: str = "Req_median",
    km_min_span: float = 1000.0
):
    """
    Detects a linear drift of the metric as a function of wheel mileage.
    """
    if metric not in df_stats.columns:
        return None, None, False

    df = df_stats.dropna(subset=["wheel_km", metric]).sort_values("wheel_km")

    if len(df) < 5:
        return None, None, False

    x = df["wheel_km"].values
    y = df[metric].values

    span_km = x.max() - x.min()
    if span_km < km_min_span:
        return None, None, False

    slope, intercept, r_value, p_value, std_err = linregress(x, y)
    is_significant = (slope > 0) and (p_value is not None and p_value < 0.05)

    return float(slope), float(p_value), bool(is_significant)


def detect_slope_inflexions(
    df_stats: pd.DataFrame,
    metric: str,
    thresholds: dict | None,
    high_is_bad: bool = True,
    window_km: float = 1500.0,
    min_km_span: float = 3000.0,
    slope_factor: float = 1.5,
    min_fraction_above_limit: float = 0.6,
):
    """
    Classifies points of a metric into "slow regime" (green) or "sustained inflexion" (red),
    based on:
    - the Gaussian danger threshold (thresholds[metric]["limit"])
    - the local slope (sliding linear regression)
    """
    if metric not in df_stats.columns:
        return [], [], pd.Series(dtype=float)

    df = df_stats.dropna(subset=["wheel_km", metric]).sort_values("wheel_km")

    if len(df) < 10:
        return df.index.to_list(), [], pd.Series(index=df_stats.index, dtype=float)

    x = df["wheel_km"].values
    y = df[metric].values

    span_km = x.max() - x.min()
    if span_km < min_km_span:
        return df.index.to_list(), [], pd.Series(index=df_stats.index, dtype=float)

    danger_limit = None
    if thresholds is not None and metric in thresholds:
        danger_limit = float(thresholds[metric]["limit"])

    mu_ref = float(np.mean(y))
    sigma_ref = float(np.std(y, ddof=1)) if len(y) > 1 else 0.0

    if danger_limit is None:
        danger_limit = mu_ref + 1.25 * sigma_ref if high_is_bad else mu_ref - 1.25 * sigma_ref

    km_cut = x.min() + span_km / 3.0
    base_mask = x <= km_cut

    if base_mask.sum() >= 5:
        xb = x[base_mask]
        yb = y[base_mask]
        z = np.polyfit(xb, yb, 1)
        slope_base = float(z[0])
    else:
        z = np.polyfit(x, y, 1)
        slope_base = float(z[0])

    slopes_local = np.full_like(y, np.nan, dtype=float)
    frac_above_limit = np.zeros_like(y, dtype=float)

    half_w = window_km / 2.0

    for i, km_i in enumerate(x):
        km_min = km_i - half_w
        km_max = km_i + half_w
        mask = (x >= km_min) & (x <= km_max)
        if mask.sum() < 5:
            continue
        xx = x[mask]
        yy = y[mask]
        zz = np.polyfit(xx, yy, 1)
        slopes_local[i] = float(zz[0])
        if danger_limit is not None:
            frac_above_limit[i] = float(np.mean(yy > danger_limit))

    slow_idx = []
    inflex_idx = []

    slope_threshold = slope_base * slope_factor

    for i, (val, s_loc, frac_lim) in enumerate(zip(y, slopes_local, frac_above_limit)):
        idx = df.index[i]
        if np.isnan(s_loc):
            if val > danger_limit:
                inflex_idx.append(idx)
            else:
                slow_idx.append(idx)
            continue

        if high_is_bad and (s_loc > slope_threshold) and (frac_lim >= min_fraction_above_limit):
            inflex_idx.append(idx)
            if DEBUG:
                print("HIGH")
        elif not high_is_bad and (s_loc <= slope_threshold) and (frac_lim < min_fraction_above_limit):
            inflex_idx.append(idx)
            if DEBUG:
                print("LOW")
        else:
            slow_idx.append(idx)

    slopes_series = pd.Series(slopes_local, index=df.index)

    return slow_idx, inflex_idx, slopes_series


def cusum_detection(
    df_stats: pd.DataFrame,
    metric: str = "Req_median",
    ref_km_max: float | None = None,
    test_km_min: float | None = None,
    k_sigma: float = 1.0,
    h_sigma: float = 5.0,
    cooldown_km: float = 500.0,
    relative_jump_min: float = 0.05,
    h_sigma_cooldown: float = 6.0,
):
    """
    Unilateral CUSUM to detect an upward shift of the metric,
    with regime management and refractory period.
    """
    if metric not in df_stats.columns:
        return [], None, None

    df = df_stats.dropna(subset=["wheel_km", metric]).sort_values("wheel_km")

    if len(df) < 5:
        return [], None, None

    if ref_km_max is None:
        ref_df = df
    else:
        ref_df = df[df["wheel_km"] <= ref_km_max]

    if len(ref_df) < 3:
        return [], None, None

    y_ref = np.sort(ref_df[metric].values.astype(float))
    n_ref = max(3, int(0.5 * len(y_ref)))
    y_ref = y_ref[:n_ref]

    mu_ref = float(np.mean(y_ref))
    sigma_ref = float(np.std(y_ref, ddof=1)) if len(y_ref) > 1 else 0.0

    if sigma_ref == 0.0:
        return [], mu_ref, sigma_ref

    if test_km_min is None:
        test_df = df
    else:
        test_df = df[df["wheel_km"] >= test_km_min]

    if len(test_df) < 3:
        return [], mu_ref, sigma_ref

    y = test_df[metric].values.astype(float)
    km = test_df["wheel_km"].values
    global_idx = test_df.index.to_list()

    k = k_sigma * sigma_ref
    h_normal = h_sigma * sigma_ref
    h_cooldown = h_sigma_cooldown * sigma_ref

    S = 0.0
    alarm_global_idx: list[int] = []
    in_cooldown = False
    cooldown_end_km = None
    regime_mu = mu_ref

    for i, (val, km_i) in enumerate(zip(y, km)):
        if in_cooldown and cooldown_end_km is not None and km_i > cooldown_end_km:
            in_cooldown = False
            cooldown_end_km = None
            S = 0.0

        h_current = h_cooldown if in_cooldown else h_normal

        S = max(0.0, S + (val - regime_mu - k))

        triggered = S >= h_current

        if triggered and in_cooldown and regime_mu > 0:
            rel_jump = (val - regime_mu) / regime_mu
            if rel_jump < relative_jump_min:
                triggered = False
                S = 0.5 * S

        if triggered:
            idx_global = global_idx[i]
            alarm_global_idx.append(idx_global)

            j0 = max(0, i - 4)
            regime_window = y[j0: i + 1]
            regime_mu = float(np.mean(regime_window))

            in_cooldown = True
            cooldown_end_km = km_i + cooldown_km
            S = 0.0

    return alarm_global_idx, mu_ref, sigma_ref


# ---------- Generic Gaussian ----------
def compute_req_band_gauss(
    df_stats: pd.DataFrame,
    optimal_frac: float = 0.5,
    n_sigma_band: float = 1.0,
    metric: str = "Req_median",
):
    """
    Calculates "healthy" band and danger threshold for a metric.
    """
    if metric not in df_stats.columns:
        raise ValueError(f"Unknown metric: {metric}")

    df_sorted = df_stats.sort_values(metric)
    n_opt = max(3, int(len(df_sorted) * optimal_frac))
    df_opt = df_sorted.head(n_opt)

    vals = df_opt[metric].dropna().values
    vals = np.asarray(vals, dtype=float)
    vals = vals[~np.isnan(vals)]

    if len(vals) < 3:
        raise ValueError(f"{metric}: insufficient data for Gaussian")

    mu = float(np.mean(vals))
    sigma = float(np.std(vals, ddof=1))

    band_low = mu - n_sigma_band * sigma
    band_high = mu + n_sigma_band * sigma

    return mu, sigma, band_low, band_high


# ---------- Gaussian alarms ----------
def detect_alarms_gauss(
    df_stats: pd.DataFrame,
    optimal_frac: float = 0.5,
    n_sigma: float = 2.0,
    use_batt_metric: bool = False,
):
    """
    Detects potentially "dangerous" logs.
    """
    trend_metric = "R_batt_median_25C" if "R_batt_median_25C" in df_stats.columns else "Req_median"

    metrics = [
        "Req_median",
        "Req_95p",
        "sag_95p",
        "sag_max",
        "temp_board_max",
        "temp_motor_max",
        "v_min_strong",
    ]

    if use_batt_metric and "R_batt_median" in df_stats.columns:
        metrics.append("R_batt_median")
    if "R_mosfet_hot" in df_stats.columns:
        metrics.append("R_mosfet_hot")

    # Add phase current metrics
    if "I_phase2_int" in df_stats.columns:
        metrics.append("I_phase2_int")
    if "i_phase_max" in df_stats.columns:
        metrics.append("i_phase_max")
    if "i_phase_95p" in df_stats.columns:
        metrics.append("i_phase_95p")

    direction = {
        "Req_median": "higher_is_bad",
        "Req_95p": "higher_is_bad",
        "sag_95p": "higher_is_bad",
        "sag_max": "higher_is_bad",
        "temp_board_max": "higher_is_bad",
        "temp_motor_max": "higher_is_bad",
        "R_batt_median": "higher_is_bad",
        "R_mosfet_hot": "higher_is_bad",
        "v_min_strong": "lower_is_bad",
        "I_phase2_int": "higher_is_bad",
        "i_phase_max": "higher_is_bad",
        "i_phase_95p": "higher_is_bad",
    }

    df_sorted = df_stats.sort_values("Req_median")
    n_opt = max(3, int(len(df_sorted) * optimal_frac))
    df_opt = df_sorted.head(n_opt)

    thresholds = {}

    for m in metrics:
        if m not in df_stats.columns or df_opt[m].isna().all():
            continue

        vals = df_opt[m].dropna().values
        vals = np.asarray(vals, dtype=float)
        vals = vals[~np.isnan(vals)]

        if len(vals) < 3:
            continue

        mu = float(np.mean(vals))
        sigma = float(np.std(vals, ddof=1))

        if direction.get(m, "higher_is_bad") == "higher_is_bad":
            limit = mu + n_sigma * sigma
        else:
            limit = mu - n_sigma * sigma

        thresholds[m] = {
            "mean": mu,
            "std": sigma,
            "limit": limit,
            "direction": direction.get(m, "higher_is_bad"),
        }

    alarm_rows = []

    for _, row in df_stats.iterrows():
        reasons = []
        for m, info in thresholds.items():
            val = row.get(m, None)
            if val is None or pd.isna(val):
                continue

            if info["direction"] == "higher_is_bad":
                bad = val > info["limit"]
            else:
                bad = val < info["limit"]

            if bad:
                reasons.append(
                    f"{m} ({info['direction']}) "
                    f"limit={info['limit']:.3f}, val={val:.3f}, "
                    f"µ={info['mean']:.3f}, σ={info['std']:.3f}"
                )

        if reasons:
            alarm_rows.append({
                "file": row["file"],
                "wheel_km": row["wheel_km"],
                "datetime_first": row["datetime_first"],
                "reasons": "; ".join(reasons),
            })

    df_alarms = pd.DataFrame(alarm_rows)

    extra_rows = []

    r_pack_nom = df_stats.get("R_pack_nominal", pd.Series([None])).iloc[0]
    if r_pack_nom is not None:
        abs_limit = r_pack_nom * ABS_REQ_FACTOR
    else:
        abs_limit = ABS_REQ_LIMIT

    for _, row in df_stats.iterrows():
        req = row.get("Req_median")
        km = row.get("wheel_km")
        if req is None or km is None or pd.isna(req) or pd.isna(km):
            continue
        
        if req > abs_limit and km >= ABS_KM_LIMIT:
            try:
                x=f"(> {abs_limit:.3f} Ω ≈ {abs_limit/r_pack_nom:.1f}×R_pack_nom={r_pack_nom:.3f} Ω) "
            except:
                r_pack_nom=1.0
            extra_rows.append({
                "file": row["file"],
                "wheel_km": km,
                "datetime_first": row.get("datetime_first"),
                "reasons": (
                    f"Absolute high Req_median: {req:.3f} Ω "
                    f"(> {abs_limit:.3f} Ω ≈ {abs_limit/r_pack_nom:.1f}×R_pack_nom={r_pack_nom:.3f} Ω) "
                    f"at {km:.0f} km"
                ),
            })

    km_max = float(df_stats["wheel_km"].max())
    ref_km_max = km_max * 0.3
    test_km_min = ref_km_max

    for m in CUSUM_METRICS:
        if m in df_stats.columns:
            alarm_idx, mu_ref, sigma_ref = cusum_detection(
                df_stats, metric=m, ref_km_max=ref_km_max, test_km_min=test_km_min
            )

            if alarm_idx:
                df_nonan = df_stats.dropna(subset=["wheel_km", trend_metric]).sort_values("wheel_km")
                first_alarm = df_nonan.iloc[alarm_idx[0]]

                extra_rows.append({
                    "file": first_alarm["file"],
                    "wheel_km": first_alarm["wheel_km"],
                    "datetime_first": first_alarm["datetime_first"],
                    "reasons": (
                        f"Regime change detected on {trend_metric} (CUSUM): "
                        f"µ_ref={mu_ref:.4f}, σ_ref={sigma_ref:.4f}, "
                        f"triggering log ≈ {first_alarm['wheel_km']:.0f} km"
                    ),
                })

    for m in TREND_METRICS:
        if m in df_stats.columns:
            slope, p_val, sig = detect_trend_linear(df_stats, metric=m)
            if sig:
                slope_per_1000km = slope * 1000.0
                df_trend_alarm = pd.DataFrame([{
                    "file": "TREND_ANALYSIS",
                    "wheel_km": df_stats["wheel_km"].max(),
                    "datetime_first": df_stats["datetime_first"].iloc[-1],
                    "reasons": (
                        f"Upward trend on {trend_metric}: "
                        f"+{slope_per_1000km:.4f} Ω / 1000 km (p={p_val:.3f})"
                    ),
                }])

                if df_alarms.empty:
                    df_alarms = df_trend_alarm
                else:
                    df_alarms = pd.concat([df_alarms, df_trend_alarm], ignore_index=True)

    if extra_rows:
        df_abs = pd.DataFrame(extra_rows)
        if df_alarms.empty:
            df_alarms = df_abs
        else:
            df_alarms = pd.concat([df_alarms, df_abs], ignore_index=True)

    return df_alarms, thresholds


# ---------- Multi-metric SoH overview ----------
def plot_soh_overview_all(
    df_stats: pd.DataFrame,
    metrics=None,
    labels=None,
    optimal_frac: float = 0.5,
    n_sigma_band: float = 1.0,
    n_sigma_danger: float = 2.0,
    title_prefix: str = "",
    include_batt_metric: bool = True,
):
    """
    Figure with one subplot per SoH metric.
    """
    default_metrics = [
        "Req_median",
        "Req_95p",
        "sag_95p",
        "sag_max",
        "v_min_strong",
        "i_95p",
        "i_max",
        "temp_board_max",
        "temp_motor_max",
    ]

    if include_batt_metric and "R_batt_median" in df_stats.columns:
        default_metrics.insert(1, "R_batt_median")
    if "R_mosfet_hot" in df_stats.columns:
        default_metrics.insert(1, "R_mosfet_hot")

    if metrics is None:
        metrics = default_metrics

    if labels is None:
        labels = {
            "Req_median": "R_eq median (Ω)",
            "Req_95p": "R_eq 95th (Ω)",
            "R_batt_median": "R_batt median (Ω)",
            "R_mosfet_hot": "R_MOSFET hot (Ω)",
            "sag_95p": "Sag 95th (V)",
            "sag_max": "Sag max (V)",
            "v_min_strong": "Min voltage strong load (V)",
            "i_95p": "Current 95th (A)",
            "i_max": "Max current (A)",
            "temp_board_max": "Board temp max (°C)",
            "temp_motor_max": "Motor temp max (°C)",
        }

    direction = {
        "v_min_strong": "lower_is_bad",
        "R_batt_median": "higher_is_bad",
        "R_mosfet_hot": "higher_is_bad",
    }

    df_sorted = df_stats.sort_values("Req_median")
    n_opt = max(3, int(len(df_sorted) * optimal_frac))
    df_opt = df_sorted.head(n_opt)

    available = [m for m in metrics if m in df_stats.columns]

    if not available:
        raise ValueError("None of the requested metrics are present in df_stats.")

    fig, axes = plt.subplots(
        len(available),
        1,
        sharex=True,
        figsize=(10, 2.8 * len(available)),
        constrained_layout=True,
    )

    if len(available) == 1:
        axes = [axes]

    df_plot = df_stats.sort_values(["wheel_km", "datetime_first"])
    x = df_plot["wheel_km"].values

    for ax, col in zip(axes, available):
        vals_ref = df_opt[col].dropna().values
        vals_ref = np.asarray(vals_ref, dtype=float)
        vals_ref = vals_ref[~np.isnan(vals_ref)]

        if len(vals_ref) < 3:
            ax.text(
                0.5,
                0.5,
                f"{col}: insufficient data",
                transform=ax.transAxes,
                ha="center",
                va="center",
            )
            ax.set_ylabel(labels.get(col, col))
            ax.grid(True, alpha=0.3)
            continue

        mu = float(np.mean(vals_ref))
        sigma = float(np.std(vals_ref, ddof=1))

        band_low = mu - n_sigma_band * sigma
        band_high = mu + n_sigma_band * sigma

        if direction.get(col, "higher_is_bad") == "higher_is_bad":
            danger = mu + n_sigma_danger * sigma
            warn_low, warn_high = band_high, danger
        else:
            danger = mu - n_sigma_danger * sigma
            warn_low, warn_high = danger, band_low

        y = df_plot[col].values
        ax.plot(x, y, "o-", label=col)

        warn_low, warn_high = sorted((warn_low, warn_high))

        ax.axhspan(
            warn_low,
            warn_high,
            color="orange",
            alpha=0.18,
            zorder=1,
            label="warning",
        )

        ax.axhspan(
            band_low,
            band_high,
            color="green",
            alpha=0.12,
            zorder=0,
            label=f"{n_sigma_band:.1f}σ",
        )

        ax.axhline(
            danger,
            color="red",
            linestyle="--",
            zorder=3,
            label=f"danger {danger:.2f}",
        )

        ax.set_ylabel(labels.get(col, col))
        ax.grid(True, alpha=0.3)
        ax.legend(loc="best", fontsize="x-small")

    axes[-1].set_xlabel("Wheel mileage (km)")

    if title_prefix:
        fig.suptitle(f"{title_prefix} - SoH", y=0.98)
        fig.tight_layout(rect=(0, 0, 1, 0.96))
    else:
        fig.tight_layout()

    return fig


# ---------- 1 metric graph ----------
def plot_metric_gauss(
    df_stats: pd.DataFrame,
    metric: str,
    label: str | None = None,
    ylabel: str | None = None,
    optimal_frac: float = 0.5,
    n_sigma_band: float = 1.0,
    n_sigma_danger: float = 2.0,
    title_prefix: str = "",
):
    """
    1 metric graph vs km with danger zones.
    """
    direction = {
        "v_min_strong": "lower_is_bad",
        "R_batt_median": "higher_is_bad",
        "R_mosfet_hot": "higher_is_bad",
    }

    df_sorted = df_stats.sort_values("Req_median")
    n_opt = max(3, int(len(df_sorted) * optimal_frac))
    df_opt = df_sorted.head(n_opt)

    vals_ref = df_opt[metric].dropna().values
    vals_ref = np.asarray(vals_ref, dtype=float)
    vals_ref = vals_ref[~np.isnan(vals_ref)]

    if len(vals_ref) < 3:
        raise ValueError(f"{metric}: insufficient data")

    mu = float(np.mean(vals_ref))
    sigma = float(np.std(vals_ref, ddof=1))

    band_low = mu - n_sigma_band * sigma
    band_high = mu + n_sigma_band * sigma

    if direction.get(metric, "higher_is_bad") == "higher_is_bad":
        danger = mu + n_sigma_danger * sigma
        band_warn_low = band_high
        band_warn_high = danger
    else:
        danger = mu - n_sigma_danger * sigma
        band_warn_low = danger
        band_warn_high = band_low

    fig, ax = plt.subplots()

    df_plot = df_stats.sort_values(["wheel_km", "datetime_first"])
    x = df_plot["wheel_km"].values
    y = df_plot[metric].values

    ax.plot(x, y, "o-", label=metric if label is None else label)

    ax.set_xlabel("Wheel mileage (km)")
    ax.set_ylabel(Y_LABELS.get(metric))

    ax.axhspan(
        band_low,
        band_high,
        color="green",
        alpha=0.1,
        label=f"±{n_sigma_band}σ",
    )

    ax.axhspan(
        band_warn_low,
        band_warn_high,
        color="orange",
        alpha=0.1,
        label="warning",
    )

    ax.axhline(
        danger,
        color="red",
        linestyle="--",
        label=f"danger threshold ≈ {danger:.2f}",
    )

    if title_prefix:
        ax.set_title(f"{title_prefix} - {label or metric}")
    else:
        ax.set_title(label or metric)

    ax.grid(True, alpha=0.3)
    ax.legend(loc="best", fontsize="x-small")

    fig.tight_layout()

    return fig


def plot_metric_inflexions(
    df_stats: pd.DataFrame,
    metric: str = "R_mosfet_hot",
    label: str | None = None,
    title_prefix: str = "",
    thresholds: dict | None = None,
    high_is_bad: bool = True,
    **kwargs_detect,
):
    """
    km vs metric graph with:
    - green points = slow regime
    - red points = sustained inflexion zone
    """
    slow_idx, inflex_idx, _ = detect_slope_inflexions(
        df_stats, metric=metric, thresholds=thresholds, high_is_bad=high_is_bad, **kwargs_detect
    )

    df = df_stats.dropna(subset=["wheel_km", metric]).sort_values("wheel_km")
    x = df["wheel_km"].values
    y = df[metric].values

    fig, ax = plt.subplots()

    danger_limit = None
    if thresholds is not None and metric in thresholds:
        danger_limit = float(thresholds[metric]["limit"])

    mu_ref = float(np.mean(y))
    sigma_ref = float(np.std(y, ddof=1)) if len(y) > 1 else 0.0

    if danger_limit is None:
        danger_limit = mu_ref + 1.25 * sigma_ref if high_is_bad else mu_ref - 1.25 * sigma_ref

    idx_to_pos = {idx: pos for pos, idx in enumerate(df.index)}

    slow_pos = [idx_to_pos[i] for i in slow_idx if i in idx_to_pos]
    infl_pos = [idx_to_pos[i] for i in inflex_idx if i in idx_to_pos]

    if slow_pos:
        ax.scatter(
            x[slow_pos],
            y[slow_pos],
            c="green",
            s=10,
            label="Slow regime",
            alpha=0.6,
        )

    if infl_pos:
        ax.scatter(
            x[infl_pos],
            y[infl_pos],
            c="red",
            s=18,
            label="Sustained inflexion",
            alpha=0.8,
        )

    ax.axhline(
        danger_limit,
        color="red",
        linestyle="--",
        label=f"danger threshold ≈ {danger_limit:.2f}",
    )

    ax.set_xlabel("Wheel mileage (km)")
    ax.set_ylabel(Y_LABELS.get(metric))

    title = metric if not title_prefix else f"{title_prefix} - {metric}"
    ax.set_title(title)

    ax.grid(True, alpha=0.3)
    ax.legend(loc="best", fontsize="x-small")

    fig.tight_layout()

    return fig


# ---------- PDF export ----------
def export_soh_pdf(
    df_stats: pd.DataFrame,
    df_alarms: pd.DataFrame | None,
    thresholds: dict | None,
    wheel_name: str,
    pdf_path,
    optimal_frac: float = 0.5,
    n_sigma_band: float = 1.0,
    n_sigma_danger: float = 2.0,
    include_batt_metric: bool = True,
    force_gaussian_plots:bool=False
):
    """
    Generates a summary PDF.
    """
    CUSUM = False

    def safe_metric(metric: str) -> bool:
        return metric in df_stats.columns and df_stats[metric].notna().any()

    ea_val = df_stats.get("arrhenius_ea_kj_per_mol", pd.Series([None])).iloc[0] if "arrhenius_ea_kj_per_mol" in df_stats.columns else None
    ea_mode = df_stats.get("arrhenius_auto_calibrated", pd.Series([None])).iloc[0] if "arrhenius_auto_calibrated" in df_stats.columns else None

    if ea_val is not None and not pd.isna(ea_val):
        mode_str = "AUTO" if bool(ea_mode) else "MANUAL"
        arr_suffix = f" (Arrhenius E_a ≈ {ea_val:.1f} kJ/mol, {mode_str})"
    else:
        arr_suffix = ""

    with PdfPages(pdf_path) as pdf:
        # Next page: linear trends
        try:
            for m in TREND_METRICS:
                if m not in df_stats.columns:
                    continue

                slope, p_val, sig = detect_trend_linear(df_stats, metric=m)
                if sig and slope is not None:
                    fig_trend, ax_trend = plt.subplots(figsize=(8.27, 5.83))

                    df_sorted = df_stats.sort_values("wheel_km")
                    x = df_sorted["wheel_km"].values
                    y = df_sorted[m].values

                    z = np.polyfit(x, y, 1)
                    p = np.poly1d(z)

                    ax_trend.plot(x, y, "o", label="Data", alpha=0.6)
                    ax_trend.plot(
                        x,
                        p(x),
                        "r-",
                        linewidth=2,
                        label=f"Trend: +{slope*1000:.4f} Ω/1000 km (p={p_val:.3f})",
                    )

                    ax_trend.set_xlabel("Wheel mileage (km)")
                    ax_trend.set_ylabel(Y_LABELS.get(m))
                    ax_trend.set_title(f"{wheel_name} - Trend {m}{arr_suffix}")
                    ax_trend.grid(True, alpha=0.3)
                    ax_trend.legend(loc="best")

                    fig_trend.tight_layout()
                    pdf.savefig(fig_trend)
                    plt.close(fig_trend)

        except Exception as e:
            if DEBUG:
                print(f"[export_soh_pdf] Error linear trends: {e}")

        # CUSUM pages
        try:
            km_max = float(df_stats["wheel_km"].max())
            ref_km_max = km_max * 0.3
            test_km_min = ref_km_max

            for m in CUSUM_METRICS:
                if m not in df_stats.columns:
                    continue

                alarm_idx, mu_ref, sigma_ref = cusum_detection(
                    df_stats,
                    metric=m,
                    ref_km_max=ref_km_max,
                    test_km_min=test_km_min,
                )

                if not alarm_idx or mu_ref is None or sigma_ref is None:
                    continue

                df_sorted = df_stats.dropna(subset=["wheel_km", m]).sort_values("wheel_km")
                x = df_sorted["wheel_km"].values
                y = df_sorted[m].values

                fig_cusum, ax_cusum = plt.subplots(figsize=(8.27, 5.83))

                ax_cusum.plot(x, y, "go", label="Normal", markersize=4, alpha=0.6)

                local_idx = [df_sorted.index.get_loc(i) for i in alarm_idx if i in df_sorted.index]

                if local_idx:
                    alarm_x = x[local_idx]
                    alarm_y = y[local_idx]
                    ax_cusum.plot(
                        alarm_x,
                        alarm_y,
                        "r*",
                        label="Change detected (CUSUM)",
                        markersize=10,
                    )

                ax_cusum.axhline(mu_ref, color="green", linestyle="--", label=f"µ_ref = {mu_ref:.4f}")
                ax_cusum.axhline(
                    mu_ref + 1.5 * sigma_ref,
                    color="orange",
                    linestyle=":",
                    label="µ_ref + 1.5σ",
                )
                ax_cusum.axhline(
                    mu_ref + 3.0 * sigma_ref,
                    color="red",
                    linestyle="-",
                    linewidth=1.5,
                    label="CUSUM threshold h",
                )

                ax_cusum.set_xlabel("Wheel mileage (km)")
                ax_cusum.set_ylabel(Y_LABELS.get(m))
                ax_cusum.set_title(f"{wheel_name} - CUSUM {m}{arr_suffix}")
                ax_cusum.grid(True, alpha=0.3)
                ax_cusum.legend(loc="best", fontsize="x-small")

                fig_cusum.tight_layout()
                pdf.savefig(fig_cusum)
                plt.close(fig_cusum)

                CUSUM = True

        except Exception as e:
            if DEBUG:
                print(f"[export_soh_pdf] Error CUSUM pages: {e}")


        # Gaussian graphs per metric
        metrics_to_plot = [
            "Req_median",
            "R_batt_median_25C",
            "R_mosfet_hot",
            "Req_95p",
            "sag_95p",
            "sag_max",
            "v_min_strong",
            "i_95p",
            "i_max",
            "temp_board_max",
            "temp_motor_max",
            # new: MOSFET stress / phase current
            "I_phase2_int",
            "i_phase_95p",
            "i_phase_max",
        ]

        for metric in metrics_to_plot:
            high_is_bad = metric != "v_min_strong"

            if not safe_metric(metric):
                continue

            if not CUSUM or force_gaussian_plots:
                try:
                    fig = plot_metric_gauss(
                        df_stats,
                        metric,
                        label=metric,
                        optimal_frac=optimal_frac,
                        n_sigma_band=n_sigma_band,
                        n_sigma_danger=n_sigma_danger,
                        title_prefix=wheel_name + arr_suffix,
                    )
                    pdf.savefig(fig)
                    plt.close(fig)
                except Exception as e:
                    if DEBUG:
                        print("Error plotting", metric, e)
                    continue

            try:
                th = thresholds.get(metric) if thresholds is not None else None
                fig_inf = plot_metric_inflexions(
                    df_stats,
                    metric=metric,
                    title_prefix=wheel_name + arr_suffix,
                    thresholds=th,
                    high_is_bad=high_is_bad,
                )
                pdf.savefig(fig_inf)
                plt.close(fig_inf)
            except Exception as e:
                if DEBUG:
                    print(f"Error plotting inflexion of {metric}: {e}")
                continue
