# Guide de test - Fonctionnalités de visualisation

## État d'intégration

✅ **Complété** :
- Backend complet (SohChartGenerator, PdfExportService, FileManager)
- UI screens (FileListScreen, ChartGalleryScreen, ResultsScreenEnhanced)
- Intégration dans MainScreen.kt
- Modèles de données (ReqStatsResult, CsvFileInfo)
- Dépendances (MPAndroidChart, iText7)

**Prêt à compiler et tester !**

---

## Pré-requis

### 1. Synchroniser Gradle

```bash
./gradlew clean build
```

Si erreur JitPack, vérifier que `settings.gradle.kts` contient :
```kotlin
maven { url = uri("https://jitpack.io") }
```

### 2. Vérifier permissions

Dans `AndroidManifest.xml` :
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28" />
```

---

## Scénarios de test

### Test 1 : Analyse basique

**Objectif** : Vérifier que les nouveaux boutons apparaissent après analyse.

**Étapes** :
1. Lancer l'app
2. Sélectionner une roue avec plusieurs fichiers CSV (≥ 3)
3. Cliquer "Analyser"
4. ✅ Attendre fin d'analyse
5. ✅ Vérifier que l'écran résultats affiche 2 boutons :
   - `Files` (outlined)
   - `Charts (N)` où N = nombre de fichiers analysés

**Résultat attendu** :
- Bouton `Charts` activé si N ≥ 3
- Bouton `Charts` grisé si N < 3 (avec message d'avertissement)

---

### Test 2 : Gestion des fichiers

**Objectif** : Tester FileListScreen.

**Étapes** :
1. Depuis l'écran résultats, cliquer `Files`
2. ✅ Vérifier que la liste des CSV apparaît
3. ✅ Chaque fichier doit afficher :
   - Nom
   - Taille (KB)
   - Icône verte (✓) si valide
4. Cliquer sur un fichier
5. ✅ Dialog "Preview" s'ouvre avec premières lignes
6. Fermer dialog
7. Cliquer bouton "Open"
8. ✅ Chooser Android apparaît ("Open CSV")
9. Retour avec bouton Back
10. ✅ Retourne à l'écran résultats

**Résultat attendu** :
- Pas de crash
- Preview affiche contenu lisible
- Open permet sélection app externe

---

### Test 3 : Graphiques (cas nominal)

**Objectif** : Tester génération et affichage graphiques.

**Étapes** :
1. Depuis écran résultats (≥ 3 fichiers), cliquer `Charts`
2. ✅ Loading indicator pendant génération (~1-2s)
3. ✅ Liste de graphiques apparaît (jusqu'à 8) :
   - Req median
   - Req 95p
   - Sag 95p
   - Sag max
   - V min strong
   - I max
   - I 95p
   - Temp board/motor (si disponible)
4. ✅ Chaque graphique doit montrer :
   - Points bleus (données)
   - Bandes vertes (μ ± σ)
   - Ligne rouge pointillée (seuil danger)
5. Cliquer sur un graphique
6. ✅ S'ouvre en plein écran
7. Fermer avec Back
8. ✅ Retourne à la galerie

**Résultat attendu** :
- Graphiques clairs et lisibles
- Bandes gaussiennes visibles
- Pas de graphiques blancs/vides

---

### Test 4 : Export PDF

**Objectif** : Tester export PDF multi-pages.

**Étapes** :
1. Depuis ChartGalleryScreen, cliquer icône PDF (TopBar)
2. ✅ Loading indicator pendant export
3. ✅ Snackbar confirme : "PDF saved: /path/to/file.pdf"
4. Ouvrir gestionnaire fichiers Android
5. Naviguer vers `Documents/EUC_SoH/`
6. ✅ Fichier PDF existe avec nom `{Wheel}_SoH_{timestamp}.pdf`
7. Ouvrir PDF
8. ✅ Contient une page par métrique
9. ✅ Chaque page a titre + graphique centré

**Résultat attendu** :
- PDF créé sans crash
- Taille fichier raisonnable (< 5 MB)
- Graphiques haute qualité (pas pixelisés)

---

### Test 5 : Cas limite - Données insuffisantes

**Objectif** : Vérifier comportement avec < 3 points.

**Étapes** :
1. Analyser une roue avec seulement 1-2 fichiers CSV
2. ✅ Écran résultats s'affiche normalement
3. ✅ Bouton `Charts` est grisé (disabled)
4. ✅ Message d'avertissement : "Need at least 3 data points"
5. Cliquer `Charts` (ne fait rien car disabled)

**Résultat attendu** :
- Pas de crash
- UI indique clairement pourquoi charts indisponibles

---

### Test 6 : Retour en arrière (navigation)

**Objectif** : Vérifier que tous les Back fonctionnent.

**Étapes** :
1. Roue → Analyser → Résultats → Charts
2. Appuyer Back
3. ✅ Retourne à Résultats (pas WheelList)
4. Cliquer Files
5. Appuyer Back
6. ✅ Retourne à Résultats
7. Appuyer Back (bouton Retour)
8. ✅ Retourne à WheelList

**Résultat attendu** :
- Navigation cohérente
- Pas de stack loop
- Bouton système Back fonctionne aussi

---

## Tests de performance

### Test 7 : Génération de graphiques (roue avec 20+ fichiers)

**Mesure** :
- Temps génération : < 2 secondes pour 8 graphiques
- Mémoire utilisée : < 50 MB (utiliser Android Profiler)
- Pas de lag UI pendant génération

**Si lent** :
- Réduire `CHART_WIDTH` et `CHART_HEIGHT` dans `SohChartGenerator`
- Activer cache (voir NEXT_STEPS.md)

### Test 8 : Export PDF (multiple fois)

**Mesure** :
- Export 1 : ~1.5s
- Export 2 : ~1.5s (même perfs)
- Taille fichier : 2-4 MB par roue

**Si problèmes mémoire** :
- Ajouter `bitmap.recycle()` après export
- Compresser PNG avant embed dans PDF

---

## Logs de débogage

### Activer logs verbeux

Ajouter dans code temporairement :

```kotlin
// Dans SohChartGenerator.generateMetricChart()
Log.d("SohChart", "Generating $metricName with ${stats.size} points")
Log.d("SohChart", "Optimal sample: ${optimal.size}, μ=$mean, σ=$std")

