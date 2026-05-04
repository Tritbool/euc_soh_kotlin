# EUC SoH - Android Application

Application Android pour l'analyse de l'état de santé (SoH) des batteries d'EUC (Electric Unicycles).

## Fonctionnalités

### Détection SAF des roues

L'utilisateur choisit explicitement un dossier via le sélecteur SAF (Storage Access Framework), puis l'application scanne ce dossier à la recherche de deux sources de logs :

1. **WheelLog** : `Download/WheelLog/MAC_ADDRESS/`
   - Structure : Chaque sous-dossier a pour nom l'adresse MAC de la roue (format `XX_YY_ZZ_AA_BB_CC`)
   - Contenu : Fichiers CSV de logs

2. **EUC World** : `Download/EUC World/EUC Data Logs/` (ou variantes localisées)
   - Structure : CSV dans un dossier plat ou arborescence
   - Métadonnées : Extraites de la colonne `extra` (une paire `key=value` par ligne)
   - Champs extraits :
     - `euc.btAddress` : Adresse MAC (obligatoire)
     - `euc.btName` : Nom d'affichage
     - `euc.make` : Fabricant
     - `euc.model` : Modèle
     - `euc.serial` : Numéro de série

### Agrégation des données

- Les roues sont identifiées par leur adresse MAC
- Les logs de WheelLog et EUC World sont automatiquement fusionnés
- Les métadonnées (nom, fabricant, modèle) proviennent d'EUC World
- Cache local pour éviter de rescanner à chaque ouverture (durée : 24h)

## Architecture

### Data Layer

```
data/
├── model/
│   └── WheelIdentity.kt        # Modèle de roue détectée
├── scanner/
│   ├── WheelLogScanner.kt      # Scanner pour WheelLog
│   ├── EucWorldScanner.kt      # Scanner pour EUC World
│   └── WheelScanner.kt         # Agrégateur de scanners
├── database/
│   ├── WheelEntity.kt          # Entity Room pour persistence
│   ├── WheelDao.kt             # DAO Room
│   └── WheelDatabase.kt        # Configuration Room
└── repository/
    └── WheelRepository.kt      # Repository avec cache
```

### Presentation Layer

```
ui/
├── SohViewModel.kt             # ViewModel avec StateFlow
└── screens/
    └── MainScreen.kt            # UI Jetpack Compose
```

## Permissions

Cette variante utilise SAF et ne requiert pas d'autorisation globale de stockage (`MANAGE_EXTERNAL_STORAGE`).

## Technologies utilisées

- **Kotlin** 2.1.0
- **Jetpack Compose** : UI moderne et déclarative
- **Room** : Persistence locale
- **Coroutines** : Opérations asynchrones
- **StateFlow** : Gestion d'état réactive
- **Kotlinx Serialization** : Sérialisation JSON des URIs

## Dépendances du module core

L'application Android dépend du module `euc-soh-core` qui contient :
- `SohAnalyzer` : Moteur d'analyse
- Détecteurs de tendances et anomalies
- Modèles de données (AlarmResult, AnalysisResult, etc.)

Exclusions pour Android :
- `dataframe-arrow`, `dataframe-excel`, `dataframe-jdbc` : Non compatibles Android
- Bibliothèques SLF4J et Logback : Remplacées par Android Log
- Apache POI : Trop lourd pour Android

## Utilisation

1. Installer l'application sur Android
2. Choisir un dossier source via SAF
3. L'app scanne ce dossier pour trouver WheelLog et EUC World
4. Sélectionner une roue dans la liste
5. Cliquer sur "Analyser" pour lancer l'analyse SoH
6. Consulter les résultats (Req band, configuration pack, alarmes)

## Force refresh

Bouton "Rafraîchir" dans la barre d'action pour forcer un nouveau scan (ignore le cache).

## Limitations actuelles

- Mode manuel : Implémentation basique (sélection d'URI unique)
- Affichage des résultats : Résumé textuel simple (pas de graphiques)
- Export : Non implémenté (prévu : JSON, CSV, partage)

## TODO

- [ ] Implémenter l'énumération de fichiers pour le mode manuel (SAF)
- [ ] Ajouter des graphiques (Req vs km, tendances)
- [ ] Exporter les résultats (JSON summary)
- [ ] Afficher les détails des alarmes (timestamps, valeurs)
- [ ] Ajouter un historique d'analyses
- [ ] Mode dark theme
