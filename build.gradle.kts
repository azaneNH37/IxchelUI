plugins {
    alias(libs.plugins.moddev) apply false
    alias(libs.plugins.moddev.legacyforge) apply false
}

// `publishToMavenLocal` and `build` at the root also cover the Gradle plugin, which is published
// with the library.
val plugin = gradle.includedBuild("gradle-plugin")
tasks.register("publishToMavenLocal") { dependsOn(plugin.task(":publishToMavenLocal")) }
tasks.register("build") { dependsOn(plugin.task(":build")) }
