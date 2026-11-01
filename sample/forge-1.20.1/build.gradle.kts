// Samples for Forge 1.20.1, set up as any mod using Ixchel is: ModDevGradle plus the
// `com.azane.ixchel` plugin, which depends on, shades, relocates and reobfuscates the library and
// lints the templates. Not published.
//
// Two source sets, two mods:
// - `main` is `ixchel_sample`, laid out as a normal mod (registry/, network/, client/ and one package
//   per sample, templates under assets/ixchel_sample/ui/<package>/<stem>.{html,css}).
// - `check` is `ixchel_sample_check`, the unattended functional checks (package `sample.check`) on
//   the library's check runner (`IxchelForge.installChecks`). It is loaded only with
//   `-Pcheck=<name>[,<name>…]|all` (no value: `all`): the run then enters the dev world (created if
//   missing), runs the selected checks in order through the samples' public entry classes, writes
//   `run/checks/ixchel_sample/report.txt`, a dump per failure and screenshots, and quits, failing on
//   a failed check. It is never packaged.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev.legacyforge")
    id("com.azane.ixchel")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

legacyForge {
    version = libs.versions.forge.get()

    runs {
        register("client") {
            client()
        }
    }
}

ixchel {
    mod("ixchel_sample", "com.azane.ixchel.sample.shadow")
    checks("ixchel_sample_check")
}

// Slot compatibility (L2): `-Pslotcompat=jei` loads JEI and Mouse Tweaks, `-Pslotcompat=emi` loads EMI
// and Mouse Tweaks, next to the machine panel (`-Pcheck=machine`).
val slotCompat = providers.gradleProperty("slotcompat")
if (slotCompat.isPresent) {
    repositories {
        maven("https://api.modrinth.com/maven") {
            content { includeGroup("maven.modrinth") }
        }
    }
    dependencies {
        "modRuntimeOnly"("maven.modrinth:mouse-tweaks:1.20.1-2.25.1-forge")
        when (slotCompat.get()) {
            "jei" -> "modRuntimeOnly"("maven.modrinth:jei:15.74.0.230")
            "emi" -> "modRuntimeOnly"("maven.modrinth:emi:1.1.24+1.20.1+forge")
            else -> throw GradleException("slotcompat must be jei or emi")
        }
    }
}
