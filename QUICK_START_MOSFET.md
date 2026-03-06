# Quick Start : Configuration MOSFET

✅ **100% opérationnel** - Guide ultra-rapide

---

## En 3 étapes

### 1️⃣ Configurer MOSFET pour ta roue (30 sec)

1. Ouvre app EUC SoH Analyzer
2. Dans liste roues, clique **⚙️** à droite de ta roue
3. Saisis **R_ds(on) @ 25°C** (voir tableau ci-dessous)
4. Clique **"Enregistrer"**
5. ✅ Badge **"MOSFET"** apparaît sur la carte

### 2️⃣ Analyser avec séparation R_batt (2-3 min)

1. Sélectionne la roue (badge "MOSFET" visible)
2. Clique **"Analyser [nom roue]"**
3. Après analyse, clique **"View Results"**
4. ✅ Colonnes **R_batt_median** et **R_mosfet_hot** présentes

### 3️⃣ Voir graphiques avec bandes couleur (1 min)

1. Clique **"View Charts"**
2. ✅ Chaque graphique affiche :
   - 🟢 **Bande verte** = Zone optimale (±1σ, 68%)
   - 🟠 **Bande orange** = Zone warning (±2σ, 95%)
   - 🔴 **Ligne rouge** = Danger (3σ, 99.7%)

**C'est tout !** Tu as maintenant la séparation R_batt/R_mosfet ET des graphiques diagnostic avancés.

---

## Valeurs R_ds(on) par modèle

| Roue | R_ds(on) @ 25°C |
|------|------------------|
| **Inmotion V11** | `0.0024` |
| **Inmotion V13** | `0.0020` |
| **Begode RS19** | `0.0012` |
| **Begode Master** | `0.0012` |
| **Begode EX.N** | `0.0015` |
| **KingSong S18** | `0.0035` |
| **KingSong S22** | `0.0025` |
| **Veteran Sherman** | `0.0028` |
| **Veteran Patton** | `0.0020` |

**Comment trouver pour autre modèle** :
1. Ouvre roue, lis référence MOSFET gravée (ex: "IPB039N10N5")
2. Google "[ref] datasheet"
3. Section "Electrical Characteristics" → R_DS(on) @ V_GS=10V, T=25°C
4. Si pont double (2 MOSFET/phase) : **x2** la valeur

**Coeff temp** : Toujours `0.01` (par défaut, correct pour 99% des cas)

---

## Interprétation résultats

### Colonnes nouvelles dans résultats

| Colonne | Signification | Valeur typique |
|---------|---------------|----------------|
| **R_batt_median** | Résistance batterie seule | 0.020-0.030 Ω |
| **R_mosfet_hot** | Résistance MOSFET @ T mesurée | 0.002-0.004 Ω |
| **Req_median** | Résistance totale (vérif) | R_batt + R_mosfet |

**Vérification** : `Req ≈ R_batt + R_mosfet` (tolérance ±10%)

### Graphiques diagnostic

**Bandes couleur** :
- 🟢 **Vert** : Roue en bon état, valeurs normales
- 🟠 **Orange** : Attention, valeurs élevées mais acceptables  
- 🔴 **Rouge** : Danger, dégradation significative (> 3σ)

**Exemple reqMedian** :
```
Tous points dans bande verte    → Roue neuve/excellente
Quelques points dans orange     → Roue usage normal
Points touchent ligne rouge     → Roue dégradée, surveiller
Points au-dessus ligne rouge    → Roue critique, maintenance urgente
```

---

## Différence Req vs R_batt

### Sans config MOSFET (mode classique)

**Mesure** : `Req` = Résistance équivalente totale  
**Limite** : Impossible de savoir si dégradation vient de batterie ou MOSFETs

```
Req augmente de 0.025 → 0.035 Ω
❓ Batterie dégradée ?
❓ MOSFETs défaillants ?
❓ Connexions oxydées ?
→ Diagnostic ambigu
```

### Avec config MOSFET (mode avancé)

**Mesure** : Séparation `R_batt` et `R_mosfet`  
**Avantage** : Diagnostic précis de la cause

```
Req augmente de 0.025 → 0.035 Ω

Si R_batt : 0.020 → 0.032 Ω (augmenté)
   R_mosfet : stable à 0.003 Ω
→ ✅ Batterie dégradée (cellules internes)

Si R_batt : stable à 0.022 Ω
   R_mosfet : 0.003 → 0.013 Ω (augmenté)
→ ✅ MOSFETs défaillants (surchauffe, vieillissement)

Si les deux augmentent proportionnellement
→ ✅ Connexions/câblage (R_wiring à revoir)
```

