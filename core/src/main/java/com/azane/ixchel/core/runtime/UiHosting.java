package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.JavaComponent;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.core.view.TagRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What one document sees beyond its files: the registries of the core, the library layers and its
 * host, combined. A linter registers the same Java components without factories: it checks against
 * them and builds nothing.
 */
public final class UiHosting {
    /** The core alone. */
    public static final UiHosting NONE = of(UiRegistry.CORE);

    private final Library library;
    private final TagRegistry tags;
    private final TagRegistry containerTags;
    private final Map<String, Object> implementations;
    private final Map<String, JavaComponent> components;

    private UiHosting(Library library, TagRegistry tags, TagRegistry containerTags, Map<String, Object> implementations,
                      Map<String, JavaComponent> components) {
        this.library = library;
        this.tags = tags;
        this.containerTags = containerTags;
        this.implementations = implementations;
        this.components = components;
    }

    /**
     * The registries' union.
     *
     * @throws IllegalArgumentException if two register the same root prefix, tag or component file
     */
    public static UiHosting of(UiRegistry... registries) {
        List<Library.Root> roots = new ArrayList<>();
        TagRegistry.Builder tags = TagRegistry.builder();
        TagRegistry.Builder containerTags = TagRegistry.builder();
        Map<String, Object> implementations = new HashMap<>();
        Map<String, JavaComponent> components = new HashMap<>();
        for (UiRegistry r : registries) {
            roots.addAll(r.roots());
            for (UiRegistry.Element e : r.elements()) {
                containerTags.add(e.type());
                if (!e.containerOnly()) {
                    tags.add(e.type());
                }
                if (e.implementation() != null) {
                    implementations.put(e.type().tag(), e.implementation());
                }
            }
            r.components().forEach((id, c) -> {
                if (components.putIfAbsent(id, c) != null) {
                    throw new IllegalArgumentException(id + " already has a Java component");
                }
            });
        }
        return new UiHosting(Library.of(roots), tags.build(), containerTags.build(), Map.copyOf(implementations),
                Map.copyOf(components));
    }

    /** The {@value Library#PREFIX} files. */
    public Library library() {
        return library;
    }

    /** The elements a document may use; container screens also accept the container-only ones. */
    public TagRegistry tags(boolean container) {
        return container ? containerTags : tags;
    }

    /** The object registered as the implementation of {@code tag}, or {@code null}. */
    public Object implementation(String tag) {
        return implementations.get(tag);
    }

    /** The Java halves of component files, by file id. */
    public Map<String, JavaComponent> components() {
        return components;
    }

    /** This hosting for a document whose components come from {@code componentFiles}, file ids by component name. */
    Bindings.Hosted forBindings(Map<String, String> componentFiles) {
        Map<String, Bindings.Hosted.Model> models = new HashMap<>();
        componentFiles.forEach((name, file) -> {
            JavaComponent c = components.get(file);
            if (c != null) {
                models.put(name, new Bindings.Hosted.Model(c.type(), c.factory()));
            }
        });
        return new Bindings.Hosted(models);
    }
}
