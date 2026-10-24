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
}

dependencies {
    api(project(":ixchel-core"))
}
