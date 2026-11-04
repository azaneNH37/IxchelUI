package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.library.Library;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * The files of a library, listed from the directories and jars its roots are in, which the runtime
 * never does: it reads each file by id.
 */
public final class LibraryFiles {
    private LibraryFiles() {
    }

    /**
     * The ids of the files of {@code library} whose name ends with {@code extension}, sorted; a file a
     * root with a longer prefix hides is left out.
     *
     * @throws UncheckedIOException if a directory or jar cannot be read
     */
    public static List<String> list(Library library, String extension) {
        Set<String> ids = new TreeSet<>();
        for (Library.Root r : library.roots()) {
            String dir = r.path();
            try {
                for (Path root : roots(r)) {
                    for (String rest : entries(root, dir)) {
                        String path = r.prefix() + rest;
                        if (rest.endsWith(extension) && !hidden(library, r, path)) {
                            ids.add(Library.PREFIX + path);
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("cannot list " + dir + ": " + e.getMessage(), e);
            }
        }
        return List.copyOf(ids);
    }

    /** The classpath roots holding the directory of {@code r}: the anchor's own and any other, such as a separate resources directory. */
    private static Set<Path> roots(Library.Root r) throws IOException {
        Set<Path> out = new LinkedHashSet<>();
        out.add(ClassFiles.root(r.anchor()));
        Enumeration<URL> urls = r.anchor().getClassLoader().getResources(r.path());
        while (urls.hasMoreElements()) {
            out.add(ClassFiles.root(urls.nextElement(), r.path()));
        }
        return out;
    }

    /** The paths of the files under {@code dir} of {@code root}, relative to {@code dir}. */
    private static List<String> entries(Path root, String dir) throws IOException {
        List<String> out = new java.util.ArrayList<>();
        if (Files.isDirectory(root)) {
            Path d = root.resolve(dir);
            if (Files.isDirectory(d)) {
                try (Stream<Path> files = Files.walk(d)) {
                    files.filter(Files::isRegularFile).forEach(p -> out.add(d.relativize(p).toString().replace('\\', '/')));
                }
            }
        } else if (Files.isRegularFile(root)) {
            try (JarFile jar = new JarFile(root.toFile())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    if (!e.isDirectory() && e.getName().startsWith(dir)) {
                        out.add(e.getName().substring(dir.length()));
                    }
                }
            }
        }
        return out;
    }

    private static boolean hidden(Library library, Library.Root owner, String path) {
        for (Library.Root r : library.roots()) {
            if (r.prefix().length() > owner.prefix().length() && path.startsWith(r.prefix())) {
                return true;
            }
        }
        return false;
    }
}
