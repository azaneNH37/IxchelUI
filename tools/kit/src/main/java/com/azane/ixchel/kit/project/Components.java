package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.runtime.UiHosting;
import com.azane.ixchel.core.template.ComponentDef;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.TemplateCode;
import com.azane.ixchel.core.template.TemplateLoader;
import com.azane.ixchel.core.template.Translations;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The component files of a project and of the library. A project component file is one some
 * template links with {@code <link rel="component">}, directly or through another component, or one
 * whose top level is a {@code <template name>} beside {@code <link>}s only. A component file is not a
 * page; it is checked through the pages that use it.
 *
 * <p>The library's components are the mc layer's view models whose {@link UiTemplate} id starts
 * with {@value TemplateLoader#LIBRARY}: the file is the resource of the id's file name in the class's
 * package, and its tag is the name of its {@code <template>}.
 */
public final class Components {
    /** The file id a probe page links a library source under. */
    private static final String PROBED_LIBRARY_FILE = "ixchel-kit:library-component.html";
    private static final String PROBE_PAGE = "ixchel-kit:probe.html";

    private final Set<String> files;
    private final List<ComponentDef> defs;
    private final List<Library> library;

    /**
     * A component of the library.
     *
     * @param hosted what a lint hands the document: no factory
     * @param def    its declaration
     */
    public record Library(UiHosting.LibraryComponent hosted, ComponentDef def) {
    }

    private Components(Set<String> files, List<ComponentDef> defs, List<Library> library) {
        this.files = Collections.unmodifiableSet(files);
        this.defs = List.copyOf(defs);
        this.library = List.copyOf(library);
    }

    /**
     * Finds the library's components, then loads every template of {@code project} as
     * {@link TemplateLoader} would with them and collects what it links.
     *
     * @throws IllegalStateException if a library component's file is missing
     */
    public static Components scan(Project project, TagRegistry tags) {
        List<Library> library = library(project, tags);
        Map<String, TemplateLoader.LibraryFile> libraryFiles = new HashMap<>();
        for (Library l : library) {
            libraryFiles.put(l.hosted().tag(), new TemplateLoader.LibraryFile(l.hosted().id(), l.hosted().source()));
        }
        Set<String> files = new TreeSet<>();
        Map<String, ComponentDef> byFile = new TreeMap<>();
        for (String id : project.templates()) {
            String text = project.read(id);
            if (text == null) {
                continue;
            }
            ComponentDef declared = declared(id, project::read, libraryFiles, tags);
            if (declared != null) {
                files.add(id);
                byFile.putIfAbsent(id, declared);
                continue;
            }
            // Problems are the lint's to report, page by page; here only the link graph matters.
            TemplateLoader.Loaded loaded = TemplateLoader.load(text, id, project::read, libraryFiles, tags,
                    Translations.NONE, new Diagnostics());
            for (String f : loaded.files()) {
                if (!f.equals(id) && f.endsWith(".html")) {
                    files.add(f);
                }
            }
            for (ComponentDef def : loaded.components().values()) {
                if (project.source(def.pos().file()) != null) {
                    byFile.putIfAbsent(def.pos().file(), def);
                }
            }
        }
        return new Components(files, new ArrayList<>(byFile.values()), library);
    }

    /** The ids of the project's component files, sorted. */
    public Set<String> files() {
        return files;
    }

    /** The components declared in the project's own files, each once, by file id. */
    public List<ComponentDef> defs() {
        return defs;
    }

    /** The library's components, by class name. */
    public List<Library> library() {
        return library;
    }

    /** The library components of the mc layer on {@code project}'s classpath. */
    private static List<Library> library(Project project, TagRegistry tags) {
        List<Library> out = new ArrayList<>();
        for (Project.Annotated a : project.libraryAnnotated()) {
            if (!ViewModel.class.isAssignableFrom(a.type())) {
                continue;
            }
            Class<? extends ViewModel> type = a.type().asSubclass(ViewModel.class);
            for (String id : a.patterns()) {
                if (!id.startsWith(TemplateLoader.LIBRARY) || id.indexOf('*') >= 0) {
                    continue;
                }
                String source = resource(type, id.substring(id.lastIndexOf('/') + 1), id);
                ComponentDef def = declared(PROBED_LIBRARY_FILE,
                        f -> f.equals(PROBED_LIBRARY_FILE) ? source : null, Map.of(), tags);
                if (def == null) {
                    throw new IllegalStateException("library file " + id + " of " + type.getName()
                            + " is not a component file");
                }
                out.add(new Library(new UiHosting.LibraryComponent(def.name(), id, () -> source, type, null), def));
            }
        }
        return out;
    }

    private static String resource(Class<?> type, String file, String id) {
        try (InputStream in = type.getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("library file " + id + " of " + type.getName() + " not found: expected "
                        + file + " next to the class");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read library file " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * The component file {@code id} declares, or {@code null} when it is not a component file: its
     * top level is not one {@code <template name>} beside {@code <link>}s only.
     */
    private static ComponentDef declared(String id, Function<String, String> read,
                                         Map<String, TemplateLoader.LibraryFile> libraryFiles, TagRegistry tags) {
        Diagnostics diag = new Diagnostics();
        TemplateLoader.Loaded loaded = TemplateLoader.load("<link rel=\"component\" href=\"" + id + "\"/>", PROBE_PAGE,
                read, libraryFiles, tags, Translations.NONE, diag);
        for (Diagnostic d : diag.entries()) {
            if (d.pos().file().equals(id) && (d.code() == TemplateCode.COMPONENT_FILE_CONTENT
                    || d.code() == TemplateCode.MISSING_COMPONENT_TEMPLATE)) {
                return null;
            }
        }
        for (Map.Entry<String, String> e : loaded.componentFiles().entrySet()) {
            if (e.getValue().equals(id)) {
                return loaded.components().get(e.getKey());
            }
        }
        return null;
    }
}
