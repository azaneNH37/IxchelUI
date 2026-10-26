// Shade check, copy B: same recipe as copy A, but with `-PshadeIxchel=<version>` it shades a published
// Ixchel (SRG jars, remapped to Mojang names first) instead of this checkout, so the two copies in one
// production instance are different versions. Without the property it builds from this checkout.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev.legacyforge")
    id("com.gradleup.shadow")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

legacyForge {
    version = libs.versions.forge.get()
}

val shade: Configuration by configurations.creating {
    isCanBeConsumed = false
    exclude(group = "it.unimi.dsi", module = "fastutil")
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}
configurations.implementation { extendsFrom(shade) }

val published = providers.gradleProperty("shadeIxchel")
if (published.isPresent) {
    repositories { mavenLocal() }
    // The two Minecraft-facing modules are published in SRG; this configuration remaps them (it is not
    // transitive, hence each module is listed). The core and taffy have no Minecraft names.
    val modShade = obfuscation.createRemappingConfiguration(shade)
    val version = published.get()
    dependencies {
        listOf("ixchel-forge-1.20.1", "ixchel-mc-1.20.1").forEach {
            modShade("com.azane.ixchel:$it") { version { strictly(version) } }
        }
        shade("com.azane.ixchel:ixchel-core") { version { strictly(version) } }
    }
} else {
    dependencies { shade(project(":ixchel-forge-1.20.1")) }
}

tasks.shadowJar {
    configurations = listOf(shade)
    archiveClassifier = ""
    relocate("com.azane.ixchel", "com.azane.shadetest.b.shadow.ixchel")
    relocate("dev.vfyjxf.taffy", "com.azane.shadetest.b.shadow.taffy")
    exclude("module-info.class", "META-INF/versions/*/module-info.class",
            "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
}

tasks.jar { archiveClassifier = "slim" }

obfuscation {
    reobfuscate(tasks.shadowJar, sourceSets.main.get())
}
