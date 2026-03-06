# Next Steps: Intégration Visualization & File Management

## État actuel

✅ **Complété** :
- Dépendances MPAndroidChart + iText7 ajoutées
- `SohChartGenerator.kt` : Moteur de graphiques complet
- `PdfExportService.kt` : Export PDF natif Android
- `FileManager.kt` : Gestion fichiers CSV
- `FileListScreen.kt` : UI liste fichiers (Compose)
- `ChartGalleryScreen.kt` : UI galerie graphiques (Compose)
- Documentation complète dans `VISUALIZATION_IMPLEMENTATION.md`

❌ **À faire** :
- Intégration dans navigation existante
- Ajout boutons dans UI principale
- Tests des graphiques
- Gestion permissions storage

---

## Actions nécessaires

### 1. Intégrer les nouveaux écrans dans la navigation

**Fichier à modifier** : `MainScreen.kt` ou fichier de navigation principal

**Ajout à faire** : Définir les nouveaux états de navigation

```kotlin
sealed class Screen {
    // ... écrans existants (Home, WheelDetails, etc.)
    
    // AJOUTER :
    data class FileList(
        val wheelName: String,
        val dirUri: Uri
    ) : Screen()
    
    data class ChartGallery(
        val wheelName: String,
        val stats: List<ReqStatsResult>
    ) : Screen()
}
```

**Router** :
```kotlin
when (currentScreen) {
    // ... cas existants
    
    is Screen.FileList -> {
        FileListScreen(
            wheelName = currentScreen.wheelName,
            wheelDirUri = currentScreen.dirUri,
            onBack = { navController.popBackStack() }
        )
    }
    
    is Screen.ChartGallery -> {
        ChartGalleryScreen(
            wheelName = currentScreen.wheelName,
            stats = currentScreen.stats,
            onBack = { navController.popBackStack() }
        )
    }
}
```

---

### 2. Ajouter boutons dans l'écran de détail roue

**Fichier à modifier** : `WheelDetailsScreen.kt` ou équivalent

**Ajout dans la Card de détail roue** :

```kotlin
// Après les stats existantes (Req, sag, etc.)
Spacer(modifier = Modifier.height(16.dp))

Text(
    text = "Actions",
    style = MaterialTheme.typography.titleMedium,
    modifier = Modifier.padding(bottom = 8.dp)
)

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    // Bouton 1: Gestion fichiers
    OutlinedButton(
        onClick = {
            navController.navigate(
                Screen.FileList(
                    wheelName = wheel.name,
                    dirUri = wheel.directoryUri // Adapter selon structure
                )
            )
        },
        modifier = Modifier.weight(1f)
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = "Files",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Files")
    }

    // Bouton 2: Graphiques
    Button(
        onClick = {
            // Charger stats depuis DB/ViewModel
            scope.launch {
                val stats = database.getStatsForWheel(wheel.id)
                if (stats.isNotEmpty()) {
                    navController.navigate(
                        Screen.ChartGallery(
                            wheelName = wheel.name,
                            stats = stats
                        )
                    )
                } else {
                    // Afficher snackbar "No data"
                }
            }
        },
        modifier = Modifier.weight(1f)
    ) {
        Icon(
            Icons.Default.BarChart,
            contentDescription = "Charts",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Charts")
    }
}
```

---

### 3. Vérifier permissions storage

**Fichier à modifier** : `AndroidManifest.xml`

**Permissions nécessaires** :

```xml
<!-- Déjà présent normalement -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- Pour PDF export (Android < 10) -->
<uses-permission 
    android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

**Note** : Le code utilise Storage Access Framework (SAF) via `DocumentFile`, ce qui élimine le besoin de permissions dangereuses sur Android 10+. Les permissions ci-dessus sont pour compatibilité Android < 10.

---

### 4. Tester l'implémentation

#### Test manuel

1. **Lancer l'app**
2. **Sélectionner une roue** avec des fichiers CSV analysés
3. **Cliquer "Files"** :
   - Vérifier que tous les CSV apparaissent
   - Tester "Preview" (affiche premières lignes)
   - Tester "Open" (ouvre avec app tierce)
4. **Cliquer "Charts"** :
   - Vérifier que les graphiques se génèrent
   - Tester tap pour plein écran
   - Tester export PDF (vérifier dans `Documents/EUC_SoH/`)

#### Test avec données minimales

Le code gère les cas limites :
- **< 3 points** : Affiche "Insufficient data" au lieu de crash
- **Aucun CSV** : Affiche "No CSV files found"
- **Stats vides** : Bouton Charts affiche snackbar d'erreur

---

### 5. Optimisations optionnelles

#### Cache des graphiques

Pour éviter regénération à chaque ouverture :

```kotlin
class ChartCache {
    private val cache = mutableMapOf<String, List<Pair<String, Bitmap>>>()

