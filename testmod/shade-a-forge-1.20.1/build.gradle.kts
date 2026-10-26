// Shade check, copy A: a host mod that shades and relocates Ixchel the way an outside host would, then
// reobfuscates the whole jar to SRG once. Not published; its jar goes into a production instance next to
// copy B (see testmod/shade-b-forge-1.20.1).
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

// Everything in here ends up relocated inside the host jar. Minecraft ships fastutil (taffy's only
// dependency), so it stays out.
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

dependencies {
    shade(project(":ixchel-forge-1.20.1"))
}

tasks.shadowJar {
    configurations = listOf(shade)
    archiveClassifier = ""
    relocate("com.azane.ixchel", "com.azane.shadetest.a.shadow.ixchel")
    relocate("dev.vfyjxf.taffy", "com.azane.shadetest.a.shadow.taffy")
    exclude("module-info.class", "META-INF/versions/*/module-info.class",
            "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    mergeServiceFiles()
}

// The plain jar would be reobfuscated and published by default; the shaded jar is the deliverable.
tasks.jar { archiveClassifier = "slim" }

obfuscation {
    reobfuscate(tasks.shadowJar, sourceSets.main.get())
}
