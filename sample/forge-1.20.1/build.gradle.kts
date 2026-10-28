// Sample and development host mod for Forge 1.20.1: runs the library inside a real client. Not published.
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
            systemProperty("ixchel.devResources.ixchelsample", file("src/main/resources").absolutePath)
            // `-Pbench` (or `-Pbench=hud`) and `-Pcheck` both start in the dev world (BenchWorld creates it
            // if missing) and quit when done. `-Pbench` runs one timing bench: HudBench cycles and screenshots
            // the HUD layers, `-Pbench=refit` runs RefitBench. `-Pcheck=<name>[,<name>…]|all` runs the
            // functional checks (sample `check/Checks`) in order and writes `run/checks/report.txt`.
            val bench = providers.gradleProperty("bench")
            if (bench.isPresent) {
                systemProperty("ixchelsample.bench", bench.get().ifEmpty { "hud" })
            }
            val check = providers.gradleProperty("check")
            if (check.isPresent) {
                systemProperty("ixchelsample.check", check.get().ifEmpty { "all" })
            }
        }
    }

    mods {
        register("ixchelsample") {
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

// M2 coexistence check: `gradlew :sample-forge-1.20.1:runClient -Pcompat` also loads Oculus (the Forge
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

// M5 slot compatibility (L2): `-Pslotcompat=jei` loads JEI and Mouse Tweaks, `-Pslotcompat=emi` loads EMI
// and Mouse Tweaks, next to the backpack refit (`-Pcheck=backpack`).
val slotCompat = providers.gradleProperty("slotcompat")
if (slotCompat.isPresent) {
    repositories {
        maven("https://api.modrinth.com/maven") {
            content { includeGroup("maven.modrinth") }
        }
    }
    dependencies {
        "modRuntimeOnly"("maven.modrinth:mouse-tweaks:1.20.1-2.25.1-forge")
        when (slotCompat.get()) {
            "jei" -> "modRuntimeOnly"("maven.modrinth:jei:15.74.0.230")
            "emi" -> "modRuntimeOnly"("maven.modrinth:emi:1.1.24+1.20.1+forge")
            else -> throw GradleException("slotcompat must be jei or emi")
        }
    }
}