**Impact maintenance** :  
- R_batt augmente → Remplacement batterie nécessaire  
- R_mosfet augmente → Remplacement MOSFETs/controleur  
- Les deux → Révision complète connexions

---

## FAQ ultra-rapide

### Q : Config MOSFET obligatoire ?
**R** : Non. App fonctionne sans (mode Req classique). Mais pour diagnostic avancé, **fortement recommandé**.

### Q : Je connais pas ma valeur R_ds ?
**R** : Deux options :  
1. Ouvre roue, lis ref MOSFET, cherche datasheet  
2. Utilise valeur moyenne `0.0025` (acceptable pour diagnostic général)

### Q : Badge MOSFET ne s'affiche pas ?
**R** : Logcat : `adb logcat | grep SohViewModel`. Vérifie "MOSFET config saved". Si absent, souci persistence.

### Q : R_batt toujours null après analyse ?
**R** : Causes :
- Fichiers sans température (requis pour R_mosfet(T))  
- SoC invalide (requis pour V_batt)  
- Config MOSFET non passée au core (bug intégration)

### Q : Graphiques sans bandes couleur ?
**R** : Vérifie `ChartGalleryScreen.kt` ligne 21 : doit avoir `SohChartGeneratorFixed` (pas l'ancien).

### Q : Effacer config ?
**R** : Ouvre dialog ⚙️ → Bouton "Effacer" (en bas). Badge disparaît.

---

## Liens utiles

**Documentation complète** :
- `MOSFET_CONFIG_GUIDE.md` : Architecture + troubleshooting [cite:84]
- `LATEST_CHANGES.md` : Résumé modifications [cite:91]
- `BUILD_AND_TEST.md` : Instructions build + tests [cite:92]

**Build rapide** :
```bash
git pull origin main
./gradlew :euc-soh-android:assembleDebug
adb install -r euc-soh-android/build/outputs/apk/debug/euc-soh-android-debug.apk
```

**Logcat surveillance** :
```bash
# MOSFET config
adb logcat | grep -i mosfet

# Analyse R_batt
adb logcat | grep -E "(R_batt|separation)"
```

---

## Exemple concret

### Scénario : Inmotion V11 avec 2000 km

**1. Configuration** (30 sec)  
- Clique ⚙️ sur V11  
- Saisis R_ds = `0.0024`  
- Enregistre  
- Badge "MOSFET" apparaît

**2. Analyse** (2 min)  
- Sélectionne V11  
- Clique "Analyser"  
- Attend fin (50 fichiers → ~40 sec séquentiel)

**3. Résultats**  
```
Req_median    = 0.0268 Ω
R_mosfet_hot  = 0.0026 Ω (@ 45°C)
R_batt_median = 0.0242 Ω

Vérif : 0.0242 + 0.0026 = 0.0268 ✓
```

**4. Graphiques**  
- reqMedian : Tous points dans bande verte → ✅ Roue excellente  
- R_batt_median : Tendance plate → ✅ Batterie stable  
- tempBoardMax : Pics à 55°C mais sous seuil → ✅ Refroidissement OK

**5. Diagnostic** : Roue en très bon état, aucune action nécessaire.

---

## Tips avancés

### Export multiple roues

```bash
# Analyse 3 roues avec MOSFET, exporte chaque PDF
for wheel in V11 RS19 Sherman; do
  # (analyse manuelle dans app)
  # Exporte PDF
  # Répète
done

# Compare PDF côte-à-côte pour détecter patterns
```

### Suivi évolution

1. Analyse roue tous les 500 km
2. Exporte CSV résultats
3. Plot `wheel_km` vs `R_batt_median` dans Excel/Python
4. Tendance linéaire → Prédit durée vie batterie

### Multi-device sync

```bash
# Device 1 : Config MOSFET
# Exporte SharedPreferences
adb backup -f mosfet_configs.ab io.github.eucsoh.android

# Device 2 : Restore
adb restore mosfet_configs.ab

# Configs MOSFET dupliquées sur device 2
```

---

**Version** : 1.0  
**Date** : 6 mars 2026  
**Status** : ✅ Production ready  
**Temps lecture** : 3 minutes
