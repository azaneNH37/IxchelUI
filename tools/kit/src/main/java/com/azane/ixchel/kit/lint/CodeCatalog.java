package com.azane.ixchel.kit.lint;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.kit.project.ClassFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every diagnostic code the library and the kit define: the constants of each enum implementing
 * {@link DiagCode} under {@code com/azane/ixchel/} in core's classpath root, the kit's, and any other
 * root holding that package (such as the mc layer's), found by reading the class files.
 */
public final class CodeCatalog {
    /** The package path every Ixchel code enum lives under. */
    static final String PACKAGE = "com/azane/ixchel/";

    private CodeCatalog() {
    }

    /** Every code, sorted by id. */
    public static List<DiagCode> all(ClassLoader loader) {
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(ClassFiles.root(DiagCode.class));
        roots.add(ClassFiles.root(KitCode.class));
        try {
            Enumeration<URL> urls = loader.getResources(PACKAGE);
            while (urls.hasMoreElements()) {
                roots.add(ClassFiles.root(urls.nextElement(), PACKAGE));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list the classpath roots of " + PACKAGE, e);
        }
        Set<Class<?>> enums = new LinkedHashSet<>();
        for (Path root : roots) {
            try {
                ClassFiles.scan(root, PACKAGE, (name, bytes) -> {
                    if (ClassFiles.mentions(bytes, DiagCode.class) && ClassFiles.mentions(bytes, Enum.class)) {
                        Class<?> type = load(name, loader);
                        if (type.isEnum() && DiagCode.class.isAssignableFrom(type)) {
                            enums.add(type);
                        }
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read classes under " + root, e);
            }
        }
        List<DiagCode> out = new ArrayList<>();
        for (Class<?> type : enums) {
            for (Object c : type.getEnumConstants()) {
                out.add((DiagCode) c);
            }
        }
        out.sort(Comparator.comparing(DiagCode::id));
        return out;
    }

    /** The code with id {@code id}, or {@code null}. */
    public static DiagCode find(ClassLoader loader, String id) {
        for (DiagCode c : all(loader)) {
            if (c.id().equals(id)) {
                return c;
            }
        }
        return null;
    }

    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("cannot load " + name + " while listing diagnostic codes", e);
        }
    }
}
