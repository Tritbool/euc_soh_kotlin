#!/usr/bin/env python3
"""
Générateur de CSV WheelLog/EUCWorld pour tests de stress mémoire.
Mode forcé "roulage continu" : pas de phases d'arrêt, pour permettre
un calcul de Req à partir de la tension et des intensités.

Usage: python3 gen_wheellog.py [output_path] [taille_mb]

  output_path : chemin du fichier de sortie (défaut: wheellog_test.csv)
  taille_mb   : taille cible en Mo (défaut: 400)
"""
import math
import os
import random
import sys
from datetime import datetime, timedelta

OUTPUT_PATH = sys.argv[1] if len(sys.argv) > 1 else "eucw_test.csv"
TARGET_MB   = int(sys.argv[2]) if len(sys.argv) > 2 else 400
TARGET_BYTES = TARGET_MB * 1024 * 1024

HEADER = (
    "datetime,duration,duration_riding,distance,distance_total,"
    "speed,speed_avg,speed_avg_riding,speed_max,speed_limit,acceleration,"
    "voltage,current,current_phase,power,energy_consumption,energy_consumed,"
    "battery,battery_corrected_voltage,temp,temp_motor,temp_batt,temp_cpu,"
    "temp_imu,safety_margin,cpu_load,tilt,roll,fan,alert,alarm,"
    "gps_datetime,gps_duration,gps_duration_riding,gps_distance,"
    "gps_lat,gps_lon,gps_speed,gps_speed_avg,gps_speed_avg_riding,gps_speed_max,"
    "gps_alt,gps_bearing,gps_acc,hr,extra"
)

EXTRA_KEYS = [
    "euc.batteryCircuitResistance=0.09",
    "euc.distance=0",
    "euc.distanceUser=100",
    "euc.distanceTotal=101",
    "euc.firmware=6.0.10",
    "euc.make=Veteran",
    "euc.model=Sherman L",
    "euc.type=VETERAN",
    "euc.btAddress=11:22:33:44:55:66",
    "euc.btName=LK00000",
    "euc.speedCorrection=0",
    "euc.distCorrection=0",
    "info.manufacturer=samsung",
    "info.brand=samsung",
    "info.device=e3q",
    "info.display=blahblahblah",
    "info.id=fooBar",
    "info.model=XXXXX",
    "info.product=YYYYY",
    "info.sdk=36",
    "info.instance=ABC",
    "info.appVersionName=2.64.3",
    "info.appVersionCode=2020640003",
    "info.timeZone=WORLD",
    "info.gnssTimeSkew=0",
    "info.networkTimeSkew=0",
    "info.gpsTimeSkew=0",
    "info.gpsTimeCorrection=0",
]

