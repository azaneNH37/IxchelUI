package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.diag.Diagnostics;
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
 * The component files of a project: the {@code .html} files some template links with
 * {@code <link rel="component">}, directly or through another component. A component file is not a
 * page; it is checked through the pages that link it.
 */
public final class Components {
    private final Set<String> files;
    private final List<ComponentDef> defs;

    private Components(Set<String> files, List<ComponentDef> defs) {
        this.files = Collections.unmodifiableSet(files);
        this.defs = List.copyOf(defs);
    }

    /** Loads every template of {@code project} as {@link TemplateLoader} would and collects what it links. */
    public static Components scan(Project project, TagRegistry tags) {
        Set<String> files = new TreeSet<>();
        Map<String, ComponentDef> byFile = new TreeMap<>();
        for (String id : project.templates()) {
            String text = project.read(id);
            if (text == null) {
                continue;
            }
            // Problems are the lint's to report, page by page; here only the link graph matters.
            TemplateLoader.Loaded loaded = TemplateLoader.load(text, id, project::read, tags, Translations.NONE, new Diagnostics());
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
        return new Components(files, new ArrayList<>(byFile.values()));
    }

    /** The ids of the files linked as components, sorted. */
    public Set<String> files() {
        return files;
    }

    /** The components declared in the project's own files, each once, by file id. */
    public List<ComponentDef> defs() {
        return defs;
    }
}
