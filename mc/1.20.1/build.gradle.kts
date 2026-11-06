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

// The docs site's reference pages, taken from this layer's library: the full feature set, 1.21.1
// being a subset. Also checks the hand-written pages, and the Java API pages, for names the library
// lacks, so it runs after every module's `javaApi`.
val kit: Configuration by configurations.creating
dependencies { kit(project(":ixchel-kit")) }
tasks.register<JavaExec>("docsReference") {
    group = "documentation"
    description = "Writes docs/reference/ and checks the names in docs/."
    mustRunAfter(rootProject.subprojects.map { p -> p.tasks.matching { it.name == "javaApi" } })
    val docs = rootProject.layout.projectDirectory.dir("docs")
    classpath = kit
    mainClass = "com.azane.ixchel.kit.Main"
    val runtime = sourceSets.main.get().runtimeClasspath
    inputs.files(runtime)
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("docs", "--out", docs.dir("reference").asFile.path, "--check", docs.asFile.path,
                "--classpath", runtime.asPath)
    })
}
