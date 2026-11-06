plugins {
    alias(libs.plugins.moddev) apply false
    alias(libs.plugins.moddev.legacyforge) apply false
}

// `publishToMavenLocal` and `build` at the root also cover the Gradle plugin, which is published
// with the library.
val plugin = gradle.includedBuild("gradle-plugin")
tasks.register("publishToMavenLocal") { dependsOn(plugin.task(":publishToMavenLocal")) }
tasks.register("build") { dependsOn(plugin.task(":build")) }

// Everything the docs site generates before `vuepress build`: each library module's Java API pages
// (`javaApi`), then the reference pages and the name check.
val javaApi = subprojects.map { p -> p.tasks.matching { it.name == "javaApi" } }
tasks.register("docs") {
    group = "documentation"
    description = "Generates the docs site's reference and Java API pages and checks its names."
    dependsOn(javaApi, ":ixchel-mc-1.20.1:docsReference")
}
