// Samples for NeoForge 1.21.1 (the ammo HUD and the Z refit screen), set up as any mod using Ixchel
// is: ModDevGradle plus the `com.azane.ixchel` plugin, which depends on, shades and relocates the
// library and lints the templates. Not published.
plugins {
    id("ixchel.java-conventions")
    id("net.neoforged.moddev")
    id("com.azane.ixchel")
}

neoForge {
    version = libs.versions.neoforge.get()

    runs {
        register("client") {
            client()
        }
    }
}

ixchel {
    mod("ixchel_sample", "com.azane.ixchel.sample.shadow")
    lint { enable("i18n/literal-text") }
}
