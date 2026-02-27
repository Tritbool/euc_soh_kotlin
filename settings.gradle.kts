rootProject.name = "euc-soh-kotlin"

include(":euc-soh-core")
include(":euc-soh-android")
include(":euc-soh-desktop")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}
