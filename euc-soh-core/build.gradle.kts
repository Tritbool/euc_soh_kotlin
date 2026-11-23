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

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

dependencyLocking {
    lockAllConfigurations()
}

kotlin {
    androidLibrary {
        namespace = "io.github.eucsoh.core"
        compileSdk = 36
        minSdk = 23
    }
    jvm()
    jvmToolchain(21)

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
            }
        }
        jvmMain {
        }
        jvmTest {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:6.1.11")
                implementation("io.kotest:kotest-assertions-core:6.1.11")
            }
        }
    }
}

tasks.named("jvmTest", Test::class) {
    useJUnitPlatform()
}

// Alias so that `gradlew test` keeps running the core tests after the KMP migration.
tasks.register("test") {
    dependsOn("jvmTest")
}