AVG_ROW_BYTES = 230
ROWS_ESTIMATE = max(1, (TARGET_BYTES - len(HEADER) - 1) // AVG_ROW_BYTES)

print(f"Cible        : {TARGET_MB} Mo ({TARGET_BYTES:,} octets)")
print(f"Lignes est.  : ~{ROWS_ESTIMATE:,}")
print(f"Sortie       : {OUTPUT_PATH}")
print("Mode         : roulage continu")
print()

random.seed(42)

t0 = datetime(2026, 5, 24, 10, 43, 10, 727000)
dt_s = 0.2

# Modèle simplifié cohérent pour Req
# V_loaded = V_oc - I * Req
# avec V_oc qui décroît lentement avec la décharge.
REQ_OHM = 0.09
V_OC_START = 151.20
V_OC_END   = 134.00
CURRENT_BASE = 7.0
CURRENT_SWING = 24.0
SPEED_BASE = 18.0
SPEED_SWING = 10.0

lat_base = 45.9072442
lon_base = 6.1274796
distance_total_base = 101

speed_max = 0.0
speed_sum = 0.0
distance = 0.0
gps_distance = 0.0
energy_consumed = 0.0
extra_cycle = len(EXTRA_KEYS)


def current_profile(i):
    t = i * dt_s
    base = CURRENT_BASE
    slow = 8.0 * math.sin(t / 45.0)
    medium = 6.0 * math.sin(t / 9.0 + 0.7)
    fast = 4.0 * math.sin(t / 2.6 + 1.3)
    noise = random.gauss(0, 0.8)
    current = base + slow + medium + fast + noise
    return max(2.0, min(CURRENT_BASE + CURRENT_SWING, current))


def speed_from_current(current, i):
    t = i * dt_s
    cruise = SPEED_BASE + 4.0 * math.sin(t / 60.0)
    coupling = 0.45 * current
    noise = random.gauss(0, 0.35)
    speed = cruise + coupling + noise
    return max(8.0, min(SPEED_BASE + SPEED_SWING + 6.0, speed))


written_bytes = 0
prev_speed = None
i = 0

with open(OUTPUT_PATH, 'w', buffering=8 * 1024 * 1024, newline='\n') as f:
    header_line = HEADER + "\n"
    f.write(header_line)
    written_bytes += len(header_line.encode())

    while written_bytes < TARGET_BYTES:
        ts = t0 + timedelta(seconds=i * dt_s)
        dt_str = ts.strftime("%Y-%m-%dT%H:%M:%S.") + f"{ts.microsecond // 1000:03d}+0200"
        duration = max(1, int((i + 1) * dt_s))
        duration_riding = duration

        soc_ratio = min(1.0, written_bytes / TARGET_BYTES)
        v_oc = V_OC_START - (V_OC_START - V_OC_END) * soc_ratio

        current = round(current_profile(i), 2)
        speed = round(speed_from_current(current, i), 2)
        current_phase = round(current * 1.12 + random.gauss(0, 0.25), 2)
        voltage = round(v_oc - current * REQ_OHM + random.gauss(0, 0.03), 2)
        battery_corrected_voltage = round(voltage + current * REQ_OHM, 2)
        power = round(voltage * current)

        speed_sum += speed
        speed_avg = round(speed_sum / (i + 1), 2)
        speed_avg_riding = speed_avg
        speed_max = max(speed_max, speed)

        distance += speed * dt_s / 3600.0
        distance_total = distance_total_base + distance
        energy_consumed += power * dt_s / 3600.0
        energy_consumption = round(energy_consumed / max(distance, 1e-9), 1)

        acceleration = 0.0 if prev_speed is None else round((speed - prev_speed) / dt_s, 2)
        prev_speed = speed

        battery = max(0, min(99, round(99 - soc_ratio * 70)))
        temp = round(28 + 0.03 * current + 0.002 * duration, 1)
        temp_motor = round(30 + 0.18 * current + 0.003 * duration, 1)
        temp_batt = round(26 + 0.05 * current + 0.0015 * duration, 1)
        temp_cpu = max(37, min(85, round(54 + 0.02 * current + random.gauss(0, 0.4))))
        temp_imu = max(30, min(65, round(37 + 0.01 * current + random.gauss(0, 0.3))))
        safety_margin = max(0, min(99, round(99 - current * 1.3)))
        cpu_load = max(0, min(100, round(18 + current * 0.7 + random.gauss(0, 2.0))))
        tilt = round(-1.5 + 0.12 * current + random.gauss(0, 0.25), 1)
        roll = round(random.gauss(0, 0.35), 1)
        fan = 1 if temp_motor >= 55 else 0

        gps_ts_str = gps_dur = gps_dur_riding = gps_dist_str = ""
        gps_lat = gps_lon = gps_spd = gps_spd_avg = gps_spd_avg_r = gps_spd_max_s = ""
        gps_alt = gps_bearing = gps_acc_s = ""

        if i % 5 == 0:
            gps_i = i // 5
            gps_t = t0 + timedelta(seconds=gps_i)
            gps_ts_str = gps_t.strftime("%Y-%m-%dT%H:%M:%S.000+0200")
            gps_distance = distance
            gps_speed = round(speed + random.gauss(0, 0.15), 2)
            lat = round(lat_base + gps_distance * 0.0001 * math.cos(gps_i * 0.01), 7)
            lon = round(lon_base + gps_distance * 0.0001 * math.sin(gps_i * 0.01), 7)
            alt = round(454.0 + 0.8 * math.sin(gps_i / 300.0) + random.gauss(0, 0.6), 1)
            gps_dur = str(max(1, gps_i + 1))
            gps_dur_riding = gps_dur
            gps_dist_str = f"{gps_distance:.3f}"
            gps_lat = f"{lat}"
            gps_lon = f"{lon}"
            gps_spd = f"{gps_speed}"
            gps_spd_avg = f"{speed_avg}"
            gps_spd_avg_r = f"{speed_avg_riding}"
            gps_spd_max_s = f"{round(speed_max, 2)}"
            gps_alt = f"{alt}"
            gps_bearing = f"{int((gps_i * 2.5) % 360)}"
            gps_acc_s = f"{random.randint(8, 18)}"

        extra = EXTRA_KEYS[i % extra_cycle] if i < extra_cycle * 4 else ""

        row = (
            f"{dt_str},{duration},{duration_riding},{distance:.3f},{distance_total:.3f},"
            f"{speed},{speed_avg},{speed_avg_riding},{round(speed_max,2)},90.00,"
            f"{acceleration},{voltage},{current},{current_phase},{power},{energy_consumption},"
            f"{round(energy_consumed,1)},{battery},{battery_corrected_voltage},{temp},{temp_motor},{temp_batt},"
            f"{temp_cpu},{temp_imu},{safety_margin},{cpu_load},{tilt},{roll},{fan},,,"
            f"{gps_ts_str},{gps_dur},{gps_dur_riding},{gps_dist_str},"
            f"{gps_lat},{gps_lon},{gps_spd},{gps_spd_avg},{gps_spd_avg_r},{gps_spd_max_s},"
            f"{gps_alt},{gps_bearing},{gps_acc_s},,{extra}\n"
        )

        f.write(row)
        written_bytes += len(row.encode())
        i += 1

        if i % 200_000 == 0:
            mb = written_bytes / 1024 / 1024
            pct = written_bytes / TARGET_BYTES * 100
            print(f"  {i:,} lignes — {mb:.0f} Mo ({pct:.0f}%)")

final_mb = os.path.getsize(OUTPUT_PATH) / 1024 / 1024
print(f"\nTerminé : {i:,} lignes, {final_mb:.1f} Mo")
print(f"Req simulée : {REQ_OHM} ohm")
