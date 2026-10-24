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
