// Pure Java: no Minecraft or loader classes on the compile classpath.
plugins {
    id("ixchel.library")
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
