package com.azane.ixchel.core.library;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The files under {@value #PREFIX}, a namespace beside the host's {@code ns:path}: the same
 * resolution, read from the classpath through the roots the libraries register instead of through
 * the host. Resource packs cannot replace them and they do not reload. Each file is read by its id;
 * the runtime never lists them.
 */
public final class Library {
    /** The start of every library file id. */
    public static final String PREFIX = "@ixchel/";

    private static final Map<String, Optional<String>> TEXTS = new ConcurrentHashMap<>();

    private final List<Root> roots;

    /**
     * Files whose path after {@value #PREFIX} starts with {@code prefix} are read from {@code dir}
     * next to {@code anchor}, without the prefix, through the anchor's class loader: they move with
     * the anchor's package when a host relocates the library. The whole subtree of {@code dir} is
     * public.
     *
     * @param prefix {@code ""} or ending in {@code /}
     * @param dir    a directory of only views, css and effects, relative to the anchor's package, ending in {@code /}
     */
    public record Root(String prefix, Class<?> anchor, String dir) {
        public Root {
            if (!prefix.isEmpty() && !prefix.endsWith("/")) {
                throw new IllegalArgumentException("root prefix '" + prefix + "' must be empty or end with '/'");
            }
            if (!dir.endsWith("/") || dir.startsWith("/")) {
                throw new IllegalArgumentException("root directory '" + dir + "' must be relative and end with '/'");
            }
        }

        /** The classpath path of the directory, such as {@code com/azane/ixchel/core/library/ui/}. */
        public String path() {
            return anchor.getPackageName().replace('.', '/') + "/" + dir;
        }
    }

    private Library(List<Root> roots) {
        this.roots = roots;
    }

    /**
     * The library of {@code roots}.
     *
     * @throws IllegalArgumentException if two roots have the same prefix
     */
    public static Library of(List<Root> roots) {
        List<Root> sorted = new ArrayList<>(roots);
        sorted.sort(Comparator.comparingInt((Root r) -> r.prefix.length()).reversed());
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).prefix.equals(sorted.get(i - 1).prefix)) {
                throw new IllegalArgumentException("root '" + sorted.get(i).prefix + "' is registered twice");
            }
        }
        return new Library(List.copyOf(sorted));
    }

    /** Whether {@code id} names a library file. */
    public static boolean isLibrary(String id) {
        return id.startsWith(PREFIX);
    }

    /** The roots, longest prefix first; the kit lists the files under them. */
    public List<Root> roots() {
        return roots;
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
                String rest = r.dir + path.substring(r.prefix.length());
                return TEXTS.computeIfAbsent(r.anchor.getName() + "/" + rest, k -> Optional.ofNullable(load(r.anchor, rest)))
                        .orElse(null);
            }
        }
        return null;
    }

    private static String load(Class<?> anchor, String path) {
        try (InputStream in = anchor.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
