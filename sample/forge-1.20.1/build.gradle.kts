// Samples for Forge 1.20.1, built the way a mod using Ixchel is: the library is an ordinary
// dependency in development and is shaded, relocated and reobfuscated into the mod's jar. Not
// published. Lines marked "external mods" show what differs outside this repository.
//
// Two source sets, two mods:
// - `main` is `ixchel_sample`, laid out as a normal mod (registry/, network/, client/ and one package
//   per sample, templates under assets/ixchel_sample/ui/<package>/<stem>.{html,css}).
// - `check` is `ixchel_sample_check`, the unattended functional checks (package `sample.check`) on
//   the library's check runner (`IxchelForge.installChecks`). It sees `main` and is loaded only with
//   `-Pcheck=<name>[,<name>…]|all` (no value: `all`): the run then enters the dev world (created if
//   missing), runs the selected checks in order through the samples' public entry classes, writes
//   `run/checks/ixchel_sample/report.txt`, a dump per failure and screenshots, and quits, failing on
//   a failed check. It is never packaged.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev.legacyforge")
    id("com.gradleup.shadow")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

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
            // Hot reload of UI templates and stylesheets straight from the source tree.
            systemProperty("ixchel.devResources.ixchel_sample", file("src/main/resources").absolutePath)
            if (checkSelection.isPresent) {
                // The check mod's classpath contains main's; the runner reads the selection from this property.
                sourceSet = checkSourceSet
                systemProperty("ixchel.check.ixchel_sample", checkSelection.get().ifEmpty { "all" })
            }
        }
    }

    mods {
        register("ixchel_sample") {
            sourceSet(sourceSets.main.get())
        }
        if (checkSelection.isPresent) {
            register("ixchel_sample_check") {
                sourceSet(checkSourceSet)
            }
        }
    }
}

// Everything in here ends up relocated inside the mod's jar. Minecraft ships fastutil (taffy's only
// dependency), so it stays out.
val shade: Configuration by configurations.creating {
    isCanBeConsumed = false
    exclude(group = "it.unimi.dsi", module = "fastutil")
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}
configurations.implementation { extendsFrom(shade) }

dependencies {
    shade(project(":ixchel-forge-1.20.1"))
    // External mods: the two Minecraft-facing modules are published in SRG and go through a remapping
    // configuration, which is not transitive, so each module is listed; the core has no Minecraft names.
    //   val modShade = obfuscation.createRemappingConfiguration(shade)
    //   modShade("com.azane.ixchel:ixchel-forge-1.20.1:<version>")
    //   modShade("com.azane.ixchel:ixchel-mc-1.20.1:<version>")
    //   shade("com.azane.ixchel:ixchel-core:<version>")

    // Plain Java libraries are not loaded in Forge runs unless added here.
    "additionalRuntimeClasspath"(libs.taffy)
}

tasks.shadowJar {
    configurations = listOf(shade)
    archiveClassifier = ""
    relocate("com.azane.ixchel", "com.azane.ixchel.sample.shadow.ixchel") {
        // Only because this sample's own package lies under the library's.
        exclude("com.azane.ixchel.sample.**")
    }
    relocate("dev.vfyjxf.taffy", "com.azane.ixchel.sample.shadow.taffy")
    exclude("module-info.class", "META-INF/versions/*/module-info.class",
            "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
}

// The plain jar would be reobfuscated by default; the shaded jar is the mod.
tasks.jar { archiveClassifier = "slim" }

obfuscation {
    reobfuscate(tasks.shadowJar, sourceSets.main.get())
}

// The checks compile in every build, so a change to the samples cannot leave them behind.
tasks.named("check") {
    dependsOn(tasks.named(checkSourceSet.classesTaskName))
}

// UI lint: checks every template under src/main/resources against the @UiTemplate view models, offline.
val ixchelKit by configurations.creating
dependencies { ixchelKit(project(":ixchel-kit")) } // external mods: ixchelKit("com.azane.ixchel:ixchel-kit:<version>")
val lintUi by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Checks the UI templates against their view models."
    classpath = ixchelKit
    mainClass = "com.azane.ixchel.kit.Main"
    val resources = file("src/main/resources")
    val runtime = sourceSets.main.get().runtimeClasspath
    inputs.files(runtime)
    argumentProviders.add { listOf("lint", "--resources", resources.path, "--classpath", runtime.asPath) }
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
