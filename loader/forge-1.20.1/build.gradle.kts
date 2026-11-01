// Loader glue: the only published 1.20.1 module allowed to reference Forge API.
plugins {
    id("ixchel.library")
    id("net.neoforged.moddev.legacyforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

legacyForge {
    version = libs.versions.forge.get()
}

dependencies {
    api(project(":ixchel-mc-1.20.1"))
}

// A host that depends on this jar in development (unshaded) gets it loaded into FML's game layer,
// next to Minecraft and the other Ixchel jars.
tasks.jar {
    manifest.attributes("FMLModType" to "GAMELIBRARY")
}
