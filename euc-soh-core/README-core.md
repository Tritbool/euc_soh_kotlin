// README.md - Guide de démarrage rapide

# EUC State of Health - Core Kotlin

## Installation

### Gradle Multi-module Setup

```
euc-soh/
├── euc-soh-core/           # Ce module
├── euc-soh-android/        # App Android (à créer)
├── euc-soh-desktop/        # App Desktop (à créer)
├── settings.gradle.kts
└── build.gradle.kts
```

**settings.gradle.kts** :
```kotlin
rootProject.name = "euc-soh"
include(":euc-soh-core")
include(":euc-soh-android")
include(":euc-soh-desktop")
```

**build.gradle.kts (root)** :
```kotlin
plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.5.12" apply false
}
```

## Utilisation du Core

### Exemple complet d'analyse

```kotlin
import com.euc.soh.analysis.*
import com.euc.soh.model.*
import com.euc.soh.parser.FolderAnalyzer
import java.nio.file.Paths

fun main() {
    // 1. Configuration optionnelle MOSFET
    val mosfetParams = MOSFETParams(
        rDsOn25cTotal = 0.020,  // 20 mΩ total @ 25°C
        tempCoeffRel = 0.005,    // +0.5%/°C
        rWiring = 0.002          // 2 mΩ câblage
    )
    
    // 2. Analyse d'un dossier de logs
    val folderPath = Paths.get("/path/to/logs")
    val analyzer = FolderAnalyzer()
    
    val statistics: WheelStatistics = analyzer.analyzeFolder(
        folderPath = folderPath,
        wheelName = "My EUC",
        mosfetParams = mosfetParams
    )
    
    // 3. Détection d'alarmes
    val alarmDetector = GaussianAlarmDetector
    val alarms = alarmDetector.detectAlarms(
        logs = statistics.logs,
        thresholds = statistics.thresholds,
        checkAbsoluteLimit = true
    )
    
    // 4. Affichage résultats
    println("=== ${statistics.wheelName} ===")
    println("Nombre de logs: ${statistics.logs.size}")
    println("Config pack: ${statistics.nsGlobal}S (${statistics.vNominal}V)")
    println("R_pack nominal: ${statistics.rPackNominal}Ω")
    println()
    println("Bande Req saine: ${statistics.reqBandLow} - ${statistics.reqBandHigh}Ω")
    
    if (statistics.rBattBandLow != null && statistics.rBattBandHigh != null) {
        println("Bande R_batt saine: ${statistics.rBattBandLow} - ${statistics.rBattBandHigh}Ω")
    }
    
    println()
    println("Alarmes détectées: ${alarms.size}")
    alarms.forEach { alarm ->
        println("  ${alarm.fileName} @ ${alarm.wheelKm}km: ${alarm.reasons}")
    }
    
    // 5. Détection CUSUM (changement de régime)
    val cusumResult = CUSUMDetector.detectCUSUM(
        logs = statistics.logs,
        metric = "Req_median"
    )
    
    if (cusumResult.alarmIndices.isNotEmpty()) {
        println()
        println("Changement de régime détecté à:")
        cusumResult.alarmIndices.forEach { idx ->
            val log = statistics.logs[idx]
            println("  ${log.fileName} @ ${log.wheelKm}km")
        }
    }
}
```

### Exemple analyse single log

```kotlin
import com.euc.soh.parser.CSVParser
import com.euc.soh.analysis.ResistanceCalculator

fun analyzeSingleLog(csvPath: Path): LogData? {
    // 1. Parse CSV
    val parser = CSVParser()
    val rawData = parser.parseCSV(csvPath)
    
    // 2. Détection source (EUC World ou WheelLog)
    val source = parser.detectSource(rawData.headers)
    
    // 3. Calcul V_idle
    val vIdle = rawData.points
        .filter { it.current.abs() < 3.0 }
        .map { it.voltage }
        .percentile(0.95)
    
    // 4. Filtrage points sous charge
    val (iMin, iMax) = ResistanceCalculator.chooseBatteryCurrentWindow(ns = 20)
    val filteredPoints = ResistanceCalculator.filterPointsForReq(
        points = rawData.points,
        speedThreshold = 20.0,
        currentMin = iMin,
        currentMax = iMax
    )
    
    // 5. Calcul Req
    val reqStats = ResistanceCalculator.computeReqStats(filteredPoints, vIdle)
        ?: return null
    
    // 6. Normalisation Arrhenius
    val tempMax = rawData.points.mapNotNull { it.systemTemp }.maxOrNull()
    val reqMedian25C = ArrheniusNormalizer.normalizeRBattTo25C(
        rBattMeasured = reqStats.reqMedian,
        tempMeasuredC = tempMax,
        eaJPerMol = 20000.0
    )
    
    // 7. Construction LogData
    return LogData(
        fileName = csvPath.fileName.toString(),
        source = source,
        datetimeFirst = rawData.points.firstOrNull()?.datetime,
        wheelKm = extractWheelKm(rawData),
        wheelKmSource = "auto",
        vIdle = vIdle,
        nsSeries = estimateNs(vIdle),
        socRefOk = false,
        socRefVFull = null,
        nPoints = filteredPoints.size,
        reqMean = reqStats.reqMean,
        reqMedian = reqStats.reqMedian,
        reqMedian25C = reqMedian25C,
        req95p = reqStats.req95p,
        sag95p = reqStats.sag95p,
        sagMax = reqStats.sagMax,
        vMinStrong = filteredPoints.minOf { it.voltage },
        iMax = reqStats.iMax,
        i95p = reqStats.i95p,
        tempBoardMax = tempMax,
        tempMotorMax = rawData.points.mapNotNull { it.motorTemp }.maxOrNull(),
        iPhase2Int = null,  // Calculer si phase current disponible
        iPhaseMax = null,
        iPhase95p = null,
        rBattMedian = null,
        rBattMedian25C = null,
        rMosfetHot = null
    )
}
```

