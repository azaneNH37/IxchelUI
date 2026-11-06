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

// The published Javadoc is the host API: only the types marked @HostApi (core.api.HostApi). Public
// alone means nothing here, since every type used across packages has to be public. The same holds
// for the docs site's Java API pages below.
tasks.withType<Javadoc>().configureEach {
    val hostApi = Regex("^@HostApi\\b", RegexOption.MULTILINE)
    exclude { !it.isDirectory && !hostApi.containsMatchIn(it.file.readText(Charsets.UTF_8)) }
}

// The docs site's Java API pages, docs/java-api/<module>/: Markdown written by :ixchel-doclet. They
// link to the pages of the Ixchel modules on the classpath, whose root package is
// com.azane.ixchel.<artifactId without "ixchel-" and the Minecraft version>.
val doclet: Configuration by configurations.creating
dependencies { doclet(project(":ixchel-doclet")) }
tasks.register<Javadoc>("javaApi") {
    group = "documentation"
    description = "Writes this module's Java API pages of the docs site."
    val main = sourceSets.main.get()
    source = main.allJava
    classpath = main.output + main.compileClasspath
    setDestinationDir(rootProject.layout.projectDirectory.dir("docs/java-api/${project.name}").asFile)
    val docletPath: FileCollection = doclet
    inputs.files(docletPath)
    title = null // read by the standard doclet only
    val module = project.name
    val modules = configurations.compileClasspath.flatMap { it.incoming.artifacts.resolvedArtifacts }.map { all ->
        listOf(module) + all.mapNotNull { (it.id.componentIdentifier as? ProjectComponentIdentifier)?.projectName }
    }
    (options as StandardJavadocDocletOptions).apply {
        doclet = "com.azane.ixchel.doclet.MarkdownDoclet"
        noTimestamp(false)
        addBooleanOption("Xdoclint:all,-missing", false)
        addStringOption("module", project.name)
    }
    doFirst {
        (options as StandardJavadocDocletOptions).apply {
            docletpath = docletPath.files.toList()
            addMultilineStringsOption("link").value = modules.get().distinct()
                .map { "com.azane.ixchel.${it.removePrefix("ixchel-").substringBefore('-')}=$it" }
        }
    }
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
