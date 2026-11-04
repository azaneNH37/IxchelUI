// Offline lint and schema for Ixchel UIs (`ixchel-kit`). Depends on core only: a host runs it with
// JavaExec on its own runtime classpath, which supplies its view models and the mc layer's
// native element declarations and library components.
plugins {
    id("ixchel.library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

dependencies {
    implementation(project(":ixchel-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
