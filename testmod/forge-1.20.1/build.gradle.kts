// Development host mod for Forge 1.20.1: runs the library inside a real client. Not published.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev.legacyforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

val libraryModules = listOf(":ixchel-core", ":ixchel-mc-1.20.1", ":ixchel-forge-1.20.1")
libraryModules.forEach { evaluationDependsOn(it) }

legacyForge {
    version = libs.versions.forge.get()

    runs {
        register("client") {
            client()
            // Hot reload of UI templates and stylesheets straight from the source tree (L1).
            systemProperty("ixchel.devResources.ixcheltest", file("src/main/resources").absolutePath)
            // `-Pbench` (or `-Pbench=hud`): BenchWorld opens the dev world (creating it if missing),
            // HudBench cycles and screenshots unattended, then quits. `-Pbench=refit` runs RefitBench.
            val bench = providers.gradleProperty("bench")
            if (bench.isPresent) {
                systemProperty("ixcheltest.bench", bench.get().ifEmpty { "hud" })
            }
        }
    }

    mods {
        register("ixcheltest") {
            sourceSet(sourceSets.main.get())
            // Library classes reference Minecraft, so in development they load as part of this mod.
            libraryModules.forEach { sourceSet(project(it).sourceSets.main.get()) }
        }
    }
}

dependencies {
    implementation(project(":ixchel-forge-1.20.1"))
    // Plain Java libraries are not loaded in Forge runs unless added here.
    "additionalRuntimeClasspath"(libs.taffy)
}

// M2 coexistence check: `gradlew :testmod-forge-1.20.1:runClient -Pcompat` also loads Oculus (the Forge
// port of Iris, which needs Embeddium), remapped from SRG for the dev runtime. ImmediatelyFast cannot
// run here: its common mixins carry SRG names without a refmap, which dev remapping does not rewrite.
if (providers.gradleProperty("compat").isPresent) {
    repositories {
        maven("https://api.modrinth.com/maven") {
            content { includeGroup("maven.modrinth") }
        }
    }
    dependencies {
        "modRuntimeOnly"("maven.modrinth:embeddium:0.3.31+mc1.20.1")
        "modRuntimeOnly"("maven.modrinth:oculus:1.20.1-1.8.0")
    }
}
