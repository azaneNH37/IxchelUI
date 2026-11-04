package com.azane.ixchel.core.library;

import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The files of the library under {@value #PREFIX}, a namespace beside the host's {@code ns:path}: the
 * same resolution, read from the classpath relative to an anchor class (so they move with relocation)
 * instead of through the host. Resource packs cannot replace them and they do not reload. Each layer
 * extends the one below it with its own root, the tags every document knows without a link (the
 * prelude) and the library's Java components.
 *
 * <pre>{@code
 * static final Library LIBRARY = Library.CORE.extend()
 *         .root("mc/", McLibrary.class)                       // @ixchel/mc/x.html -> x.html next to McLibrary
 *         .component("mc-player-inventory", PlayerInventoryView.class, PlayerInventoryView::new)
 *         .build();
 * }</pre>
 */
public final class Library {
    /** The start of every library file id. */
    public static final String PREFIX = "@ixchel/";

    /** The core's files: themes under {@code themes/}, effects under {@code effects/}; no components. */
    public static final Library CORE = new Builder(List.of(), Map.of(), Map.of()).root("", Library.class).build();

    private static final Map<String, Optional<String>> TEXTS = new ConcurrentHashMap<>();

    private final List<Root> roots;
    private final Map<String, String> prelude;
    private final Map<String, JavaComponent> components;

    private record Root(String prefix, Class<?> anchor) {
    }

    private Library(List<Root> roots, Map<String, String> prelude, Map<String, JavaComponent> components) {
        this.roots = roots;
        this.prelude = prelude;
        this.components = components;
    }

    /** Whether {@code id} names a library file. */
    public static boolean isLibrary(String id) {
        return id.startsWith(PREFIX);
    }

    /**
     * The text of the library file {@code id}, read once; {@code null} if no root has it.
     *
     * @throws IllegalArgumentException if {@code id} does not start with {@value #PREFIX}
     */
    public String read(String id) {
        if (!isLibrary(id)) {
            throw new IllegalArgumentException("'" + id + "' is not a library file");
        }
        String path = id.substring(PREFIX.length());
        for (Root r : roots) {
            if (path.startsWith(r.prefix)) {
                String rest = path.substring(r.prefix.length());
                return TEXTS.computeIfAbsent(r.anchor.getName() + "/" + rest, k -> Optional.ofNullable(load(r.anchor, rest)))
                        .orElse(null);
            }
        }
        return null;
    }

    /** The tags every document knows without a link, to the library file declaring each. */
    public Map<String, String> prelude() {
        return prelude;
    }

    /** The library's Java components, by file id. */
    public Map<String, JavaComponent> components() {
        return components;
    }

    /** A library with this one's files, prelude and components, plus what the builder adds. */
    public Builder extend() {
        return new Builder(roots, prelude, components);
    }

    private static String load(Class<?> anchor, String path) {
        try (InputStream in = anchor.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static final class Builder {
        private final List<Root> roots;
        private final Map<String, String> prelude;
        private final Map<String, JavaComponent> components;

        private Builder(List<Root> roots, Map<String, String> prelude, Map<String, JavaComponent> components) {
            this.roots = new ArrayList<>(roots);
            this.prelude = new LinkedHashMap<>(prelude);
            this.components = new LinkedHashMap<>(components);
        }

        /**
         * Files whose path after {@value #PREFIX} starts with {@code prefix} ({@code ""} or ending in
         * {@code /}) are read relative to {@code anchor}, without the prefix.
         */
        public Builder root(String prefix, Class<?> anchor) {
            if (!prefix.isEmpty() && !prefix.endsWith("/")) {
                throw new IllegalArgumentException("root prefix '" + prefix + "' must be empty or end with '/'");
            }
            for (Root r : roots) {
                if (r.prefix.equals(prefix)) {
                    throw new IllegalArgumentException("root '" + prefix + "' is already " + r.anchor.getName());
                }
            }
            roots.add(new Root(prefix, anchor));
            return this;
        }

        /** {@code <tag>} is the template component of {@value #PREFIX}{@code path}. */
        public Builder component(String tag, String path) {
            return prelude(tag, PREFIX + path);
        }

        /** {@code <tag>} is the Java component {@code type}, of the file its {@link UiTemplate} names. */
        public Builder component(String tag, Class<? extends ViewModel> type, Supplier<? extends ViewModel> factory) {
            String id = UiTemplate.Ids.single(type);
            if (!isLibrary(id)) {
                throw new IllegalArgumentException(type.getName() + " names '" + id + "', not a file under " + PREFIX);
            }
            prelude(tag, id);
            components.put(id, new JavaComponent(type, factory));
            return this;
        }

        private Builder prelude(String tag, String id) {
            String previous = prelude.putIfAbsent(tag, id);
            if (previous != null) {
                throw new IllegalArgumentException("<" + tag + "> is already " + previous);
            }
            return this;
        }

        public Library build() {
            List<Root> sorted = new ArrayList<>(roots);
            sorted.sort(Comparator.comparingInt((Root r) -> r.prefix.length()).reversed());
            return new Library(List.copyOf(sorted), Map.copyOf(prelude), Map.copyOf(components));
        }
    }
}