### Exemple custom threshold

```kotlin
fun customThresholds(logs: List<LogData>) {
    // Calcul automatique
    val autoThresholds = GaussianAlarmDetector.computeThresholds(
        logs = logs,
        optimalFrac = 0.5,
        nSigma = 2.0
    )
    
    // Override manuel
    val customThresholds = autoThresholds.toMutableMap()
    customThresholds["Req_median"] = Threshold(
        mean = 0.08,
        std = 0.01,
        limit = 0.10,  // Seuil personnalisé
        direction = AlarmDirection.HIGHER_IS_BAD
    )
    
    // Détection avec thresholds custom
    val alarms = GaussianAlarmDetector.detectAlarms(logs, customThresholds)
}
```

## Tests

```bash
# Lancer tous les tests
./gradlew :euc-soh-core:test

# Tests avec rapport détaillé
./gradlew :euc-soh-core:test --info

# Tests d'une classe spécifique
./gradlew :euc-soh-core:test --tests ArrheniusNormalizerTest
```

## Structure des données

### LogData
Représente un fichier CSV analysé.

**Champs principaux** :
- `reqMedian` : Résistance équivalente médiane (Ω)
- `reqMedian25C` : Résistance normalisée à 25°C (Ω)
- `sag95p` : Chute de tension 95e percentile (V)
- `wheelKm` : Kilométrage total roue
- `tempBoardMax` : Température carte max (°C)

### WheelStatistics
Agrégation de tous les logs d'une roue.

**Champs principaux** :
- `logs` : Liste de tous les LogData
- `nsGlobal` : Nombre de cellules en série
- `reqBandLow/High` : Bande de résistance "saine"
- `thresholds` : Seuils calculés par métrique
- `alarms` : Liste des alarmes détectées

## Algorithmes

### Arrhenius Normalizer
Normalise les résistances à 25°C pour compenser l'effet thermique.

**Formule** : R(25°C) = R(T) / exp((Ea/R) × (1/T - 1/T_ref))

**Calibration auto** : Régression linéaire sur ln(R) vs 1/T

### CUSUM Detector
Détecte les changements de régime (shifts brusques).

**Paramètres** :
- `kSigma` : Sensibilité à la dérive (défaut: 1.0)
- `hSigma` : Seuil déclenchement (défaut: 5.0)
- `cooldownKm` : Distance réfractaire post-alarme (défaut: 500km)

### Gaussian Alarm Detector
Détecte les valeurs anormales via distribution gaussienne.

**Méthode** :
1. Tri des logs par Req_median
2. Référence = 50% meilleurs logs
3. Calcul µ et σ par métrique
4. Seuil danger = µ ± nσ (défaut: 2σ)

## Intégration Android/Desktop

### Dans Android (build.gradle.kts)
```kotlin
dependencies {
    implementation(project(":euc-soh-core"))
    // ... autres dépendances Android
}
```

### Dans Desktop (build.gradle.kts)
```kotlin
dependencies {
    implementation(project(":euc-soh-core"))
    // ... autres dépendances Desktop
}
```

## Prochaines étapes

### À implémenter dans le core
- [ ] `CSVParser` complet (EUC World + WheelLog)
- [ ] `FolderAnalyzer` orchestration
- [ ] `TrendAnalyzer` (régression linéaire + inflexions)
- [ ] `PackInference` (détection config batterie)
- [ ] Gestion SoC voltage-based
- [ ] Export JSON/CSV des résultats

### Android
- [ ] UI Jetpack Compose
- [ ] Storage Access Framework
- [ ] MPAndroidChart integration
- [ ] PDF export iText7

### Desktop
- [ ] UI Compose Desktop
- [ ] File chooser natif
- [ ] JFreeChart integration
- [ ] Packaging (MSI/DMG/DEB)

## Contribution

Le core est maintenant prêt pour être étendu. Les classes principales sont en place :

✅ **Models** : Toutes les structures de données  
✅ **ArrheniusNormalizer** : Normalisation thermique  
✅ **ResistanceCalculator** : Calculs Req et stats  
✅ **CUSUMDetector** : Détection changements régime  
✅ **GaussianAlarmDetector** : Détection alarmes  
✅ **Constants** : Configuration globale  
✅ **Tests** : Framework Kotest en place

**À compléter** :
- CSVParser (parsing CSV brut)
- FolderAnalyzer (orchestration multi-fichiers)
- TrendAnalyzer (tendances linéaires)
- PackInference (config batterie)

## License

[Spécifier licence]

## Contact

[Contact info]
