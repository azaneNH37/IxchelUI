package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.binding.UiTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * The host project as the kit sees it: resource roots holding {@code assets/<namespace>/…}, the
 * directories of its compiled classes, and the class loader with its runtime classpath.
 *
 * <p>File ids are {@code namespace:path} and name {@code assets/<namespace>/<path>}, as in the mc
 * layer: a namespace with a directory under a resource root reads from the roots only; any other
 * namespace reads from the classpath, as packed resources of another mod or library would be.
 */
public final class Project {
    private static final String ASSETS = "assets";

    private final List<Path> resources;
    private final List<Path> classes;
    private final ClassLoader loader;

    /**
     * A class with {@link UiTemplate} and the ids or patterns it lists.
     */
    public record Annotated(Class<?> type, List<String> patterns) {
        public Annotated {
            patterns = List.copyOf(patterns);
        }
    }

    public Project(List<Path> resources, List<Path> classes, ClassLoader loader) {
        this.resources = List.copyOf(resources);
        this.classes = List.copyOf(classes);
        this.loader = loader;
    }

    public ClassLoader loader() {
        return loader;
    }

    /** Whether some resource root has {@code assets/<namespace>/}: the project's own files. */
    public boolean ownsNamespace(String namespace) {
        if (!isSegment(namespace)) {
            return false;
        }
        for (Path root : resources) {
            if (Files.isDirectory(root.resolve(ASSETS).resolve(namespace))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The file of {@code id} under the resource roots, the first root holding it, or {@code null}
     * when it is not there or {@code id} is not {@code namespace:path}.
     */
    public Path source(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (!isSegment(namespace) || path.isEmpty()) {
            return null;
        }
        for (Path root : resources) {
            Path dir = root.resolve(ASSETS).resolve(namespace).normalize();
            Path file = dir.resolve(path).normalize();
            if (file.startsWith(dir) && Files.isRegularFile(file)) {
                return file;
            }
        }
        return null;
    }

    /**
     * The text of file id {@code id}, {@code null} if there is no such file; the {@code files}
     * function of {@link com.azane.ixchel.core.runtime.UiDocument#lint}.
     *
     * @throws UncheckedIOException if the file exists but cannot be read
     */
    public String read(String id) {
        int colon = id.indexOf(':');
        if (colon < 0) {
            return null;
        }
        String namespace = id.substring(0, colon);
        try {
            if (ownsNamespace(namespace)) {
                Path file = source(id);
                return file == null ? null : Files.readString(file, StandardCharsets.UTF_8);
            }
            if (!isSegment(namespace)) {
                return null;
            }
            try (InputStream in = loader.getResourceAsStream(ASSETS + "/" + namespace + "/" + id.substring(colon + 1))) {
                return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + id + ": " + e.getMessage(), e);
        }
    }

    /** The namespaces under the resource roots, sorted. */
    public Set<String> namespaces() {
        Set<String> out = new TreeSet<>();
        for (Path root : resources) {
            Path assets = root.resolve(ASSETS);
            if (!Files.isDirectory(assets)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(assets)) {
                namespaces.filter(Files::isDirectory).forEach(ns -> out.add(ns.getFileName().toString()));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot list " + assets + ": " + e.getMessage(), e);
            }
        }
        return out;
    }

    /** The ids of every {@code .html} file under {@code assets/<namespace>/} of the resource roots, sorted. */
    public List<String> templates() {
        Set<String> ids = new TreeSet<>();
        for (Path root : resources) {
            Path assets = root.resolve(ASSETS);
            if (!Files.isDirectory(assets)) {
                continue;
            }
            try (Stream<Path> namespaces = Files.list(assets)) {
                for (Path ns : namespaces.filter(Files::isDirectory).toList()) {
                    try (Stream<Path> files = Files.walk(ns)) {
                        files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".html"))
                                .forEach(p -> ids.add(ns.getFileName() + ":"
                                        + ns.relativize(p).toString().replace('\\', '/')));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("cannot list " + assets + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Every class under the classes directories carrying {@link UiTemplate}, loaded without
     * initialization through {@link #loader()}, in class name order.
     *
     * @throws IllegalStateException if such a class cannot be loaded from the classpath
     */
    public List<Annotated> annotated() {
        List<Annotated> out = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        for (Path root : classes) {
            try {
                ClassFiles.scan(root, "", (name, bytes) -> {
                    if (ClassFiles.mentions(bytes, UiTemplate.class) && seen.add(name)) {
                        Class<?> type = load(name);
                        UiTemplate t = type.getAnnotation(UiTemplate.class);
                        if (t != null) {
                            out.add(new Annotated(type, List.of(t.value())));
                        }
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read classes under " + root + ": " + e.getMessage(), e);
            }
        }
        out.sort((a, b) -> a.type().getName().compareTo(b.type().getName()));
        return out;
    }

    private Class<?> load(String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("cannot load " + name + " (" + e + "); pass the project's runtime "
                    + "classpath with --classpath", e);
        }
    }

    /** A namespace or similar single path segment: non-empty, no separators. */
    private static boolean isSegment(String s) {
        return !s.isEmpty() && s.indexOf('/') < 0 && s.indexOf('\\') < 0 && !s.equals(".") && !s.equals("..");
    }
}
