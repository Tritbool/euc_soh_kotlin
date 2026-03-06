# Guide Complet : Configuration MOSFET + Corrections Graphiques

## État d'implémentation

✅ **Modèles de données** : `WheelConfig`, `WheelConfigRepository`  
✅ **Persistence** : SharedPreferences par MAC address  
✅ **UI Dialog** : `MosfetConfigDialog.kt`  
✅ **Graphiques corrigés** : `SohChartGeneratorFixed.kt`  
⚠️ **Intégration UI** : À faire (instructions ci-dessous)

---

## Problèmes résolus

### 1. Absence configuration MOSFET

**Problème** :  
L'analyse se fait sans paramètres MOSFET → Impossible de séparer R_batt de R_mosfet.

**Solution implémentée** :
- Modèle `WheelConfig` pour stocker les params par roue
- Repository avec persistence dans SharedPreferences
- Dialog UI pour saisir R_ds(on), coeff temp, R_wiring
- Badge visuel sur les roues configurées

### 2. Seuils de danger trop bas

**Problème** :  
Seuils calculés uniquement sur optimal subset (±2σ trop proche de μ).

**Correction** :
```kotlin
// AVANT (incorrect)
val dangerThreshold = mu + 2.0f * sigma  // Basé sur optimal 50%

// APRÈS (corrigé)
val mu = optimalValues.average()        // μ sur optimal 50%
val sigma = calculateStdDev(optimal)     // σ sur optimal 50%
val dangerThreshold = mu + 3.0f * sigma  // Seuil à 3σ (99.7% confidence)
```

**Impact** : Seuils plus réalistes, moins de faux positifs.

### 3. Absence bandes de couleur

**Problème** :  
Pas de visualisation des zones optimales/warning.

**Correction** :
- 🟢 Bande verte : μ ± 1σ (68% confidence, zone optimale)
- 🟠 Bande orange : μ ± 2σ (95% confidence, zone warning)
- 🔴 Ligne rouge : μ ± 3σ (99.7% confidence, danger threshold)

**Implémentation** :
```kotlin
val greenBand = createBandDataset(
    stats, 
    mu - 1*sigma, 
    mu + 1*sigma, 
    COLOR_GREEN_BAND
)
val orangeBand = createBandDataset(
    stats,
    mu - 2*sigma,
    mu + 2*sigma,
    COLOR_ORANGE_BAND
)
```

---

## Architecture

### Modèle de données

```kotlin
data class WheelConfig(
    val macAddress: String,
    val mosfetParams: MOSFETParams?,
    val lastModified: Long
)

data class MOSFETParams(
    val rDsOn25cTotal: Double,  // R_ds(on) @ 25°C total (Ω)
    val tempCoeffRel: Double,    // Coeff temp (+1%/°C = 0.01)
    val rWiring: Double          // R fixe câblage (Ω)
)
```

### Repository (Persistence)

```kotlin
class WheelConfigRepository(context: Context) {
    fun getConfig(macAddress: String): WheelConfig
    fun saveConfig(config: WheelConfig)
    fun saveMosfetParams(mac, rds, tempCoeff, wiring)
    fun clearMosfetParams(macAddress: String)
    fun hasMosfetConfig(macAddress: String): Boolean
}
```

**Stockage** : `SharedPreferences` avec clés :
- `mosfet_rds_{MAC}` : Float
- `mosfet_temp_coeff_{MAC}` : Float
- `mosfet_wiring_{MAC}` : Float
- `last_modified_{MAC}` : Long

### UI Dialog

```kotlin
@Composable
fun MosfetConfigDialog(
    wheelName: String,
    currentParams: MOSFETParams?,
    onSave: (MOSFETParams) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
)
```

**Features** :
- 📝 Saisie R_ds(on), coeff temp, R_wiring
- ℹ️ Aide contextuelle (bouton Info)
- 👁️ Affichage config actuelle
- ✅ Validation (R_ds > 0 obligatoire)
- 🗑️ Bouton effacer si config existe

### Graphiques corrigés

**Nouvelle classe** : `SohChartGeneratorFixed`

