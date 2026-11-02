package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.template.TagRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The tags templates may use: the HTML subset plus the native elements the mc layer on the
 * classpath declares in its element files. Without an mc layer it is plain HTML.
 *
 * @param containerOnly the tags only container screens accept
 */
public record ElementDeclarations(TagRegistry tags, Set<String> containerOnly) {
    /** Native elements of every screen. */
    public static final String ELEMENTS = "com/azane/ixchel/mc/element/elements.txt";
    /** Native elements of container screens only. */
    public static final String CONTAINER_ELEMENTS = "com/azane/ixchel/mc/container/elements.txt";
    /** A host's own native elements, under {@code assets/<namespace>/}. */
    public static final String HOST_ELEMENTS = "ui/elements.txt";

    public ElementDeclarations {
        containerOnly = Collections.unmodifiableSet(new TreeSet<>(containerOnly));
    }

    /**
     * Reads every element file the project's class loader finds, where copies of the same file on the
     * classpath count once, then the host's own {@code ui/elements.txt} of each namespace the project owns.
     *
     * @throws IllegalArgumentException if a file is malformed
     */
    public static ElementDeclarations load(Project project) {
        ClassLoader loader = project.loader();
        TagRegistry.Builder all = TagRegistry.HTML.extend();
        for (Map.Entry<String, String> f : read(loader, ELEMENTS).entrySet()) {
            all.declare(f.getValue(), f.getKey());
        }
        Set<String> container = new TreeSet<>();
        for (Map.Entry<String, String> f : read(loader, CONTAINER_ELEMENTS).entrySet()) {
            all.declare(f.getValue(), f.getKey());
            container.addAll(TagRegistry.HTML.extend().declare(f.getValue(), f.getKey()).build().tags());
        }
        container.removeAll(TagRegistry.HTML.tags());
        for (String namespace : project.namespaces()) {
            String id = namespace + ":" + HOST_ELEMENTS;
            String text = project.read(id);
            if (text != null) {
                all.declare(text, id);
            }
        }
        return new ElementDeclarations(all.build(), container);
    }

    /** The distinct texts of resource {@code name}, keyed by the URL of their first copy. */
    private static Map<String, String> read(ClassLoader loader, String name) {
        Map<String, String> byText = new LinkedHashMap<>();
        try {
            Enumeration<URL> urls = loader.getResources(name);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream()) {
                    byText.putIfAbsent(new String(in.readAllBytes(), StandardCharsets.UTF_8), url.toString());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + name + ": " + e.getMessage(), e);
        }
        Map<String, String> byFile = new LinkedHashMap<>();
        byText.forEach((text, file) -> byFile.put(file, text));
        return byFile;
    }
}