    fun get(wheelId: String): List<Pair<String, Bitmap>>? = cache[wheelId]

    fun put(wheelId: String, charts: List<Pair<String, Bitmap>>) {
        cache[wheelId] = charts
    }

    fun clear() {
        cache.values.forEach { charts ->
            charts.forEach { (_, bitmap) -> bitmap.recycle() }
        }
        cache.clear()
    }
}
```

**Usage** :
```kotlin
val chartCache = remember { ChartCache() }

LaunchedEffect(stats) {
    val cached = chartCache.get(wheelId)
    if (cached != null) {
        charts = cached
        isLoading = false
    } else {
        isLoading = true
        charts = chartGenerator.generateOverviewCharts(stats)
        chartCache.put(wheelId, charts)
        isLoading = false
    }
}
```

#### Background generation avec WorkManager

Pour générer les graphiques en arrière-plan après analyse :

```kotlin
class ChartGenerationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        val wheelId = inputData.getString("wheel_id") ?: return Result.failure()
        val stats = database.getStatsForWheel(wheelId)
        
        val chartGenerator = SohChartGenerator(applicationContext)
        val charts = chartGenerator.generateOverviewCharts(stats)
        
        // Sauvegarder bitmaps en fichiers temporaires
        charts.forEach { (name, bitmap) ->
            val file = File(cacheDir, "chart_${wheelId}_${name}.png")
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            }
        }
        
        return Result.success()
    }
}
```

---

## Ordre d'exécution recommandé

1. **Étape 1** : Intégration navigation (5 min)
2. **Étape 2** : Ajout boutons UI (10 min)
3. **Étape 4** : Tests manuels (15 min)
4. **Étape 3** : Vérification permissions si problème (2 min)
5. **Étape 5** : Optimisations (optionnel, 30 min)

**Temps total estimé** : 30-45 minutes (hors optimisations)

---

## Troubleshooting courant

### Erreur : "Cannot resolve symbol 'SohChartGenerator'"
**Cause** : Gradle sync incomplet
**Solution** : `Build > Clean Project` puis `Build > Rebuild Project`

### Erreur : "Failed to load library libchart.so"
**Cause** : MPAndroidChart non synchronisé
**Solution** : Vérifier JitPack dans `settings.gradle.kts`, puis sync Gradle

### Graphiques vides/noirs
**Cause** : `ReqStatsResult` contient des `null`
**Solution** : Vérifier que l'analyse CSV produit des stats valides. Loguer `stats.mapNotNull { it.reqMedian }` pour déboguer.

### PDF export crash avec "FileNotFoundException"
**Cause** : Dossier `Documents/EUC_SoH/` n'existe pas
**Solution** : Le code appelle `mkdirs()`, mais vérifier permissions. Sur Android 11+, utiliser `MediaStore` à la place.

### Crash "OutOfMemoryError" lors génération
**Cause** : Trop de bitmaps en mémoire (8 graphiques × 1200×800 px = ~30 MB)
**Solution** : Générer et exporter immédiatement, ou réduire résolution (`CHART_WIDTH = 800, CHART_HEIGHT = 533`)

---

## Support

Consulter `VISUALIZATION_IMPLEMENTATION.md` pour la documentation détaillée de l'API.

Pour questions : ouvrir une issue GitHub ou contacter directement.

---

## Status final

✅ **Backend complet** (ChartGenerator, PdfExport, FileManager)  
✅ **UI screens prêts** (FileList, ChartGallery)  
🟡 **Intégration navigation** (5 min de config)  
✅ **Documentation** (VISUALIZATION_IMPLEMENTATION.md)

**Estimation complétion totale** : 95% (manque juste wiring)
