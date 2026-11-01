// Loader glue: the only published module allowed to reference NeoForge API.
plugins {
    id("ixchel.library")
    id("net.neoforged.moddev")
}

neoForge {
    version = libs.versions.neoforge.get()
}

dependencies {
    api(project(":ixchel-mc-1.21.1"))
}

// A host that depends on this jar in development (unshaded) gets it loaded into FML's game layer,
// next to Minecraft and the other Ixchel jars.
tasks.jar {
    manifest.attributes("FMLModType" to "GAMELIBRARY")
}
