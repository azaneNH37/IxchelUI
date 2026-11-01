package com.azane.ixchel.gradle;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import net.neoforged.moddevgradle.legacyforge.dsl.ObfuscationExtension;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/** The Forge 1.20.1 branch: Ixchel's 1.20.1 jars are published in SRG names, and so is the mod. */
final class LegacyForge {
    private LegacyForge() {
    }

    /**
     * The published library, remapped to Mojang names for development. Remapping is not transitive,
     * so both Minecraft-facing modules are listed; the core has no Minecraft names.
     */
    static void addLibrary(Project project, Configuration shade, String version) {
        Configuration modShade = obfuscation(project).createRemappingConfiguration(shade);
        var deps = project.getDependencies();
        deps.add(modShade.getName(), "com.azane.ixchel:" + Loader.FORGE_1_20_1.loaderModule + ":" + version);
        deps.add(modShade.getName(), "com.azane.ixchel:" + Loader.FORGE_1_20_1.mcModule + ":" + version);
        deps.add(shade.getName(), "com.azane.ixchel:ixchel-core:" + version);
    }

    /** The shaded jar, library included, back to SRG names; it replaces the plain jar's publication. */
    static void reobfuscate(Project project, TaskProvider<ShadowJar> shadowJar, SourceSet main) {
        obfuscation(project).reobfuscate(shadowJar, main);
    }

    private static ObfuscationExtension obfuscation(Project project) {
        return project.getExtensions().getByType(ObfuscationExtension.class);
    }
}
