package com.azane.ixchel.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * {@code com.azane.ixchel}: makes a ModDevGradle mod a host of Ixchel. Applied next to
 * {@code net.neoforged.moddev.legacyforge} (Forge 1.20.1) or {@code net.neoforged.moddev}
 * (NeoForge 1.21.1); see {@link IxchelExtension} for what it sets up.
 */
public final class IxchelPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("com.gradleup.shadow");
        IxchelExtension ixchel = project.getExtensions().create("ixchel", IxchelExtension.class, project);
        // Each branch runs only once its ModDevGradle plugin is applied; the other's classes never load.
        project.getPluginManager().withPlugin("net.neoforged.moddev.legacyforge",
                p -> ixchel.attach(Loader.FORGE_1_20_1));
        project.getPluginManager().withPlugin("net.neoforged.moddev",
                p -> ixchel.attach(Loader.NEOFORGE_1_21_1));
        project.afterEvaluate(p -> ixchel.requireAttached());
    }
}
