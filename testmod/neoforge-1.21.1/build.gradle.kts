// Development host mod: runs the library inside a real client. Not published.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev")
}

val libraryModules = listOf(":ixchel-core", ":ixchel-mc-1.21.1", ":ixchel-neoforge-1.21.1")
libraryModules.forEach { evaluationDependsOn(it) }

neoForge {
    version = libs.versions.neoforge.get()

    runs {
        register("client") {
            client()
            // Hot reload of UI templates and stylesheets straight from the source tree (L1).
            systemProperty("ixchel.devResources.ixcheltest", file("src/main/resources").absolutePath)
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
    implementation(project(":ixchel-neoforge-1.21.1"))
    // Plain Java libraries are not loaded in 1.21.1 runs unless added here.
    "additionalRuntimeClasspath"(libs.taffy)
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
