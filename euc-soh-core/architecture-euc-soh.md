# Architecture EUC State of Health - Migration Kotlin

## Vue d'ensemble

Projet de migration d'une application Python/Kivy d'analyse de State of Health (SoH) pour batteries EUC vers une architecture Kotlin multiplateforme.

### Objectifs

1. **Core Kotlin** : Bibliothèque pure JVM contenant toute la logique métier
2. **App Android** : Application native avec génération de graphiques et PDF
3. **Frontend PC** : Application desktop (Compose Desktop recommandé)

---

## 1. Architecture Core Kotlin

### 1.1 Structure du projet

```
euc-soh-core/
├── src/main/kotlin/
│   ├── com/euc/soh/
│   │   ├── model/           # Modèles de données
│   │   │   ├── LogData.kt
│   │   │   ├── Statistics.kt
│   │   │   ├── MOSFETParams.kt
│   │   │   └── Alarm.kt
│   │   ├── parser/          # Parsing CSV
│   │   │   ├── CSVParser.kt
│   │   │   ├── EUCWorldParser.kt
│   │   │   └── WheelLogParser.kt
│   │   ├── analysis/        # Algorithmes d'analyse
│   │   │   ├── ResistanceCalculator.kt
│   │   │   ├── ArrheniusNormalizer.kt
│   │   │   ├── CUSUMDetector.kt
│   │   │   ├── TrendAnalyzer.kt
│   │   │   └── GaussianAlarmDetector.kt
│   │   ├── config/          # Configuration pack batterie
│   │   │   ├── PackInference.kt
│   │   │   └── Constants.kt
│   │   └── export/          # Export données
│   │       └── DataExporter.kt
│   └── resources/
└── build.gradle.kts
```

### 1.2 Modèles de données

#### LogData
```kotlin
data class LogData(
    val fileName: String,
    val source: LogSource,
    val datetimeFirst: String?,
    val wheelKm: Double?,
    val vIdle: Double,
    val nsSeries: Int?,
    val socRefOk: Boolean,
    val socRefVFull: Double?,
    val nPoints: Int,
    val reqMean: Double,
    val reqMedian: Double,
    val reqMedian25C: Double,
    val req95p: Double,
    val sag95p: Double,
    val sagMax: Double,
    val vMinStrong: Double,
    val iMax: Double,
    val i95p: Double,
    val tempBoardMax: Double?,
    val tempMotorMax: Double?,
    val iPhase2Int: Double?,
    val iPhaseMax: Double?,
    val iPhase95p: Double?,
    val rBattMedian: Double?,
    val rBattMedian25C: Double?,
    val rMosfetHot: Double?
)

enum class LogSource {
    EUC_WORLD, WHEELLOG
}
```

#### Statistics
```kotlin
data class WheelStatistics(
    val logs: List<LogData>,
    val nsGlobal: Int?,
    val vNominal: Double?,
    val rPackNominal: Double?,
    val reqBandLow: Double,
    val reqBandHigh: Double,
    val rBattBandLow: Double?,
    val rBattBandHigh: Double?,
    val arrheniusEaKJperMol: Double,
    val arrheniusAutoCalibrated: Boolean,
    val alarms: List<Alarm>,
    val thresholds: Map<String, Threshold>
)

data class Threshold(
    val mean: Double,
    val std: Double,
    val limit: Double,
    val direction: Direction
)

enum class Direction {
    HIGHER_IS_BAD, LOWER_IS_BAD
}
```

### 1.3 Algorithmes principaux

#### ArrheniusNormalizer
Normalisation thermique des résistances vers 25°C
- `normalizeRBattTo25C(rMeasured, tempC, eaJperMol): Double`
- `calibrateEaFromLogs(logs, metric, tempCol): Double`

#### CUSUMDetector
Détection de changement de régime
- `detectCUSUM(logs, metric, refKmMax, testKmMin, kSigma, hSigma): CUSUMResult`
- `computeThresholds(referenceData): CUSUMThreshold`

#### TrendAnalyzer
Analyse de tendance linéaire
- `detectLinearTrend(logs, metric, kmMinSpan): TrendResult`
- `detectSlopeInflexions(logs, metric, windowKm): InflexionResult`

#### GaussianAlarmDetector
Détection d'alarmes via distribution gaussienne
- `computeThresholds(logs, optimalFrac, nSigma): Map<String, Threshold>`
- `detectAlarms(logs, thresholds): List<Alarm>`

### 1.4 Dépendances recommandées

```kotlin
// build.gradle.kts
dependencies {
    // Parsing CSV
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.3")
    
    // Calculs statistiques
    implementation("org.apache.commons:commons-math3:3.6.1")
    
    // Dates
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    
    // Tests
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
}
```

