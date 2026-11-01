// Pure Java: no Minecraft or loader classes on the compile classpath.
plugins {
    id("ixchel.library")
}

// Shared by the 1.20.1 (Java 17) and 1.21.1 (Java 21) adapters; tests run on 17 too.
java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

dependencies {
    implementation(libs.taffy)

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
