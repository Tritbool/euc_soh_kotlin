# Visualization & File Management Implementation Guide

## Aperçu

Cette implémentation ajoute deux fonctionnalités majeures à EUC_SOH Android :

1. **Gestion des fichiers CSV** : Visualiser, filtrer, prévisualiser et ouvrir les fichiers CSV de chaque roue
2. **Génération de graphiques** : Reproduire les graphiques Python (`soh_core_en.py`) en natif Android avec MPAndroidChart

---

## Architecture

### Dépendances ajoutées

```gradle
// MPAndroidChart pour les graphiques natifs
implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

// iText7 pour l'export PDF
implementation("com.itextpdf:itext7-core:7.2.5")
```

**Repository JitPack** ajouté dans `settings.gradle.kts` pour MPAndroidChart.

---

## Modules créés

### 1. Visualization Package

#### `SohChartGenerator.kt`
**Rôle** : Génère les graphiques de métriques SoH avec bandes gaussiennes.

**Implémentation basée sur** :
- `plot_metric_gauss()` : Graphique simple métrique vs km avec bandes σ
- `plot_soh_overview_all()` : Vue d'ensemble multi-métriques
- Bandes de danger : vert (μ ± σ), orange (μ ± 1.5σ), rouge (μ ± 2σ)

**Métriques supportées** :
- `reqMedian` : Résistance équivalente médiane (Ω)
- `req95p` : Résistance équivalente 95e percentile (Ω)
- `sag95p` : Chute de tension 95e percentile (V)
- `sagMax` : Chute de tension maximale (V)
- `vMinStrong` : Tension minimale sous charge (V)
- `iMax` : Courant maximal (A)
- `i95p` : Courant 95e percentile (A)
- `tempBoardMax` : Température board max (°C)
- `tempMotorMax` : Température moteur max (°C)

**API** :
```kotlin
val chartGenerator = SohChartGenerator(context)

// Graphique unique
val bitmap: Bitmap = chartGenerator.generateMetricChart(
    stats = listOf(...),
    metricExtractor = { it.reqMedian },
    metricName = "reqMedian",
    higherIsBad = true
)

// Overview complet
val charts: List<Pair<String, Bitmap>> = 
    chartGenerator.generateOverviewCharts(stats)
```

#### `PdfExportService.kt`
**Rôle** : Exporter les graphiques vers PDF multi-pages.

**Fonctionnalités** :
- Format A4 paysage (842x595 px)
- Une métrique par page
- Titre automatique (nom roue + métrique)
- Sauvegarde dans `Documents/EUC_SoH/`

**API** :
```kotlin
val pdfExporter = PdfExportService(context)
val file: File = pdfExporter.exportToPdf(
    stats = listOf(...),
    wheelName = "Begode A2",
    outputFileName = null // Auto-généré
)
```

---

### 2. File Management

#### `FileManager.kt`
**Rôle** : Gérer les fichiers CSV d'une roue.

**Fonctionnalités** :
- Lister tous les CSV d'un dossier roue
- Prévisualiser les premières lignes
- Compter le nombre de lignes
- Support DocumentFile (Storage Access Framework)

**API** :
```kotlin
val fileManager = FileManager(context)

// Lister fichiers
val files: List<CsvFileInfo> = fileManager.listCsvFiles(wheelDirUri)

// Prévisualiser
val preview: List<String> = fileManager.previewCsv(fileUri, maxLines = 20)

// Compter lignes
val count: Int = fileManager.countLines(fileUri)
```

---

### 3. UI Screens (Jetpack Compose)

#### `FileListScreen.kt`
**Écran de gestion des fichiers CSV.**

**Fonctionnalités** :
- Liste tous les CSV avec métadonnées (nom, taille, date)
- Actions :
  - **Preview** : Dialog affichant premières lignes
  - **Open** : Intent Android pour ouvrir avec app tierce
