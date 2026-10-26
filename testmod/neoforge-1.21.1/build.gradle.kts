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