---

## 2. Application Android Kotlin

### 2.1 Architecture

**Pattern MVVM** avec Jetpack Compose

```
euc-soh-android/
├── src/main/kotlin/
│   ├── com/euc/soh/android/
│   │   ├── ui/
│   │   │   ├── screens/
│   │   │   │   ├── HomeScreen.kt
│   │   │   │   ├── FolderSelectionScreen.kt
│   │   │   │   ├── AnalysisScreen.kt
│   │   │   │   └── ResultScreen.kt
│   │   │   ├── components/
│   │   │   │   ├── MetricChart.kt
│   │   │   │   ├── AlarmBanner.kt
│   │   │   │   └── StatisticsTable.kt
│   │   │   └── theme/
│   │   │       └── Theme.kt
│   │   ├── viewmodel/
│   │   │   ├── AnalysisViewModel.kt
│   │   │   └── ResultViewModel.kt
│   │   ├── charts/
│   │   │   ├── ChartRenderer.kt
│   │   │   └── MetricChartFactory.kt
│   │   ├── pdf/
│   │   │   └── PDFExporter.kt
│   │   └── MainActivity.kt
│   └── res/
└── build.gradle.kts
```

### 2.2 Dépendances Android

```kotlin
// build.gradle.kts (app module)
dependencies {
    // Core Kotlin
    implementation(project(":euc-soh-core"))
    
    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Charts
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // Alternative moderne : implementation("com.patrykandpatrick.vico:compose:1.13.1")
    
    // PDF generation
    implementation("com.itextpdf:itext7-core:8.0.2")
    
    // Storage Access Framework
    implementation("androidx.documentfile:documentfile:1.0.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 2.3 Composants clés

#### AnalysisViewModel
```kotlin
class AnalysisViewModel(
    private val analyzer: FolderAnalyzer,
    private val alarmDetector: GaussianAlarmDetector
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()
    
    fun analyzeFolderAsync(folderUri: Uri, wheelName: String?, mosfetParams: MOSFETParams?) {
        viewModelScope.launch {
            _uiState.value = AnalysisUiState.Loading
            try {
                val stats = analyzer.analyzeFolder(folderUri, mosfetParams)
                val alarms = alarmDetector.detectAlarms(stats.logs, stats.thresholds)
                _uiState.value = AnalysisUiState.Success(stats.copy(alarms = alarms))
            } catch (e: Exception) {
                _uiState.value = AnalysisUiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}

sealed class AnalysisUiState {
    object Idle : AnalysisUiState()
    object Loading : AnalysisUiState()
    data class Success(val statistics: WheelStatistics) : AnalysisUiState()
    data class Error(val message: String) : AnalysisUiState()
}
```

#### MetricChart (Compose + MPAndroidChart)
```kotlin
@Composable
fun MetricChart(
    logs: List<LogData>,
    metric: MetricType,
    threshold: Threshold?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setDrawGridBackground(false)
                // Configuration du chart...
            }
        },
        update = { chart ->
            chart.data = buildLineData(logs, metric, threshold)
            chart.invalidate()
        },
        modifier = modifier
    )
}
```

#### PDFExporter
```kotlin
class PDFExporter(private val context: Context) {
    
