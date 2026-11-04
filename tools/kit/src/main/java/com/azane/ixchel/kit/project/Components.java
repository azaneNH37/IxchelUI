package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.core.template.ComponentDef;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.TemplateLoader;
import com.azane.ixchel.core.template.Translations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The component files of a project and of the library. A project component file is one some
 * template links with {@code <link rel="component">}, directly or through another component, or one
 * that has a top-level {@code <template>} ({@link TemplateLoader#declared}). A component file is not a
 * page; it is checked through the pages that use it.
 *
 * <p>The library's components are the tags of the project's {@linkplain Project#library() library}
 * prelude, each declared by its {@value Library#PREFIX} file.
 */
public final class Components {
    private final Set<String> files;
    private final List<ComponentDef> defs;
    private final List<LibraryComponent> library;

    /**
     * A component of the library.
     *
     * @param id  its file, under {@value Library#PREFIX}
     * @param def its declaration
     */
    public record LibraryComponent(String id, ComponentDef def) {
    }

    private Components(Set<String> files, List<ComponentDef> defs, List<LibraryComponent> library) {
        this.files = Collections.unmodifiableSet(files);
        this.defs = List.copyOf(defs);
        this.library = List.copyOf(library);
    }

    /**
     * Declares the library's components, then loads every template of {@code project} as
     * {@link TemplateLoader} would with them and collects what it links.
     *
     * @throws IllegalStateException if a library component's file is missing or not a component file
     */
    public static Components scan(Project project, Library lib, TagRegistry tags) {
        List<LibraryComponent> library = library(project, lib, tags);
        Set<String> files = new TreeSet<>();
        Map<String, ComponentDef> byFile = new TreeMap<>();
        for (String id : project.templates()) {
            String text = project.read(id);
            if (text == null) {
                continue;
            }
            ComponentDef declared = TemplateLoader.declared(id, project::read, lib, tags);
            if (declared != null) {
                files.add(id);
                byFile.putIfAbsent(id, declared);
                continue;
            }
            // Problems are the lint's to report, page by page; here only the link graph matters.
            TemplateLoader.Loaded loaded = TemplateLoader.load(text, id, project::read, lib, tags,
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

    /** The library's components, in tag order. */
    public List<LibraryComponent> library() {
        return library;
    }

    private static List<LibraryComponent> library(Project project, Library lib, TagRegistry tags) {
        List<LibraryComponent> out = new ArrayList<>();
        new TreeMap<>(lib.prelude()).forEach((tag, id) -> {
            ComponentDef def = TemplateLoader.declared(id, project::read, lib, tags);
            if (def == null || !def.name().equals(tag)) {
                throw new IllegalStateException("library file " + id + " of <" + tag + "> is missing or does not declare it");
            }
            out.add(new LibraryComponent(id, def));
        });
        return out;
    }
}
