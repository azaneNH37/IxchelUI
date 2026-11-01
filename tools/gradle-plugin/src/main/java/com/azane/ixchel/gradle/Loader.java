package com.azane.ixchel.gradle;

/** The ModDevGradle flavours the plugin supports, each with the one Minecraft version Ixchel ships for it. */
enum Loader {
    FORGE_1_20_1("net.neoforged.moddev.legacyforge", "legacyForge", "ixchel-forge-1.20.1",
            "ixchel-mc-1.20.1", "com.azane.ixchel.forge"),
    NEOFORGE_1_21_1("net.neoforged.moddev", "neoForge", "ixchel-neoforge-1.21.1",
            "ixchel-mc-1.21.1", "com.azane.ixchel.neoforge");

    /** The ModDevGradle plugin id. */
    final String pluginId;
    /** The name of ModDevGradle's extension. */
    final String extension;
    /** The loader module, which carries the mc layer and the core. */
    final String loaderModule;
    final String mcModule;
    /** The loader module's package; the mc layer and the core are {@code com.azane.ixchel.mc} and {@code .core}. */
    final String loaderPackage;

    Loader(String pluginId, String extension, String loaderModule, String mcModule, String loaderPackage) {
        this.pluginId = pluginId;
        this.extension = extension;
        this.loaderModule = loaderModule;
        this.mcModule = mcModule;
        this.loaderPackage = loaderPackage;
    }
}
