# EUC State of Health – Kotlin Multiplatform

Port Kotlin de `soh_core_en.py` (repo Python EUC_SOH) avec architecture multiplateforme.

## Architecture

```
euc-soh-kotlin/
├── euc-soh-core/      # Logique métier + analyse CSV (JVM)
├── euc-soh-android/   # Module Android + AndroidCsvOpener
└── euc-soh-desktop/   # Module Desktop JVM + DesktopCsvOpener
```

### euc-soh-core

Module central indépendant de toute plateforme. Contient :

- **`CsvSource`** – Interface d'abstraction pour l'accès aux CSV (io)
- **`FileProvider`** – Interface pour lister les fichiers à analyser (io)
- **`SourceDetector`** – Détection de source CSV (EUC World / WheelLog) — port de `detect_source()`
- **`Analyzer`** – Analyse d'un CSV ou d'un dossier, calcul de `Req` — port de `compute_req_stats_for_file()` et `analyze_folder_for_req()`
- **`ArrheniusNormalizer`** – Normalisation thermique Arrhenius + calibration automatique de Ea — port de `normalize_r_batt_to_25c()` et `calibrate_ea_from_logs()`
- **`ResistanceCalculator`** – Calculs de résistance équivalente, intégrale I² de phase
- **`GaussianAlarmDetector`** – Détection d'alarmes gaussiennes — port de `detect_alarms_gauss()`
- **`CUSUMDetector`** – Détection CUSUM de changement de régime — port de `cusum_detection()`
- **`TrendAnalyzer`** – Régression linéaire métrique vs km — port de `detect_trend_linear()`
- **Models** – `LogData`, `MOSFETParams`, `ThresholdInfo` (= `Threshold`), `WheelStatistics`, etc.

### euc-soh-android

Module Android App. Implémente `CsvSource` via `AndroidCsvOpener` :

- Supporte URI `content://` (Storage Access Framework / DocumentFile)
- Supporte chemins de fichiers classiques
- `MainActivity` : Hello World minimal

### euc-soh-desktop

Module JVM Desktop. Implémente `CsvSource` via `DesktopCsvOpener` :

- Accès direct au système de fichiers
- Filtre `.csv` insensible à la casse

## Principe d'architecture

```
euc-soh-core  ←── euc-soh-android
              ←── euc-soh-desktop
```

Le cœur (`euc-soh-core`) ne dépend d'aucune plateforme.  
L'injection de `CsvSource` se fait via constructeur (IoC).

## Utilisation rapide (Desktop)

```kotlin
import com.euc.soh.desktop.DesktopCsvOpener
import com.euc.soh.analysis.Analyzer
import com.euc.soh.io.FileProvider

// Adapter DesktopCsvOpener → FileProvider
val opener = DesktopCsvOpener()
val provider = object : FileProvider {
    override fun getFiles() = opener.listCsvFiles("/path/to/logs")
}

val stats = Analyzer.analyzeFolderForReq("MonEUC", provider)
println("Req médian bande: ${stats.reqBandLow} – ${stats.reqBandHigh} Ω")
stats.alarms.forEach { println("Alarme: ${it.fileName} – ${it.reasons}") }
```

## Utilisation rapide (Android)

```kotlin
import com.euc.soh.android.AndroidCsvOpener
import com.euc.soh.analysis.Analyzer
import com.euc.soh.io.FileProvider

val opener = AndroidCsvOpener(context)
val provider = object : FileProvider {
    override fun getFiles() = opener.listCsvFiles("content://...uri...")
}

val stats = Analyzer.analyzeFolderForReq("MonEUC", provider)
```

## Tests

```bash
# Tous les tests du core
./gradlew :euc-soh-core:test

# Tests individuels
./gradlew :euc-soh-core:test --tests SourceDetectionTest
./gradlew :euc-soh-core:test --tests ReqStatsTest
./gradlew :euc-soh-core:test --tests AlarmsTest
./gradlew :euc-soh-core:test --tests TrendTest
```

## Algorithmes portés

| Python (`soh_core_en.py`)      | Kotlin                              |
|-------------------------------|--------------------------------------|
| `detect_source(df)`           | `SourceDetector.detectSource(cols)` |
| `compute_req_stats_for_file()`| `Analyzer.analyzeLogForReq()`       |
| `analyze_folder_for_req()`    | `Analyzer.analyzeFolderForReq()`    |
| `normalize_r_batt_to_25c()`   | `ArrheniusNormalizer.normalizeRBattTo25C()` |
| `calibrate_ea_from_logs()`    | `ArrheniusNormalizer.calibrateEaFromLogs()` |
| `detect_alarms_gauss()`       | `GaussianAlarmDetector.detectAlarms()` |
| `cusum_detection()`           | `CUSUMDetector.detectCUSUM()`       |
| `detect_trend_linear()`       | `TrendAnalyzer.detectTrendLinear()` |
| `build_vidle_profile()`       | Intégré dans `Analyzer.analyzeLogForReq()` |
| `choose_battery_current_window()` | `ResistanceCalculator.chooseBatteryCurrentWindow()` |

## Dépendances principales (euc-soh-core)

- `kotlinx.dataframe:0.14.1` – API DataFrame (remplace pandas)
- `commons-math3:3.6.1` – Régression linéaire (`SimpleRegression`)
- `kotlinx-coroutines-core` – Analyse parallèle des CSV
- `kotest` – Framework de tests
