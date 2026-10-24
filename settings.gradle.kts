pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ixchel-ui"

include(
    "ixchel-core",
    "ixchel-mc-1.21.1",
    "ixchel-neoforge-1.21.1",
    "testmod-neoforge-1.21.1",
)
