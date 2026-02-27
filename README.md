# EUC SoH - Kotlin Multiplatform

Kotlin port of the [EUC_SOH Python project](https://github.com/Tritbool/EUC_SOH) for electric unicycle battery and MOSFET health monitoring.

## Architecture

```
euc_soh_kotlin/
├── euc-soh-core/          # Platform-agnostic business logic
│   ├── model/              # Data classes (MOSFETParams, ThresholdInfo)
│   ├── analysis/           # Core algorithms (Arrhenius, CUSUM, trends)
│   └── CsvSource.kt        # Platform abstraction for file I/O
├── euc-soh-android/       # Android app
│   └── AndroidCsvOpener    # Handles ContentResolver + File APIs
└── euc-soh-desktop/       # JVM desktop
    └── DesktopCsvOpener    # java.io.File wrapper
```

## Key Differences from Python Version

### ✅ What's Implemented

1. **DataFrame-based analysis** (kotlinx.dataframe ≈ pandas)
   - CSV loading and parsing
   - Column operations (median, quantile, groupBy)
   - Vectorized calculations

2. **Thermal normalization (Arrhenius)**
   - Battery resistance normalization to 25°C
   - Auto-calibration of activation energy E_a
   - Temperature-dependent MOSFET modeling

3. **Source detection**
   - Auto-detect EUC World vs WheelLog format
   - Distance normalization (km vs meters)
   - Datetime parsing for log ordering

4. **Platform abstraction**
   - `CsvSource` interface for Android/Desktop
   - No hardcoded file paths
   - Ready for ContentResolver (Android SAF)

### ❌ Not Yet Implemented (from Python soh_core_en.py)

- `compute_req_stats_for_file()` - Main per-file analysis function
- `build_vidle_profile()` - Local V_idle calculation based on SoC
- `analyze_folder_for_req()` - Folder aggregation
- `detect_alarms_gauss()` - Gaussian alarm detection
- `cusum_detection()` - CUSUM regime change detection
- `detect_trend_linear()` - Linear regression on metrics
- `detect_slope_inflexions()` - Slope-based drift detection

These will be added in follow-up commits as the core is validated.

## Usage

### Desktop

```kotlin
import io.github.tritbool.euc_soh.core.CsvSource
import io.github.tritbool.euc_soh.desktop.DesktopCsvOpener

val csvSource: CsvSource = DesktopCsvOpener()
val files = csvSource.listCsvFiles("/path/to/logs")
// TODO: call analysis functions when implemented
```

### Android

```kotlin
import io.github.tritbool.euc_soh.android.AndroidCsvOpener

val csvSource = AndroidCsvOpener(context)
val files = csvSource.listCsvFiles("content://...")
// TODO: call analysis functions when implemented
```

## Testing

Run tests from core module:

```bash
./gradlew :euc-soh-core:test
```

Current coverage:
- ✅ Source detection (EUC World / WheelLog)
- ✅ Thermal normalization (Arrhenius law)
- ✅ MOSFET resistance modeling
- ❌ Main analysis pipeline (TODO)

## Dependencies

- **kotlinx.dataframe 0.14.1** - DataFrame operations (replaces pandas)
- **Apache Commons Math 3.6.1** - Statistical functions
- **Kotest 5.9.0** - Testing framework

## Build

```bash
# Core library only
./gradlew :euc-soh-core:build

# Android app
./gradlew :euc-soh-android:assembleDebug

# Desktop (future CLI)
./gradlew :euc-soh-desktop:build
```

## Contributing

This is a direct port of the Python version. When implementing missing features:

1. **Preserve Python logic exactly** (especially Arrhenius, V_idle_local, SoC filtering)
2. **Use DataFrame operations** instead of manual loops where possible
3. **Add unit tests** for each new function
4. **Document differences** if Kotlin behavior diverges

## License

Same as parent Python project (GPL-3.0).