// Dans PdfExportService.exportToPdf()
Log.d("PdfExport", "Starting export for ${stats.size} metrics")
Log.d("PdfExport", "PDF saved to: ${outputFile.absolutePath}")
```

### Vérifier avec Logcat

```bash
adb logcat | grep -E "(SohChart|PdfExport|FileManager)"
```

---

## Résolution d'erreurs courantes

### Erreur : "Cannot resolve MPAndroidChart"

**Cause** : Gradle sync incomplet

**Solution** :
```bash
./gradlew clean
./gradlew --refresh-dependencies
./gradlew build
```

Si persiste, vérifier `settings.gradle.kts` contient JitPack.

### Erreur : "FileNotFoundException" lors PDF export

**Cause** : Permissions storage manquantes

**Solution** :
1. Vérifier `AndroidManifest.xml` (voir Pré-requis)
2. Sur Android 11+, demander permissions runtime :
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
    intent.data = Uri.parse("package:${context.packageName}")
    context.startActivity(intent)
}
```

### Erreur : "Insufficient data points" alors que ≥ 3 fichiers

**Cause** : Les fichiers ne contiennent pas de données valides (reqMedian = null)

**Solution** :
1. Vérifier que l'analyse core fonctionne (table résultats affiche des valeurs)
2. Vérifier dans logs si `ReqStatsResult` contient des `null`
3. Si tous null : problème dans conversion `row["Req_median"]` dans `MainScreenEnhanced.kt`

### Graphiques blancs/noirs

**Cause** : `measure()` et `layout()` non appelés avant `draw()`

**Solution** : Déjà implémenté dans `SohChartGenerator.captureChartAsBitmap()`. Si persiste :
```kotlin
chart.measure(
    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
)
chart.layout(0, 0, width, height)
chart.invalidate() // AJOUTER CETTE LIGNE
```

### OutOfMemoryError

**Cause** : Trop de bitmaps en mémoire simultanément

**Solution** :
1. Réduire résolution graphiques :
```kotlin
companion object {
    private const val CHART_WIDTH = 800  // Au lieu de 1200
    private const val CHART_HEIGHT = 533 // Au lieu de 800
}
```

2. Recycler bitmaps immédiatement après usage :
```kotlin
charts.forEach { (_, bitmap) ->
    bitmap.recycle()
}
```

---

## Checklist de validation finale

- [ ] App compile sans erreurs
- [ ] Tous les imports résolus
- [ ] Test 1 : Boutons apparaissent après analyse
- [ ] Test 2 : Files list fonctionne
- [ ] Test 3 : Charts s'affichent correctement
- [ ] Test 4 : PDF export crée fichier valide
- [ ] Test 5 : Cas < 3 points géré proprement
- [ ] Test 6 : Navigation Back cohérente
- [ ] Test 7 : Performances acceptables (< 2s)
- [ ] Test 8 : Pas de memory leak (profiler)
- [ ] Pas de crash sur aucun test
- [ ] Logs propres (pas d'erreurs rouges)

---

## Prochaines étapes (si tout fonctionne)

1. **Optimisation cache** : Éviter regénération à chaque ouverture (voir NEXT_STEPS.md)
2. **Graphiques avancés** : CUSUM, trend lines, inflection points
3. **Export CSV** : Exporter stats calculés en CSV
4. **Comparaison roues** : Overlay de 2 roues sur même graphique
5. **Swipe gestures** : Naviguer entre charts par swipe

---

## Support

Si blocage :
1. Vérifier cette checklist
2. Lire VISUALIZATION_IMPLEMENTATION.md pour détails API
3. Consulter NEXT_STEPS.md pour intégration
4. Ouvrir issue GitHub avec :
   - Logs Logcat complets
   - Version Android device
   - Étapes de reproduction exactes

---

**Temps total estimé pour tests complets** : 20-30 minutes

**Status actuel** : ✅ Prêt à tester
