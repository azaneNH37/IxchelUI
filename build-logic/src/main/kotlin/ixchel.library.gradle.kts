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

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