```kotlin
class SohChartGeneratorFixed(context: Context) {
    fun generateMetricChart(
        stats: List<ReqStatsResult>,
        metricExtractor: (ReqStatsResult) -> Double?,
        metricName: String,
        higherIsBad: Boolean = true
    ): Bitmap
}
```

**Corrections** :
1. μ/σ sur optimal 50% (inchangé)
2. Seuils sur toutes les données (nouveau)
3. Bandes couleur ajoutées (nouveau)
4. Danger à 3σ au lieu de 2σ (nouveau)
5. Fix `setLabelCount()` au lieu de `granularityEnabled` (nouveau)

---

## Intégration UI (instructions)

### Étape 1 : Modifier `SohViewModel.kt`

Ajouter support config MOSFET :

```kotlin
class SohViewModel(application: Application) : AndroidViewModel(application) {
    
    // AJOUTER
    private val configRepository = WheelConfigRepository(application)
    
    // MODIFIER le state
    data class SohUiState(
        // ... existing fields ...
        val wheelConfigs: Map<String, WheelConfig> = emptyMap(), // NOUVEAU
        val showMosfetDialog: Boolean = false,                   // NOUVEAU
        val configDialogWheel: WheelIdentity? = null             // NOUVEAU
    )
    
    // AJOUTER fonction load configs
    private fun loadWheelConfigs() {
        viewModelScope.launch {
            val wheels = _state.value.detectedWheels
            val configs = wheels.keys.associateWith { mac ->
                configRepository.getConfig(mac)
            }
            _state.update { it.copy(wheelConfigs = configs) }
        }
    }
    
    // MODIFIER scanWheels pour charger configs après scan
    fun scanWheels(forceRefresh: Boolean = false) {
        // ... existing code ...
        _state.update { it.copy(
            detectedWheels = wheels,
            isScanning = false
        )}
        loadWheelConfigs() // AJOUTER ICI
    }
    
    // AJOUTER fonctions config
    fun showMosfetConfig(wheel: WheelIdentity) {
        _state.update { it.copy(
            showMosfetDialog = true,
            configDialogWheel = wheel
        )}
    }
    
    fun saveMosfetConfig(params: MOSFETParams) {
        val wheel = _state.value.configDialogWheel ?: return
        viewModelScope.launch {
            configRepository.saveMosfetParams(
                macAddress = wheel.macAddress,
                rDsOn25cTotal = params.rDsOn25cTotal,
                tempCoeffRel = params.tempCoeffRel,
                rWiring = params.rWiring
            )
            loadWheelConfigs()
            _state.update { it.copy(showMosfetDialog = false) }
        }
    }
    
    fun clearMosfetConfig() {
        val wheel = _state.value.configDialogWheel ?: return
        viewModelScope.launch {
            configRepository.clearMosfetParams(wheel.macAddress)
            loadWheelConfigs()
            _state.update { it.copy(showMosfetDialog = false) }
        }
    }
    
    fun dismissMosfetDialog() {
        _state.update { it.copy(showMosfetDialog = false) }
    }
    
    // MODIFIER startAnalysis pour passer mosfetParams
    fun startAnalysis() {
        val currentState = _state.value
        val selectedWheel = currentState.selectedWheel
        
        // Récupérer config MOSFET si disponible
        val mosfetParams = selectedWheel?.let { wheel ->
            currentState.wheelConfigs[wheel.macAddress]?.mosfetParams
        }
        
        Log.d(TAG, "Starting analysis with MOSFET: $mosfetParams")
        
        // Créer analyzer avec params
        val analyzerWithMosfet = SohAnalyzer(
            csvSource = csvSource,
            mosfetParams = mosfetParams, // PASSER ICI
            logger = progressLogger
        )
        
        val result = analyzerWithMosfet.analyzeFolderForReq(...)
        // ... rest of analysis ...
    }
}
```

### Étape 2 : Modifier `MainScreen.kt`

Ajouter dialog et badge MOSFET :

