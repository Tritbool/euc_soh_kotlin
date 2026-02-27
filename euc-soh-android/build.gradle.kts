plugins {
    id("com.android.application")
    kotlin("android") version "2.1.0"
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

    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
