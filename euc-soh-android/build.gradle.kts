plugins {
    id("com.android.application")
    kotlin("android") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    kotlin("plugin.compose") version "2.1.0"  // Compose Compiler for Kotlin 2.0+
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

android {
    namespace = "io.github.eucsoh.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.eucsoh.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin {
        jvmToolchain(21)
    }
    
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core module
    implementation(project(":euc-soh-core")) {
        // Exclude JVM-heavy DataFrame submodules
        exclude(group = "org.jetbrains.kotlinx", module = "dataframe-arrow")
        exclude(group = "org.jetbrains.kotlinx", module = "dataframe-excel")
        exclude(group = "org.jetbrains.kotlinx", module = "dataframe-jdbc")
        exclude(group = "org.jetbrains.kotlinx", module = "dataframe-openapi")

        // Exclude JVM logging
        exclude(group = "org.slf4j")
        exclude(group = "ch.qos.logback")

        // Exclude Apache POI (Excel processing)
        exclude(group = "org.apache.poi")
    }
    
    // Android core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    
    // Material Design
    implementation("com.google.android.material:material:1.12.0")
    
    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