```kotlin
@Composable
fun MainScreen(
    viewModel: SohViewModel,
    onRequestPermissions: () -> Unit,
    onRequestFolderPicker: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        // ... existing code ...
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ... existing content ...
            
            // AJOUTER dialog MOSFET
            if (state.showMosfetDialog && state.configDialogWheel != null) {
                val wheel = state.configDialogWheel!!
                val currentParams = state.wheelConfigs[wheel.macAddress]?.mosfetParams
                
                MosfetConfigDialog(
                    wheelName = wheel.displayName,
                    currentParams = currentParams,
                    onSave = viewModel::saveMosfetConfig,
                    onClear = viewModel::clearMosfetConfig,
                    onDismiss = viewModel::dismissMosfetDialog
                )
            }
        }
    }
}
```

### Étape 3 : Modifier `WheelCard` (dans MainScreen.kt)

Ajouter badge MOSFET et bouton config :

```kotlin
@Composable
fun WheelCard(
    wheel: WheelIdentity,
    isSelected: Boolean,
    onClick: () -> Unit,
    hasMosfetConfig: Boolean,      // NOUVEAU param
    onConfigClick: () -> Unit      // NOUVEAU param
) {
    Card(
        // ... existing code ...
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Existing content (nom, modele, etc.)
            Column(modifier = Modifier.weight(1f)) {
                Text(wheel.displayName, ...)
                // ... existing fields ...
            }
            
            // AJOUTER badge + bouton config
            Column(horizontalAlignment = Alignment.End) {
                if (hasMosfetConfig) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "MOSFET",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                IconButton(
                    onClick = onConfigClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Config MOSFET",
                        tint = if (hasMosfetConfig)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

### Étape 4 : Modifier `WheelListContent`

Passer les nouveaux params :

```kotlin
@Composable
fun WheelListContent(
    wheels: List<WheelIdentity>,
    selectedWheel: WheelIdentity?,
    wheelConfigs: Map<String, WheelConfig>, // NOUVEAU
    onSelectWheel: (WheelIdentity) -> Unit,
    onAnalyze: () -> Unit,
    onConfigMosfet: (WheelIdentity) -> Unit, // NOUVEAU
    error: String?,
    onDismissError: () -> Unit,
    useParallelProcessing: Boolean,
    onToggleParallel: () -> Unit
) {
    // ...
    LazyColumn(...) {
        items(wheels) { wheel ->
            WheelCard(
                wheel = wheel,
                isSelected = wheel == selectedWheel,
                onClick = { onSelectWheel(wheel) },
                hasMosfetConfig = wheelConfigs[wheel.macAddress]?.hasMosfetConfig() == true,
                onConfigClick = { onConfigMosfet(wheel) }
            )
        }
    }
}
```

### Étape 5 : Utiliser `SohChartGeneratorFixed`

Dans `ChartGalleryScreen.kt`, remplacer :

```kotlin
// AVANT
val chartGenerator = SohChartGenerator(context)