- Tri par date (plus récent d'abord)

**Navigation** :
```kotlin
FileListScreen(
    wheelName = "Begode A2",
    wheelDirUri = Uri.parse("...")
    onBack = { /* retour */ }
)
```

#### `ChartGalleryScreen.kt`
**Galerie de graphiques SoH.**

**Fonctionnalités** :
- Affiche tous les graphiques générés
- Tap → Plein écran avec zoom
- Bouton export PDF (TopBar)
- Loading indicator pendant génération
- Snackbar pour feedback export

**Navigation** :
```kotlin
ChartGalleryScreen(
    wheelName = "Begode A2",
    stats = listOf(...), // ReqStatsResult
    onBack = { /* retour */ }
)
```

---

## Intégration dans l'app

### Étape 1 : Ajouter les écrans à la navigation

Dans `MainScreen.kt`, ajouter deux nouveaux états de navigation :

```kotlin
sealed class Screen {
    // ... écrans existants
    data class FileList(val wheelName: String, val dirUri: Uri) : Screen()
    data class ChartGallery(val wheelName: String, val stats: List<ReqStatsResult>) : Screen()
}
```

### Étape 2 : Ajouter boutons dans WheelDetailScreen

Dans la carte d'une roue (écran détail), ajouter deux boutons :

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceEvenly
) {
    Button(onClick = {
        navController.navigate(Screen.FileList(
            wheelName = wheel.name,
            dirUri = wheel.directoryUri
        ))
    }) {
        Icon(Icons.Default.Folder, "Files")
        Spacer(modifier = Modifier.width(8.dp))
        Text("Manage Files")
    }

    Button(onClick = {
        // Charger stats depuis DB
        val stats = database.getStatsForWheel(wheel.id)
        navController.navigate(Screen.ChartGallery(
            wheelName = wheel.name,
            stats = stats
        ))
    }) {
        Icon(Icons.Default.BarChart, "Charts")
        Spacer(modifier = Modifier.width(8.dp))
        Text("View Charts")
    }
}
```

### Étape 3 : Gérer la navigation

Dans le composant de navigation principal :

```kotlin
when (currentScreen) {
    is Screen.FileList -> {
        FileListScreen(
            wheelName = currentScreen.wheelName,
            wheelDirUri = currentScreen.dirUri,
            onBack = { /* pop */ }
        )
    }
    is Screen.ChartGallery -> {
        ChartGalleryScreen(
            wheelName = currentScreen.wheelName,
            stats = currentScreen.stats,
            onBack = { /* pop */ }
        )
    }
    // ... autres écrans
}
```

---

## Permissions Android

Ajouter dans `AndroidManifest.xml` :

```xml
<!-- Lecture fichiers externes -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- Écriture pour PDF export (Android < 10) -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />

<!-- Access storage pour SAF -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
    tools:ignore="ScopedStorage" />
```

**Note** : Utiliser Storage Access Framework (SAF) via `DocumentFile` élimine le besoin de permissions dangereuses sur Android 10+.

---

## Améliorations futures

### Graphiques manquants (du Python)

1. **CUSUM Detection** : Détection de changement de régime
2. **Trend Lines** : Régression linéaire avec pente
3. **Inflection Points** : Points de changement de pente (vert=stable, rouge=dégradation)
4. **R_batt vs R_mosfet** : Séparation des résistances si params MOSFET fournis
5. **Arrhenius Calibration** : Normalisation thermique avec E_a auto-calibré

### Optimisations

1. **Cache charts** : Stocker bitmaps en mémoire pour éviter regénération
2. **Background generation** : Générer charts dans WorkManager
3. **Compression PDF** : Réduire taille fichier (actuellement ~2-5 MB par roue)
4. **Export CSV stats** : Permettre export des stats calculés

### UX

1. **Swipe gestures** : Naviguer entre charts par swipe
2. **Zoom interactif** : Pinch-to-zoom sur graphiques
3. **Filtrage métrique** : Sélectionner quelles métriques afficher
4. **Comparaison roues** : Overlay de 2 roues sur même graphique

---

## Tests

### Test unitaire SohChartGenerator

```kotlin
@Test
fun `generate metric chart with valid data`() {
    val stats = listOf(
        ReqStatsResult(wheelKm = 100.0, reqMedian = 0.050, ...),
        ReqStatsResult(wheelKm = 200.0, reqMedian = 0.055, ...),
        ReqStatsResult(wheelKm = 300.0, reqMedian = 0.060, ...)
    )
    
    val bitmap = chartGenerator.generateMetricChart(
        stats, { it.reqMedian }, "reqMedian"
    )
    
    assertNotNull(bitmap)
    assertEquals(1200, bitmap.width)
    assertEquals(800, bitmap.height)
}
```

### Test intégration PDF

```kotlin
@Test
fun `export PDF with all metrics`() = runBlocking {
    val stats = loadTestStats()
    val file = pdfExporter.exportToPdf(stats, "TestWheel")
    
    assertTrue(file.exists())
    assertTrue(file.length() > 0)
}
```

---

## Troubleshooting

### Erreur : "JitPack not found"
**Solution** : Vérifier que `maven { url = uri("https://jitpack.io") }` est dans `settings.gradle.kts`.

### Erreur : "Insufficient data points"
**Solution** : Graphiques nécessitent minimum 3 points valides. Vérifier que `ReqStatsResult` contient des données.

### PDF export crash
**Solution** : Vérifier permissions Storage et que `Environment.DIRECTORY_DOCUMENTS` existe.

### Charts vides/blancs
**Solution** : MPAndroidChart requiert `measure()` et `layout()` avant `draw()`. Code implémente déjà.

---

## Références

- [MPAndroidChart Documentation](https://github.com/PhilJay/MPAndroidChart/wiki)
- [iText7 PDF Guide](https://kb.itextpdf.com/home/it7kb)
- [Android Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider)
- [Jetpack Compose Canvas](https://developer.android.com/jetpack/compose/graphics/draw/overview)

---

## Auteur

Implémentation basée sur `soh_core_en.py` par Tritbool.
Portage Android : Mars 2026.
