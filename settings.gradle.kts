pluginManagement {
    includeBuild("build-logic")
    includeBuild("tools/gradle-plugin")
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ixchel-ui"

// Project names stay flat (they are the artifactIds); directories are grouped by layer.
fun module(name: String, dir: String) {
    include(name)
    project(":$name").projectDir = file(dir)
}

module("ixchel-core", "core")
module("ixchel-mc-1.20.1", "mc/1.20.1")
module("ixchel-mc-1.21.1", "mc/1.21.1")
module("ixchel-forge-1.20.1", "loader/forge-1.20.1")
module("ixchel-neoforge-1.21.1", "loader/neoforge-1.21.1")
module("ixchel-kit", "tools/kit")
module("sample-forge-1.20.1", "sample/forge-1.20.1")
module("sample-neoforge-1.21.1", "sample/neoforge-1.21.1")

// The Gradle plugin is also a regular included build, so the root `build` and `publishToMavenLocal`
// can depend on its tasks.
includeBuild("tools/gradle-plugin")
