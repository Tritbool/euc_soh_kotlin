plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

dependencies {
    // Core dependencies
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    // Kotlin DataFrame (pandas-like API) kept for future use
    implementation("org.jetbrains.kotlinx:dataframe:0.10.0")
    // kotlin-csv for robust, simple CSV parsing (fallback / reliable parser)
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.3")

    // Test dependencies
    testImplementation("io.kotest:kotest-runner-junit5:5.9.0")
    testImplementation("io.kotest:kotest-assertions-core:5.9.0")
    testImplementation("io.kotest:kotest-property:5.9.0")
    testImplementation("io.mockk:mockk:1.13.11")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
