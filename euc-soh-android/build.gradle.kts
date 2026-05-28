/*
 * EUC SoH Kotlin - State of Health analysis for Electric Unicycles
 * Copyright (C) 2026  Gauthier LE BARTZ LYAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.github.jk1.license.render.InventoryMarkdownReportRenderer
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.filter.ExcludeTransitiveDependenciesFilter

plugins {
    id("com.android.application")
    kotlin("plugin.serialization") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"  // Compose Compiler for Kotlin 2.0+
    id("com.google.devtools.ksp") version "2.3.6"
    id("com.github.jk1.dependency-license-report") version "3.1.2"
}
kotlin {
    jvmToolchain(21)
}

android {
    namespace = "io.github.eucsoh.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.eucsoh.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "1.53"
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

    
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/thirdparty-LICENSE"
        }
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
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    
    // Material Design
    implementation("com.google.android.material:material:1.13.0")
    
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
    val roomVersion = "2.8.4"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // Charts - MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    
    // PDF Export - iText7 for Android
    implementation("com.itextpdf:itext7-core:9.6.0")

    // MD Compose
    // MD Compose
    implementation("com.halilibo.compose-richtext:richtext-ui-material3:1.0.0-alpha04")
    implementation("com.halilibo.compose-richtext:richtext-commonmark-android:1.0.0-alpha04")
}

licenseReport {
    outputDir = layout.buildDirectory.dir("licenses").get().asFile.toString()

    renderers = arrayOf(
        InventoryMarkdownReportRenderer(
            "third-party-licenses.md",
            "Android dependencies"
        )
    )

    configurations = arrayOf("releaseRuntimeClasspath")

    filters = arrayOf(
        LicenseBundleNormalizer(),
        ExcludeTransitiveDependenciesFilter()
    )
}

tasks.register<Copy>("copyLicenseReportToAssets") {
    dependsOn("generateLicenseReport")

    val generatedReport = layout.buildDirectory
        .file("licenses/third-party-licenses.md")

    val destDir = layout.projectDirectory.dir("src/main/assets")
    val destFile = destDir.file("third_party_licenses.md").asFile  // résolu ici, hors du doLast

    inputs.file(generatedReport)
    outputs.file(destFile)

    from(generatedReport)
    into(destDir)
    rename { "third_party_licenses.md" }

    doLast {
        // destFile est un java.io.File ordinaire, pas une référence à Project
        val cleaned = destFile.readLines()
            .map { line ->
                if (line.matches(Regex("^_\\d{4}-\\d{2}-\\d{2}.*UTC_$")))
                    "_Generated at build time_"
                else
                    line
            }
            .joinToString("\n")
        destFile.writeText(cleaned)
    }
}

tasks.named("preBuild") {
    dependsOn("copyLicenseReportToAssets")
}
