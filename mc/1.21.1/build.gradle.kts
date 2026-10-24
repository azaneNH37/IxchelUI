// Vanilla-only adapter: compiles against Minecraft through NeoForm, without any loader API.
plugins {
    id("ixchel.library")
    id("net.neoforged.moddev")
}

neoForge {
    neoFormVersion = libs.versions.neoform.get()
}

dependencies {
    api(project(":ixchel-core"))
}
