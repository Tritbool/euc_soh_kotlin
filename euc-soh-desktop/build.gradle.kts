plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":euc-soh-core"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
}

kotlin {
    jvmToolchain(21)
}
