// The `com.azane.ixchel` Gradle plugin: wires a ModDevGradle mod to depend on, shade and lint Ixchel.
// An included build of the Ixchel repository (the samples apply it from source), published with the
// library under the same group and version.
import java.util.Properties

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

val ixchel = Properties().apply {
    file("../../gradle.properties").reader(Charsets.UTF_8).use { load(it) }
}
group = ixchel.getProperty("group")
version = ixchel.getProperty("version")

dependencies {
    // The mod applies ModDevGradle itself; the plugin only calls into it.
    compileOnly("net.neoforged:moddev-gradle:${libs.versions.moddev.get()}")
    implementation("com.gradleup.shadow:shadow-gradle-plugin:${libs.versions.shadow.get()}")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Gradle may run on Java 17.
    options.release = 17
}

gradlePlugin {
    plugins {
        register("ixchel") {
            id = "com.azane.ixchel"
            implementationClass = "com.azane.ixchel.gradle.IxchelPlugin"
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            licenses {
                license {
                    name = "MPL-2.0"
                    url = "https://mozilla.org/MPL/2.0/"
                }
            }
        }
    }
}

// The library version and the coordinates the plugin adds to a mod outside this repository.
val buildInfo by tasks.registering(WriteProperties::class) {
    destinationFile = layout.buildDirectory.file("generated/build-info/com/azane/ixchel/gradle/build.properties")
    property("group", project.group)
    property("version", project.version)
    val taffy = libs.taffy.get()
    property("taffy", "${taffy.module}:${taffy.versionConstraint.requiredVersion}")
}
sourceSets.main {
    resources.srcDir(buildInfo.map { layout.buildDirectory.dir("generated/build-info") })
}
