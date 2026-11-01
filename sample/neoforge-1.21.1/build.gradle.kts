// Samples for NeoForge 1.21.1 (the ammo HUD and the Z refit screen), built the way a mod using
// Ixchel is: the library is an ordinary dependency in development and is shaded and relocated into
// the mod's jar. Not published. Lines marked "external mods" show what differs outside this repository.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev")
    id("com.gradleup.shadow")
}

neoForge {
    version = libs.versions.neoforge.get()

    runs {
        register("client") {
            client()
            // Hot reload of UI templates and stylesheets straight from the source tree.
            systemProperty("ixchel.devResources.ixchel_sample", file("src/main/resources").absolutePath)
        }
    }

    mods {
        register("ixchel_sample") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Everything in here ends up relocated inside the mod's jar. Minecraft ships fastutil (taffy's only
// dependency), so it stays out.
val shade: Configuration by configurations.creating {
    isCanBeConsumed = false
    exclude(group = "it.unimi.dsi", module = "fastutil")
}
configurations.implementation { extendsFrom(shade) }

dependencies {
    shade(project(":ixchel-neoforge-1.21.1")) // external mods: shade("com.azane.ixchel:ixchel-neoforge-1.21.1:<version>")
    // Plain Java libraries are not loaded in 1.21.1 runs unless added here.
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

// The shaded jar is the mod.
tasks.jar { archiveClassifier = "slim" }
tasks.assemble { dependsOn(tasks.shadowJar) }

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
