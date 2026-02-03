# 🚀 Guide de Setup Complet - EUC SoH

## Étape 1 : Créer la structure de dossiers

```bash
# À la racine de ton projet (dossier euc_soh)
mkdir -p euc-soh-core/src/main/kotlin/com/euc/soh/{model,analysis,parser,config,export}
mkdir -p euc-soh-core/src/test/kotlin/com/euc/soh/analysis
mkdir -p gradle
```

## Étape 2 : Placer les fichiers de configuration (ROOT du projet)

Copie ces fichiers **à la racine** de `euc_soh/` :

```
euc_soh/
├── settings.gradle.kts          ← Fichier que je viens de générer
├── build.gradle.kts             ← Fichier que je viens de générer
├── gradle.properties            ← Fichier que je viens de générer
├── .gitignore                   ← Fichier que je viens de générer
└── gradle/
    └── libs.versions.toml       ← Fichier que je viens de générer (dans dossier gradle/)
```

## Étape 3 : Placer build.gradle.kts du module core

Copie le fichier `build-gradle-core.kts` (que j'ai généré) vers :
```
euc_soh/euc-soh-core/build.gradle.kts
```

## Étape 4 : Placer les fichiers Kotlin

### Dans euc-soh-core/src/main/kotlin/com/euc/soh/

**config/**
- `Constants.kt`

**model/**
- `Models.kt`

**analysis/**
- `ArrheniusNormalizer.kt`
- `ResistanceCalculator.kt`
- `CUSUMDetector.kt`
- `GaussianAlarmDetector.kt`

### Dans euc-soh-core/src/test/kotlin/com/euc/soh/analysis/

- `ArrheniusNormalizerTest.kt`

## Étape 5 : Installer Gradle Wrapper

À la racine de `euc_soh/`, lance :

```bash
# Si tu as gradle installé globalement :
gradle wrapper --gradle-version=8.5

# OU si tu veux télécharger directement :
# Linux/Mac
curl -s https://get.sdkman.io | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.5
gradle wrapper

# Windows (PowerShell)
# Télécharger depuis https://gradle.org/releases/
# Puis extraire et ajouter bin/ au PATH
```

Cela va créer :
```
euc_soh/
├── gradlew          ← Script pour Linux/Mac
├── gradlew.bat      ← Script pour Windows
└── gradle/
    └── wrapper/
        ├── gradle-wrapper.jar
        └── gradle-wrapper.properties
```

## Étape 6 : Vérifier la structure complète

```
euc_soh/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── .gitignore
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── wrapper/
│   │   ├── gradle-wrapper.jar
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
│
└── euc-soh-core/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/euc/soh/
        │   ├── config/
        │   │   └── Constants.kt
        │   ├── model/
        │   │   └── Models.kt
        │   ├── analysis/
        │   │   ├── ArrheniusNormalizer.kt
        │   │   ├── ResistanceCalculator.kt
        │   │   ├── CUSUMDetector.kt
        │   │   └── GaussianAlarmDetector.kt
        │   ├── parser/         (vide pour l'instant)
        │   └── export/         (vide pour l'instant)
        └── test/kotlin/com/euc/soh/
            └── analysis/
                └── ArrheniusNormalizerTest.kt
```

## Étape 7 : Premier build

```bash
# À la racine de euc_soh/
./gradlew build

# Ou sur Windows :
gradlew.bat build
```

## Étape 8 : Lancer les tests

```bash
./gradlew :euc-soh-core:test

# Avec détails :
./gradlew :euc-soh-core:test --info
```

## Étape 9 : Vérifier que tout compile

```bash
# Compiler sans exécuter les tests
./gradlew :euc-soh-core:compileKotlin

# Voir les dépendances
./gradlew :euc-soh-core:dependencies
```

## 🛠️ Si tu n'as pas Gradle installé

### Option 1 : Utiliser SDKMAN (Linux/Mac - RECOMMANDÉ)

```bash
curl -s https://get.sdkman.io | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.5
```

### Option 2 : Téléchargement manuel

**Linux/Mac :**
```bash
wget https://services.gradle.org/distributions/gradle-8.5-bin.zip
unzip gradle-8.5-bin.zip
sudo mv gradle-8.5 /opt/gradle
export PATH=$PATH:/opt/gradle/bin
```

**Windows :**
1. Télécharger https://services.gradle.org/distributions/gradle-8.5-bin.zip
2. Extraire dans `C:\Gradle`
3. Ajouter `C:\Gradle\bin` au PATH système

### Option 3 : Via package manager

```bash
# Ubuntu/Debian
sudo apt install gradle

# Fedora
sudo dnf install gradle

# macOS
brew install gradle

# Arch
sudo pacman -S gradle
```

## 🐛 Dépannage

### Erreur : "Could not find kotlin-stdlib"

Solution : Vérifier que `gradle/libs.versions.toml` est bien présent

### Erreur : "Plugin with id 'kotlin' not found"

Solution : Vérifier `build.gradle.kts` (root) - la version Kotlin doit être définie

### Gradle trop lent

Ajouter dans `gradle.properties` :
```properties
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
```

## ✅ Commandes utiles

```bash
# Nettoyer le projet
./gradlew clean

# Build + tests
./gradlew build

# Tests seuls
./gradlew test

# Compiler sans tests
./gradlew assemble

# Voir toutes les tâches disponibles
./gradlew tasks

# Publier en local (pour utiliser dans Android/Desktop)
./gradlew publishToMavenLocal
```

## 📝 Prochaines étapes

Une fois que `./gradlew build` fonctionne :

1. **Implémenter les parsers CSV** (je peux les générer)
2. **Tester avec tes logs réels**
3. **Créer le module Android**
4. **Créer le module Desktop**

## 🆘 Besoin d'aide ?

Si tu bloques à une étape, dis-moi :
- Quelle commande plante
- Le message d'erreur complet
- Ton OS (Linux/Mac/Windows)