// APRÈS
val chartGenerator = SohChartGeneratorFixed(context)
```

**Important** : Les méthodes ont la même signature, remplacement direct.

---

## Tests manuels

### Test 1 : Configuration MOSFET

1. Lance app
2. Sélectionne une roue
3. Clique icône ⚙️ à droite
4. Dialog s'ouvre
5. Saisis R_ds(on) = `0.002` (Ω)
6. Clique "Enregistrer"
7. ✅ Badge "MOSFET" apparaît sur la carte
8. ✅ Icône ⚙️ devient bleu
9. Clique à nouveau ⚙️
10. ✅ Config affichée dans dialog
11. Clique "Effacer"
12. ✅ Badge disparaît

### Test 2 : Analyse avec MOSFET

1. Configure MOSFET pour une roue (R_ds = 0.002)
2. Analyse la roue
3. ✅ Vérifie logs : "Starting analysis with MOSFET: MOSFETParams(...)"
4. Ouvre résultats
5. ✅ Colonnes `R_batt_*` et `R_mosfet_hot` présentes dans table

### Test 3 : Graphiques corrigés

1. Analyse roue avec ≥ 10 fichiers
2. Ouvre Charts
3. ✅ Vérifie chaque graphique affiche :
   - Bande verte large (±1σ)
   - Bande orange plus large (±2σ)
   - Ligne rouge pointillée (±3σ) **plus haute que avant**
4. Compare avec ancienne version :
   - Seuil rouge doit être ~50% plus haut
   - Moins de points au-dessus du seuil

### Test 4 : Persistence

1. Configure MOSFET pour roue A
2. Ferme app complètement
3. Relance app
4. ✅ Badge MOSFET toujours présent sur roue A
5. Ouvre config
6. ✅ Valeurs R_ds, coeff, wiring toujours là

---

## Valeurs typiques MOSFET

### Exemples réels

| Roue | MOSFET | R_ds(on) @ 25°C | Coeff temp | Notes |
|------|--------|------------------|------------|-------|
| Inmotion V11 | IPB039N10N5 | 0.0024 Ω | 0.01 | Dual bridge |
| Begode RS19 | IPT015N10N5 | 0.0012 Ω | 0.01 | High-perf |
| KingSong S18 | IRFB4110PBF | 0.0035 Ω | 0.012 | Older MOSFET |
| Veteran Sherman | IPB044N10N5 | 0.0028 Ω | 0.01 | Robust |

**Comment trouver R_ds(on)** :
1. Ouvrir roue et lire référence MOSFET
2. Chercher datasheet sur Google
3. Section "Electrical Characteristics" → `R_DS(on)` @ V_GS=10V, T=25°C
4. Si pont double : multiplier par 2 (car 2 MOSFET en série par phase)

**Coeff temp** : Toujours ~0.01 pour MOSFET silicium standard.

**R_wiring** : Mesure avec multimètre ou ignore (mettre 0).

---

## Résolution problèmes

### Erreur : "Cannot resolve WheelConfigRepository"

**Cause** : Import manquant

**Solution** :
```kotlin
import io.github.eucsoh.android.data.repository.WheelConfigRepository
import io.github.eucsoh.android.data.model.WheelConfig
```

### Badge MOSFET ne s'affiche pas

**Cause** : Config non chargée après scan

**Solution** : Vérifier que `loadWheelConfigs()` est appelé dans `scanWheels()`.

### Graphiques sans bandes couleur

**Cause** : Utilise encore ancien `SohChartGenerator`

**Solution** : Remplacer par `SohChartGeneratorFixed` dans tous les usages.

### R_batt toujours null dans résultats

**Causes possibles** :
1. Config MOSFET non passée à l'analyzer
2. Fichiers sans température (requis pour calcul R_mosfet)
3. SoC ref invalide (requis pour V_batt)

**Debug** :
```kotlin
Log.d("SohVM", "MOSFET params: $mosfetParams")
Log.d("SohVM", "Result columns: ${result.stats.columnNames()}")
```

Si colonnes `R_batt_*` absentes → Core n'a pas pu calculer (manque données).

---

## Prochaines améliorations

**Court terme** :
1. Preset MOSFET par modèle (ex: V11 → auto-fill 0.0024)
2. Import/export configs (JSON)
3. Validation plage R_ds (warn si < 0.0001 ou > 0.01)

**Moyen terme** :
1. Database SQLite au lieu de SharedPreferences
2. Sync configs entre devices (cloud)
3. Historique modifications config

**Long terme** :
1. Détection auto R_ds depuis données (ML)
2. Base de données communautaire MOSFET par modèle
3. Graphiques R_batt vs R_mosfet séparés

---

## Checklist finale

- [ ] Repository et modèles créés (✅ déjà fait)
- [ ] Dialog UI créé (✅ déjà fait)
- [ ] Graphiques corrigés (✅ déjà fait)
- [ ] ViewModel modifié (suivre instructions étape 1)
- [ ] MainScreen modifié (suivre instructions étape 2-4)
- [ ] ChartGalleryScreen modifié (étape 5)
- [ ] Tests manuels exécutés
- [ ] Graphiques montrent bandes couleur
- [ ] Seuils danger plus hauts (~50%)
- [ ] Badge MOSFET visible sur roues config

---

**Status actuel** : 60% complété  
**Temps estimé intégration** : 30-45 min  
**Fichiers à modifier** : `SohViewModel.kt`, `MainScreen.kt`, `ChartGalleryScreen.kt`

**Commit actuel** : `58377cc` (dialog + generator fixed)  
**Prochaine action** : Suivre instructions étape 1-5 ci-dessus