    suspend fun exportToPDF(
        statistics: WheelStatistics,
        wheelName: String,
        outputUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                val pdfWriter = PdfWriter(outputStream)
                val pdfDocument = PdfDocument(pdfWriter)
                val document = Document(pdfDocument, PageSize.A4)
                
                // Titre
                document.add(Paragraph("$wheelName - State of Health Report")
                    .setFontSize(20f)
                    .setBold())
                
                // Statistiques globales
                addSummaryTable(document, statistics)
                
                // Graphiques (convertis en images)
                addChartsToDocument(document, statistics)
                
                // Alarmes
                addAlarmsTable(document, statistics.alarms)
                
                document.close()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

## 3. Frontend PC

### 3.1 Option recommandée : Compose Desktop

**Avantages** :
- Partage 80%+ du code UI avec Android
- API déclarative moderne (comme Jetpack Compose)
- Performance native
- Packaging facile (JVM + natives)

```
euc-soh-desktop/
├── src/main/kotlin/
│   ├── com/euc/soh/desktop/
│   │   ├── ui/
│   │   │   ├── screens/        # Réutilisation des screens Android
│   │   │   ├── components/     # Adaptations desktop
│   │   │   └── theme/
│   │   ├── charts/
│   │   │   └── DesktopChartRenderer.kt
│   │   ├── pdf/
│   │   │   └── DesktopPDFExporter.kt
│   │   └── Main.kt
│   └── resources/
└── build.gradle.kts
```

### 3.2 Dépendances Desktop

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose") version "1.5.12"
}

dependencies {
    // Core
    implementation(project(":euc-soh-core"))
    
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    
    // Charts pour desktop
    implementation("org.jfree:jfreechart:1.5.4")
    // ou : implementation("com.github.kscripting:plotly.kt:0.7.0")
    
    // PDF (même lib qu'Android)
    implementation("com.itextpdf:itext7-core:8.0.2")
    
    // File chooser natif
    implementation("net.java.dev.jna:jna:5.13.0")
}

compose.desktop {
    application {
        mainClass = "com.euc.soh.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "EUC SoH Analyzer"
            packageVersion = "1.0.0"
        }
    }
}
```

### 3.3 Main Desktop

```kotlin
// Main.kt
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "EUC State of Health Analyzer",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MaterialTheme {
            val navController = rememberNavController()
            NavHost(navController, startDestination = "home") {
                composable("home") { HomeScreen(navController) }
                composable("analysis") { AnalysisScreen(navController) }
                composable("results/{wheelName}") { backStackEntry ->
                    ResultScreen(
                        navController,
                        backStackEntry.arguments?.getString("wheelName")
                    )
                }
            }
        }
    }
}
```

### 3.4 Adaptation Charts Desktop

```kotlin
@Composable
fun DesktopMetricChart(
    logs: List<LogData>,
    metric: MetricType,
    threshold: Threshold?,
    modifier: Modifier = Modifier
) {
    // Utilisation de JFreeChart via Swing interop
    SwingPanel(
        factory = {
            ChartPanel(createJFreeChart(logs, metric, threshold))
        },
        modifier = modifier
    )
}

private fun createJFreeChart(
    logs: List<LogData>,
    metric: MetricType,
    threshold: Threshold?
): JFreeChart {
    val dataset = XYSeriesCollection()
    val series = XYSeries("Metric")
    
    logs.forEach { log ->
        log.wheelKm?.let { km ->
            series.add(km, metric.getValue(log))
        }
    }
    dataset.addSeries(series)
    
    return ChartFactory.createXYLineChart(
        metric.label,
        "Wheel km",
        metric.unit,
        dataset,
        PlotOrientation.VERTICAL,
        true, true, false
    )
}
```

---

## 4. Structure Multi-module Gradle

### 4.1 settings.gradle.kts

```kotlin
rootProject.name = "euc-soh"

include(":euc-soh-core")
include(":euc-soh-android")
include(":euc-soh-desktop")
```

### 4.2 build.gradle.kts (root)

```kotlin
plugins {
    kotlin("jvm") version "1.9.22" apply false
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.compose") version "1.5.12" apply false
}

