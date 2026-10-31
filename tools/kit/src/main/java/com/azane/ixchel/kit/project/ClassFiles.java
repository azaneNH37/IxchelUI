package com.azane.ixchel.kit.project;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The class files of a classpath root, a classes directory or a jar, read as bytes so a caller can
 * pick the classes worth loading before it loads any.
 */
public final class ClassFiles {
    /** Receives one class file. */
    @FunctionalInterface
    public interface Visitor {
        /** @param binaryName the class's binary name, such as {@code a.b.Outer$Inner} */
        void visit(String binaryName, byte[] bytes);
    }

    private ClassFiles() {
    }

    /**
     * Visits every class file under {@code root} whose path starts with {@code prefix} (an
     * internal package path such as {@code com/azane/ixchel/}, or {@code ""} for all), in path order;
     * {@code module-info} and {@code package-info} are skipped. A missing root is empty.
     */
    public static void scan(Path root, String prefix, Visitor visitor) throws IOException {
        if (Files.isDirectory(root)) {
            List<Path> files;
            try (Stream<Path> s = Files.walk(root)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".class")).sorted().toList();
            }
            for (Path p : files) {
                String path = root.relativize(p).toString().replace('\\', '/');
                if (path.startsWith(prefix) && !isInfo(path)) {
                    visitor.visit(binaryName(path), Files.readAllBytes(p));
                }
            }
        } else if (Files.isRegularFile(root)) {
            try (JarFile jar = new JarFile(root.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    String path = e.getName();
                    if (!e.isDirectory() && path.endsWith(".class") && path.startsWith(prefix) && !isInfo(path)) {
                        try (InputStream in = jar.getInputStream(e)) {
                            visitor.visit(binaryName(path), in.readAllBytes());
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether a class file's constant pool mentions {@code type}, as an annotation, a supertype or
     * any other reference; a cheap test that avoids loading unrelated classes.
     */
    public static boolean mentions(byte[] classFile, Class<?> type) {
        byte[] name = type.getName().replace('.', '/').getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= classFile.length - name.length; i++) {
            for (int k = 0; k < name.length; k++) {
                if (classFile[i + k] != name[k]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** The classpath root, directory or jar, that holds {@code type}. */
    public static Path root(Class<?> type) {
        String path = type.getName().replace('.', '/') + ".class";
        URL url = type.getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalStateException("cannot locate the class file of " + type.getName());
        }
        return root(url, path);
    }

    /**
     * The classpath root, directory or jar, that {@code url} was found in as the resource
     * {@code resourcePath}.
     */
    public static Path root(URL url, String resourcePath) {
        try {
            if (url.getProtocol().equals("jar")) {
                String s = url.getPath();
                return Path.of(URI.create(s.substring(0, s.indexOf("!/"))));
            }
            Path p = Path.of(url.toURI());
            int depth = (int) Stream.of(resourcePath.split("/")).filter(s -> !s.isEmpty()).count();
            for (int i = 0; i < depth; i++) {
                p = p.getParent();
            }
            return p;
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the classpath root of " + url, e);
        }
    }

    private static boolean isInfo(String path) {
        return path.endsWith("module-info.class") || path.endsWith("package-info.class");
    }

    private static String binaryName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }
}
