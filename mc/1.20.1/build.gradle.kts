// Vanilla-only adapter: compiles against Minecraft 1.20.1 through MCP mode, without any loader API.
plugins {
    id("ixchel.library")
    id("net.neoforged.moddev.legacyforge")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

legacyForge {
    mcpVersion = libs.versions.mcp.get()
    // Plain JUnit tests of the math in this module see Minecraft's libraries (JOML); no game is started.
    addModdingDependenciesTo(sourceSets.test.get())
}

dependencies {
    api(project(":ixchel-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// A host that depends on this jar in development (unshaded) gets it loaded into FML's game layer,
// next to Minecraft and the other Ixchel jars.
tasks.jar {
    manifest.attributes("FMLModType" to "GAMELIBRARY")
}
