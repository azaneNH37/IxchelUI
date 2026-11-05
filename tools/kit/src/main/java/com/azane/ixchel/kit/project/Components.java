package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.core.runtime.UiHosting;
import com.azane.ixchel.core.view.ComponentDef;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.core.link.Linker;
import com.azane.ixchel.core.view.Translations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The component and theme files of a project, and the library's components. A project component file
 * is one some view links, directly or through another component, or one whose root is
 * {@code <component>} ({@link Linker#declared}). A component file is not a page; it is checked through
 * the pages that use it. A project theme is a {@code @partial;} css file some page's stylesheet links,
 * directly or through other partials.
 *
 * <p>The library's components are its {@value Library#PREFIX} files whose root is {@code <component>}:
 * view components and the files of its Java components.
 */
public final class Components {
    private final Set<String> files;
    private final Set<String> themes;
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

    private Components(Set<String> files, Set<String> themes, List<ComponentDef> defs, List<LibraryComponent> library) {
        this.files = Collections.unmodifiableSet(files);
        this.themes = Collections.unmodifiableSet(themes);
        this.defs = List.copyOf(defs);
        this.library = List.copyOf(library);
    }

    /**
     * Declares the library's components, then loads every view of {@code project} as
     * {@link Linker} would with them and collects what it links.
     *
     * @throws IllegalStateException if a library Java component file is missing or declares nothing
     */
    public static Components scan(Project project, UiHosting hosting, TagRegistry tags) {
        Library lib = hosting.library();
        Map<String, ComponentDef> libraryDefs = new TreeMap<>();
        for (String id : LibraryFiles.list(lib, ".html")) {
            ComponentDef def = Linker.declared(id, project::read, lib, tags);
            if (def != null) {
                libraryDefs.put(id, def);
            }
        }
        for (String id : hosting.components().keySet()) {
            if (Library.isLibrary(id) && !libraryDefs.containsKey(id)) {
                throw new IllegalStateException("library file " + id + " of a Java component is missing or declares no component");
            }
        }
        List<LibraryComponent> library = new ArrayList<>();
        libraryDefs.forEach((id, def) -> library.add(new LibraryComponent(id, def)));
        library.sort(Comparator.comparing(c -> c.def().name()));

        Set<String> files = new TreeSet<>();
        Set<String> themes = new TreeSet<>();
        Map<String, ComponentDef> byFile = new TreeMap<>();
        for (String id : project.views()) {
            String text = project.read(id);
            if (text == null) {
                continue;
            }
            ComponentDef declared = Linker.declared(id, project::read, lib, tags);
            if (declared != null) {
                files.add(id);
                byFile.putIfAbsent(id, declared);
                continue;
            }
            // Problems are the lint's to report, page by page; here only the link graph matters.
            Linker.Loaded loaded = Linker.load(text, id, project::read, lib, tags,
                    Translations.NONE, new Diagnostics());
            for (String f : loaded.files()) {
                if (!f.equals(id) && f.endsWith(".html")) {
                    files.add(f);
                }
            }
            for (Linker.Layer layer : loaded.layers()) {
                if (layer.partial() && layer.views().contains(id) && !Library.isLibrary(layer.sheet().id())) {
                    themes.add(layer.sheet().id());
                }
            }
            for (ComponentDef def : loaded.components().values()) {
                if (project.source(def.pos().file()) != null) {
                    byFile.putIfAbsent(def.pos().file(), def);
                }
            }
        }
        return new Components(files, themes, new ArrayList<>(byFile.values()), library);
    }

    /** The ids of the project's component files, sorted. */
    public Set<String> files() {
        return files;
    }

    /** The ids of the project's theme files, sorted. */
    public Set<String> themes() {
        return themes;
    }

    /** The components declared in the project's own files, each once, by file id. */
    public List<ComponentDef> defs() {
        return defs;
    }

    /** The library's components, in name order. */
    public List<LibraryComponent> library() {
        return library;
    }
}
