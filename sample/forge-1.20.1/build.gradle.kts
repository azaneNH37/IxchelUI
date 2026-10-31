// Sample and development host mod for Forge 1.20.1: runs the library inside a real client. Not published.
//
// Two source sets, two mods:
// - `main` is `ixchelsample`, laid out as a normal mod (registry/, network/, client/ and one package
//   per sample, templates under assets/ixchelsample/ui/<package>/<stem>.{html,css}).
// - `check` is `ixchelsamplecheck`, the unattended functional checks (package `sample.check`). It sees
//   `main` and is loaded only with `-Pcheck=<name>[,<name>…]|all` (no value: `all`): the run then starts
//   in the dev world (BenchWorld creates it if missing), runs the selected checks in order through the
//   samples' public entry classes, writes `run/checks/report.txt` and screenshots, and quits.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev.legacyforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

val libraryModules = listOf(":ixchel-core", ":ixchel-mc-1.20.1", ":ixchel-forge-1.20.1")
libraryModules.forEach { evaluationDependsOn(it) }

val checkSelection = providers.gradleProperty("check")

val checkSourceSet = sourceSets.create("check") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().output + sourceSets.main.get().runtimeClasspath
}

legacyForge {
    version = libs.versions.forge.get()
    addModdingDependenciesTo(checkSourceSet)

    runs {
        register("client") {
            client()
            // Hot reload of UI templates and stylesheets straight from the source tree (L1).
            systemProperty("ixchel.devResources.ixchelsample", file("src/main/resources").absolutePath)
            if (checkSelection.isPresent) {
                // The check mod's classpath contains main's; the checks read the selection from this property.
                sourceSet = checkSourceSet
                systemProperty("ixchelsample.check", checkSelection.get().ifEmpty { "all" })
            }
        }
    }

    mods {
        register("ixchelsample") {
            sourceSet(sourceSets.main.get())
            // Library classes reference Minecraft, so in development they load as part of this mod.
            libraryModules.forEach { sourceSet(project(it).sourceSets.main.get()) }
        }
        if (checkSelection.isPresent) {
            register("ixchelsamplecheck") {
                sourceSet(checkSourceSet)
            }
        }
    }
}

dependencies {
    implementation(project(":ixchel-forge-1.20.1"))
    // Plain Java libraries are not loaded in Forge runs unless added here.
    "additionalRuntimeClasspath"(libs.taffy)
}

// The checks compile in every build, so a change to the samples cannot leave them behind.
tasks.named("check") {
    dependsOn(tasks.named(checkSourceSet.classesTaskName))
}

// UI lint: checks every template under src/main/resources against the @UiTemplate view models, offline.
// This is the recipe for mods using Ixchel, copied verbatim except the dependency line.
val ixchelKit by configurations.creating
dependencies { ixchelKit(project(":ixchel-kit")) } // external mods: ixchelKit("com.azane.ixchel:ixchel-kit:<version>")
val lintUi by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the UI templates against their view models."
    val resources = file("src/main/resources")
    val classesDirs = sourceSets.main.get().output.classesDirs
    // The mod's view models, Minecraft (for their field types) and the Ixchel mc layer's native
    // element declarations. The kit reads them through its own loader instead of running on this
    // classpath: the development Minecraft jar is patched but keeps signature files the JVM rejects.
    val runtime = sourceSets.main.get().runtimeClasspath
    classpath = ixchelKit
    mainClass.set("com.azane.ixchel.kit.Main")
    inputs.files(runtime)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("lint", "--resources", resources.path, "--classes") + classesDirs.files.map { it.path } +
                listOf("--classpath", runtime.asPath)
    })
}
tasks.check { dependsOn(lintUi) }

// Slot compatibility (L2): `-Pslotcompat=jei` loads JEI and Mouse Tweaks, `-Pslotcompat=emi` loads EMI
// and Mouse Tweaks, next to the machine panel (`-Pcheck=machine`).
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
