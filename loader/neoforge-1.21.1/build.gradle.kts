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
