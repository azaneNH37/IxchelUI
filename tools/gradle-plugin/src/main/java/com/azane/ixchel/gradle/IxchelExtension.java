package com.azane.ixchel.gradle;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import net.neoforged.moddevgradle.dsl.ModDevExtension;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * The {@code ixchel} block. As soon as ModDevGradle is applied the plugin
 * <ul>
 * <li>adds Ixchel to the mod: a {@code shade} configuration that {@code implementation} extends,
 *     holding the library (remapped from SRG on Forge 1.20.1); the jars load in development as
 *     game libraries, and taffy, a plain Java library, is added to the runs;</li>
 * <li>makes the shaded jar the mod's jar ({@code shadowJar}, no classifier; the plain jar becomes
 *     {@code -slim}), reobfuscated on Forge 1.20.1;</li>
 * <li>registers {@code ixchelLint}, the offline view lint, on {@code check}.</li>
 * </ul>
 * {@link #mod} names the mod and where the library is relocated; {@link #playtests} adds the
 * in-game playtests; {@link #lint} configures the lint.
 */
public abstract class IxchelExtension {
    private static final String GROUP = "com.azane.ixchel";
    private static final String TAFFY_PACKAGE = "dev.vfyjxf.taffy";

    private final Project project;
    private final LintSpec lint;
    private Loader loader;
    private ModDevExtension modDev;
    private String modId;

    @Inject
    public IxchelExtension(Project project) {
        this.project = project;
        this.lint = project.getObjects().newInstance(LintSpec.class);
    }

    /** The {@code lint} block of {@code ixchelLint}. */
    public abstract static class LintSpec {
        /** The opt-in diagnostic codes the lint also checks. */
        public abstract SetProperty<String> getEnabled();

        /** Turns on opt-in checks by code id, such as {@code i18n/literal-text}. */
        public void enable(String... codes) {
            getEnabled().addAll(codes);
        }
    }

    /** Configures {@code ixchelLint}: {@code ixchel { lint { enable("i18n/literal-text") } }}. */
    public void lint(Action<? super LintSpec> action) {
        action.execute(lint);
    }

    /**
     * Names the mod, registers it with ModDevGradle on the main source set and relocates the library
     * under {@code shadePackage}: Ixchel to {@code <shadePackage>.ixchel}, taffy to
     * {@code <shadePackage>.taffy}. Every run gets {@code ixchel.devResources.<modId>}, so views
     * and stylesheets reload from {@code src/main/resources}.
     */
    public void mod(String modId, String shadePackage) {
        requireAttached();
        if (this.modId != null) {
            throw new GradleException("ixchel.mod(…) is already set to " + this.modId);
        }
        this.modId = modId;
        SourceSet main = sourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        modDev.getMods().register(modId, m -> m.sourceSet(main));
        String resources = project.file("src/main/resources").getAbsolutePath();
        modDev.getRuns().configureEach(run -> run.systemProperty("ixchel.devResources." + modId, resources));

        project.getTasks().named("shadowJar", ShadowJar.class, jar -> {
            for (String pkg : List.of(GROUP + ".core", GROUP + ".mc", loader.loaderPackage)) {
                jar.relocate(pkg, shadePackage + ".ixchel" + pkg.substring(GROUP.length()));
            }
            jar.relocate(TAFFY_PACKAGE, shadePackage + ".taffy");
        });
    }

    /**
     * Adds the in-game playtests: a {@code playtest} source set that sees {@code main} and holds the
     * mod {@code playtestModId}, compiled on every {@code check}. With
     * {@code -Pplaytest=<name>[,<name>…]|all} (no value: {@code all}) the playtest mod is loaded,
     * client runs start from the playtest source set and {@code ixchel.playtest.<modId>} carries the
     * selection. {@code -Precord=<name>[,<name>…]|all} does the same as a recording run
     * ({@code ixchel.record.<modId>}): the selected playtests' takes become videos, which needs
     * ffmpeg; {@code -Precord.fps}, {@code .ffmpeg}, {@code .window} ({@code <w>x<h>}),
     * {@code .guiScale}, {@code .scale} and {@code .crf} become {@code ixchel.recording.*}. The two
     * exclude each other.
     */
    public void playtests(String playtestModId) {
        if (modId == null) {
            throw new GradleException("call ixchel.mod(…) before ixchel.playtests(…)");
        }
        SourceSet main = sourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet playtest = sourceSets().create("playtest", s -> {
            s.setCompileClasspath(s.getCompileClasspath().plus(main.getOutput()).plus(main.getCompileClasspath()));
            s.setRuntimeClasspath(s.getRuntimeClasspath().plus(main.getOutput()).plus(main.getRuntimeClasspath()));
        });
        modDev.addModdingDependenciesTo(playtest);
        project.getTasks().named("check", t -> t.dependsOn(playtest.getClassesTaskName()));

        Provider<String> tested = project.getProviders().gradleProperty("playtest");
        Provider<String> recorded = project.getProviders().gradleProperty("record");
        if (tested.isPresent() && recorded.isPresent()) {
            throw new GradleException("-Pplaytest and -Precord exclude each other");
        }
        if (!tested.isPresent() && !recorded.isPresent()) {
            return;
        }
        Map<String, String> properties = new LinkedHashMap<>();
        if (tested.isPresent()) {
            properties.put("ixchel.playtest." + modId, tested.get().isEmpty() ? "all" : tested.get());
        } else {
            properties.put("ixchel.record." + modId, recorded.get().isEmpty() ? "all" : recorded.get());
            for (String setting : List.of("fps", "ffmpeg", "window", "guiScale", "scale", "crf")) {
                Provider<String> value = project.getProviders().gradleProperty("record." + setting);
                if (value.isPresent()) {
                    properties.put("ixchel.recording." + setting, value.get());
                }
            }
        }
        modDev.getMods().register(playtestModId, m -> m.sourceSet(playtest));
        modDev.getRuns().configureEach(run -> {
            Provider<Boolean> client = run.getType().map("client"::equals).orElse(false);
            run.getSourceSet().set(client.map(c -> c ? playtest : main));
            run.getSystemProperties().putAll(client.map(c -> c ? properties : Map.of()));
        });
    }

    /** Called once ModDevGradle is applied: everything that needs no name from the mod. */
    void attach(Loader loader) {
        if (this.loader != null) {
            throw new GradleException("com.azane.ixchel supports one of " + this.loader.pluginId + " and "
                    + loader.pluginId + " per project");
        }
        this.loader = loader;
        this.modDev = (ModDevExtension) project.getExtensions().getByName(loader.extension);
        Properties build = buildInfo();
        String version = build.getProperty("version");
        boolean inRepository = project.getRootProject().findProject(":ixchel-core") != null;

        // Minecraft ships fastutil, taffy's only dependency.
        Configuration shade = project.getConfigurations().create("shade", c -> {
            c.setCanBeConsumed(false);
            c.exclude(Map.of("group", "it.unimi.dsi", "module", "fastutil"));
            c.attributes(a -> {
                a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                a.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                a.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
                a.attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
            });
        });
        project.getConfigurations().getByName("implementation").extendsFrom(shade);
        var deps = project.getDependencies();
        if (inRepository) {
            // The repository's own modules: their development jars are already in Mojang names.
            deps.add(shade.getName(), deps.project(Map.of("path", ":" + loader.loaderModule)));
        } else if (loader == Loader.FORGE_1_20_1) {
            LegacyForge.addLibrary(project, shade, version);
        } else {
            deps.add(shade.getName(), GROUP + ":" + loader.loaderModule + ":" + version);
        }
        // Plain Java libraries are not loaded in ModDevGradle runs unless added here; ModDevGradle
        // creates this configuration once modding is enabled (a version is set).
        String taffy = build.getProperty("taffy");
        project.getConfigurations().matching(c -> c.getName().equals("additionalRuntimeClasspath"))
                .configureEach(c -> c.getDependencies().add(deps.create(taffy)));

        TaskProvider<ShadowJar> shadowJar = project.getTasks().named("shadowJar", ShadowJar.class, jar -> {
            jar.getConfigurations().set(List.of(shade));
            jar.getArchiveClassifier().set("");
            jar.exclude("module-info.class", "META-INF/versions/*/module-info.class",
                    "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA");
            jar.mergeServiceFiles();
        });
        project.getTasks().named("jar", Jar.class, jar -> jar.getArchiveClassifier().set("slim"));
        project.getTasks().named("assemble", t -> t.dependsOn(shadowJar));
        if (loader == Loader.FORGE_1_20_1) {
            LegacyForge.reobfuscate(project, shadowJar, sourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME));
        }

        Configuration kit = project.getConfigurations().create("ixchelKit", c -> c.setCanBeConsumed(false));
        Dependency kitDependency = inRepository
                ? deps.project(Map.of("path", ":ixchel-kit"))
                : deps.create(GROUP + ":ixchel-kit:" + version);
        kit.getDependencies().add(kitDependency);
        registerLint(kit);
    }

    void requireAttached() {
        if (loader == null) {
            throw new GradleException("com.azane.ixchel needs net.neoforged.moddev.legacyforge (Forge 1.20.1) or "
                    + "net.neoforged.moddev (NeoForge 1.21.1) applied in the same project");
        }
    }

    private void registerLint(Configuration kit) {
        SourceSet main = sourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        TaskProvider<JavaExec> lint = project.getTasks().register("ixchelLint", JavaExec.class, t -> {
            t.setGroup("verification");
            t.setDescription("Lints the UI views against their view models.");
            t.setClasspath(kit);
            t.getMainClass().set("com.azane.ixchel.kit.Main");
            FileCollection runtime = main.getRuntimeClasspath();
            t.getInputs().files(runtime);
            SetProperty<String> enabled = this.lint.getEnabled();
            t.getInputs().property("enabled", enabled);
            t.getArgumentProviders().add(new LintArguments(project.file("src/main/resources"), runtime, enabled));
        });
        project.getTasks().named("check", t -> t.dependsOn(lint));
    }

    /**
     * The kit's arguments, resolved when the task runs. A class with plain fields, not a record: the
     * configuration cache restores fields reflectively, which a record's final fields refuse.
     */
    private static final class LintArguments implements CommandLineArgumentProvider {
        private File resources;
        private FileCollection runtime;
        private SetProperty<String> enabled;

        LintArguments(File resources, FileCollection runtime, SetProperty<String> enabled) {
            this.resources = resources;
            this.runtime = runtime;
            this.enabled = enabled;
        }

        @Override
        public Iterable<String> asArguments() {
            List<String> args = new ArrayList<>(List.of("lint", "--resources", resources.getPath(),
                    "--classpath", runtime.getAsPath()));
            if (!enabled.get().isEmpty()) {
                args.add("--enable");
                args.addAll(enabled.get());
            }
            return args;
        }
    }

    private SourceSetContainer sourceSets() {
        return project.getExtensions().getByType(SourceSetContainer.class);
    }

    private static Properties buildInfo() {
        Properties p = new Properties();
        try (InputStream in = IxchelExtension.class.getResourceAsStream("build.properties")) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }
}
