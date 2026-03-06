# EUC SoH - Kotlin

✅ **Production Ready** - Android app opérationnelle avec analyse avancée

Kotlin reimplementation of [EUC_SOH](https://github.com/Tritbool/EUC_SOH) for battery State of Health monitoring.

## 🆕 Nouveautés (Mars 2026)

### Configuration MOSFET par roue
- ⚙️ Interface de configuration persistante (SharedPreferences)
- 🔋 Séparation R_batterie / R_mosfet dans l'analyse
- 🏷️ Badge visuel sur roues configurées
- 📊 Colonnes R_batt_median et R_mosfet_hot dans résultats

### Graphiques diagnostics avancés
- 🟢 Bande verte : Zone optimale (±1σ, 68% confidence)
- 🟠 Bande orange : Zone warning (±2σ, 95% confidence)
- 🔴 Ligne rouge : Danger (3σ, 99.7% confidence)
- ✅ Seuils corrigés (plus de faux positifs)

**Guides rapides** :
- 🚀 **Démarrage** : [QUICK_START_MOSFET.md](QUICK_START_MOSFET.md) (3 min) [cite:93]
- 🛠️ **Build & Test** : [BUILD_AND_TEST.md](BUILD_AND_TEST.md) (15 min) [cite:92]
- 📚 **Guide complet** : [MOSFET_CONFIG_GUIDE.md](MOSFET_CONFIG_GUIDE.md) (30 min) [cite:84]

---

## Architecture

### Modules

- **euc-soh-core**: Pure Kotlin business logic with DataFrame
  - Analyse Arrhenius avec normalisation température
  - Détection anomalies (Gaussian, CUSUM, Trend)
  - Support MOSFET params pour séparation R_batt
  
- **euc-soh-android**: Android app (API 26+)
  - Scan automatique WheelLog/EUC World
  - Configuration MOSFET persistante
  - Visualisation graphiques avec bandes diagnostics
  - Export PDF/CSV
  
- **euc-soh-desktop**: Desktop JVM app
  - CLI pour analyses batch
  - Export rapports automatiques

### Tech Stack

- Kotlin 1.9+ / Coroutines
- Jetpack Compose (Android UI)
- Kotlin DataFrame (data processing)
- MPAndroidChart (graphiques)
- SharedPreferences (persistence)

---

## Quick Start Android

### Installation

```bash
git clone https://github.com/Tritbool/euc_soh_kotlin.git
cd euc_soh_kotlin
./gradlew :euc-soh-android:assembleDebug
adb install euc-soh-android/build/outputs/apk/debug/euc-soh-android-debug.apk
```

### Utilisation

1. **Scan roues** : App détecte automatiquement dossiers WheelLog/EUC World
2. **Config MOSFET** (optionnel) : Clique ⚙️ sur une roue, saisis R_ds(on)
3. **Analyse** : Sélectionne roue, clique "Analyser"
4. **Résultats** : Table CSV + graphiques diagnostics + export PDF

**Exemple valeurs MOSFET** :
- Inmotion V11 : `0.0024` Ω
- Begode RS19 : `0.0012` Ω
- KingSong S18 : `0.0035` Ω

Voir [QUICK_START_MOSFET.md](QUICK_START_MOSFET.md) pour guide complet. [cite:93]

---

## Features

### Analyse core

- ✅ Multi-pass analysis (calibration Ea + final)
- ✅ Arrhenius normalization (temp compensation)
- ✅ Pack inference (Ns, Vnom, Rpack)
- ✅ Gaussian alarm detection (2σ threshold)
- ✅ CUSUM regime change detection
- ✅ Linear trend analysis
- ✅ **MOSFET separation** (R_batt vs R_mosfet)

### Android app

- ✅ Recursive wheel detection (WheelLog/EUC World)
- ✅ Parallel/sequential analysis modes
- ✅ **MOSFET config UI** (persistent per wheel)
- ✅ **Advanced charts** (color bands, corrected thresholds)
- ✅ Progress tracking (file-by-file)
- ✅ CSV/PDF export
- ✅ Material 3 Design

### Graphiques

- ✅ 8 metrics : Req, Sag, Vmin, Imax, Temp, etc.
- ✅ **Bandes gaussiennes** : vert (±1σ), orange (±2σ), rouge (3σ)
- ✅ Danger thresholds (99.7% confidence)
- ✅ Interactive zoom/fullscreen
- ✅ PDF batch export

---

## Status modules

| Module | Status | Features |
|--------|--------|----------|
| **euc-soh-core** | ✅ Stable | Analysis, detection, MOSFET support |
| **euc-soh-android** | ✅ Production | Full UI, config MOSFET, charts fixed |
| **euc-soh-desktop** | 🚧 WIP | CLI analysis, batch processing |

---

## Documentation

### Guides utilisateur

- 🚀 [**QUICK_START_MOSFET.md**](QUICK_START_MOSFET.md) : Guide 3 minutes config MOSFET [cite:93]
- 🛠️ [**BUILD_AND_TEST.md**](BUILD_AND_TEST.md) : Instructions build + tests validation [cite:92]
- 📊 [**LATEST_CHANGES.md**](LATEST_CHANGES.md) : Résumé modifications Mars 2026 [cite:91]

### Guides développeur

- 📚 [**MOSFET_CONFIG_GUIDE.md**](MOSFET_CONFIG_GUIDE.md) : Architecture config MOSFET complète [cite:84]
- 🏛️ [**VISUALIZATION_IMPLEMENTATION.md**](VISUALIZATION_IMPLEMENTATION.md) : Architecture graphiques
- ⚙️ [**TESTING_GUIDE.md**](TESTING_GUIDE.md) : Scénarios test

---

## Exemple analyse avec MOSFET

### Input

```
WheelLog/[MAC]/
  ├── 20250101_ride1.csv
  ├── 20250102_ride2.csv
  └── ...

Config MOSFET (SharedPreferences):
  R_ds(on) = 0.0024 Ω
  Coeff temp = 0.01
```

### Output

```csv
file,wheel_km,Req_median,R_batt_median,R_mosfet_hot,temp_board_max
ride1,1250.5,0.0268,0.0242,0.0026,45.2
ride2,1275.3,0.0270,0.0244,0.0026,46.8
...
```

**Diagnostic** :
- `R_batt` stable → ✅ Batterie saine
- `R_mosfet` stable → ✅ MOSFETs OK
- Graphiques bande verte → ✅ Roue excellente

---

## Commits récents

| Date | Feature | Commits |
|------|---------|----------|
| 2026-03-06 | MOSFET config + charts fixes | `a451a1e`, `58377cc`, `e21fbcc`, `bcde4db`, `09c61f4` [cite:81][cite:82][cite:86][cite:88][cite:90] |
| 2026-03-06 | Documentation complète | `5f3d2c4`, `b256bff`, `65d4341`, `6a5d923` [cite:84][cite:91][cite:92][cite:93] |

**Total ajouté** : ~2300 lignes (code + docs)

---

## Roadmap

### Court terme (2026 Q2)
- [ ] Tests unités config repository
- [ ] UI tests instrumented (Compose)
- [ ] Preset MOSFET par modèle connu
- [ ] Import/export configs (JSON)

### Moyen terme (2026 Q3-Q4)
- [ ] Desktop app complète (CLI + GUI)
- [ ] Database SQLite (remplace SharedPreferences)
- [ ] Cloud sync configs multi-device
- [ ] API REST pour analyse distante

### Long terme (2027+)
- [ ] ML auto-détection R_ds depuis historique
- [ ] Base communautaire MOSFET par modèle
- [ ] Prédiction durée vie batterie (ML)
- [ ] Alertes proactives dégradation

---

## Contributing

Contributions bienvenues ! Spécialement :
- Valeurs MOSFET vérifiées pour autres modèles
- Améliorations UI/UX
- Tests automatiques
- Traductions

**Process** :
1. Fork repo
2. Create feature branch
3. Commit changes
4. Push to branch
5. Create Pull Request

Voir [MOSFET_CONFIG_GUIDE.md](MOSFET_CONFIG_GUIDE.md) pour architecture. [cite:84]

---

## License

MIT License - Voir LICENSE file

---

## Contact

- **GitHub Issues** : Bug reports, feature requests
- **Original Python version** : [EUC_SOH](https://github.com/Tritbool/EUC_SOH)
- **Author** : Gauthier LYAN

---

## Remerciements

- Original EUC_SOH Python implementation
- WheelLog/EUC World communities
- Kotlin DataFrame team
- MPAndroidChart library
- Beta testers V11/RS19/Sherman

---

**Version** : 2.0.0  
**Date** : Mars 2026  
**Status** : ✅ Production Ready  
**Android** : API 26+ (Android 8.0+)  
**Kotlin** : 1.9+
