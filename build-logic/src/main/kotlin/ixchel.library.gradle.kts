// Published Ixchel modules: library API split, sources/javadoc jars, Maven publication.
plugins {
    id("ixchel.java-conventions")
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

// A shaded jar must tell its recipients the license and where the source is (MPL-2.0 §3.2), and carry Taffy's notice.
tasks.withType<Jar>().configureEach {
    from(rootProject.files("LICENSE", "NOTICE")) {
        into("META-INF/ixchel")
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
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
}