allprojects {
    group = "com.euc.soh"
    version = "1.0.0"
    
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

---

## 5. Migration des algorithmes Python → Kotlin

### 5.1 Correspondances clés

| Python | Kotlin | Notes |
|--------|--------|-------|
| `pandas.DataFrame` | `List<LogData>` | Approche type-safe |
| `numpy.array` | `DoubleArray` | Performance similaire |
| `scipy.stats.linregress` | Apache Commons Math `SimpleRegression` | |
| `matplotlib` | MPAndroidChart / JFreeChart | |
| `PdfPages` | iText7 | PDF generation |

### 5.2 Algorithme Arrhenius (exemple)

**Python** :
```python
def normalize_r_batt_to_25c(r_batt_measured, temp_measured_c, ea_j_per_mol=20000.0):
    R_gas = 8.314
    T_ref_k = 25.0 + 273.15
    T_meas_k = temp_measured_c + 273.15
    
    exponent = (ea_j_per_mol / R_gas) * (1.0 / T_meas_k - 1.0 / T_ref_k)
    factor = np.exp(exponent)
    return r_batt_measured / factor
```

**Kotlin** :
```kotlin
fun normalizeRBattTo25C(
    rBattMeasured: Double,
    tempMeasuredC: Double,
    eaJPerMol: Double = 20000.0
): Double {
    val rGas = 8.314
    val tRefK = 25.0 + 273.15
    val tMeasK = tempMeasuredC + 273.15
    
    val exponent = (eaJPerMol / rGas) * (1.0 / tMeasK - 1.0 / tRefK)
    val factor = exp(exponent)
    return rBattMeasured / factor
}
```

---

## 6. Tests

### 6.1 Tests unitaires Core

```kotlin
class ArrheniusNormalizerTest : FunSpec({
    
    test("normalizeRBattTo25C should return same value at 25°C") {
        val rMeasured = 0.050
        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, 25.0)
        result shouldBe rMeasured plusOrMinus 0.0001
    }
    
    test("normalizeRBattTo25C should decrease resistance for hot temperature") {
        val rMeasured = 0.080
        val result = ArrheniusNormalizer.normalizeRBattTo25C(rMeasured, 45.0)
        result shouldBeLessThan rMeasured
    }
})
```

### 6.2 Tests d'intégration

```kotlin
class FolderAnalyzerIntegrationTest : FunSpec({
    
    test("should analyze sample folder correctly") {
        val testFolder = Path("src/test/resources/sample_logs")
        val analyzer = FolderAnalyzer()
        
        val result = analyzer.analyzeFolder(testFolder)
        
        result.logs.size shouldBeGreaterThan 0
        result.nsGlobal shouldNotBe null
        result.reqBandLow shouldBeLessThan result.reqBandHigh
    }
})
```

---

## 7. Roadmap d'implémentation

### Phase 1 : Core Kotlin (2-3 semaines)
1. ✅ Setup projet multi-module Gradle
2. ✅ Modèles de données
3. ✅ Parsers CSV (EUC World + WheelLog)
4. ✅ ResistanceCalculator
5. ✅ ArrheniusNormalizer
6. ✅ CUSUMDetector
7. ✅ GaussianAlarmDetector
8. ✅ Tests unitaires complets

### Phase 2 : App Android (2-3 semaines)
1. ✅ Setup projet Android avec Compose
2. ✅ UI principale (navigation, screens)
3. ✅ Intégration Storage Access Framework
4. ✅ ViewModels + State management
5. ✅ Charts MPAndroidChart
6. ✅ Export PDF iText7
7. ✅ Tests UI (Compose test)

### Phase 3 : Desktop (1-2 semaines)
1. ✅ Setup Compose Desktop
2. ✅ Adaptation screens Android → Desktop
3. ✅ File chooser natif
4. ✅ Charts JFreeChart
5. ✅ Packaging (MSI, DMG, DEB)

### Phase 4 : Polish & Release
1. Documentation utilisateur
2. CI/CD (GitHub Actions)
3. Releases binaires
4. Play Store (Android)

---

## 8. Considérations techniques

### 8.1 Performance

**Parsing CSV** : 
- Utiliser `sequence {}` pour lazy loading des gros fichiers
- Coroutines pour parallélisation (1 fichier = 1 coroutine)

**Calculs intensifs** :
- Vectorisation avec `DoubleArray` natif
- Éviter les allocations inutiles dans les boucles

### 8.2 Gestion mémoire

**Mobile (Android)** :
- Limiter le nombre de points sur les charts (downsampling)
- Utiliser `Flow` pour streaming de données

**Desktop** :
- Plus permissif mais attention aux fichiers volumineux (>100 MB)

### 8.3 Internationalisation

```kotlin
// strings.xml (Android) ou properties (Desktop)
enum class Strings(val fr: String, val en: String) {
    CHOOSE_FOLDER("Choisis un dossier", "Choose a folder"),
    ANALYZE("Analyser", "Analyze"),
    // ...
}
```

---

## 9. Avantages de cette architecture

1. **Réutilisabilité** : Core Kotlin partagé = un seul codebase pour la logique
2. **Type-safety** : Kotlin élimine les erreurs runtime communes en Python
3. **Performance** : JVM optimisée, compilation native possible
4. **Maintenance** : Compose permet de partager l'UI Android/Desktop
5. **Distribution** : Apps standalone sans Python runtime
6. **Tests** : Écosystème de test Kotlin mature (Kotest, JUnit5)

---

## 10. Alternatives considérées

### 10.1 Kotlin Multiplatform Mobile (KMM)
- Avantage : Partage code avec iOS
- Inconvénient : Complexité accrue, pas d'intérêt ici (pas d'iOS prévu)

### 10.2 Web (Kotlin/JS)
- Avantage : Accessible via navigateur
- Inconvénient : Performance moindre, complexité backend/frontend

### 10.3 JavaFX pour Desktop
- Avantage : UI builder (Scene Builder)
- Inconvénient : Moins moderne que Compose, pas de partage avec Android

---

## Conclusion

Cette architecture offre :
- **Maximum de réutilisation** (core partagé)
- **UI native** sur chaque plateforme
- **Performance optimale**
- **Maintenance simplifiée**

Le choix de Compose Desktop est particulièrement pertinent car il permet de partager jusqu'à 80% du code UI avec Android tout en offrant une expérience native sur PC.
