plugins {
    kotlin("jvm") version "2.1.0"
    application
}

application {
    mainClass.set("io.github.eucsoh.desktop.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":euc-soh-core")){
        exclude(group = "org.slf4j")
        exclude(group = "ch.qos.logback")
    }
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    
    // Simple Swing UI for file picker
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
}

kotlin {
    jvmToolchain(21)
}
