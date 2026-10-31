package com.azane.ixchel.kit.project;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * Loads the host project's classes and resources from its runtime classpath entries, directories
 * and jars, below the kit's own loader: parent first, so core's types the kit compiles against stay
 * the same classes, and copies of core on the project's classpath are shadowed.
 *
 * <p>Jars are read without signature verification. A development Minecraft jar is patched while it
 * keeps the loader's signature files, which the JDK's class loaders reject; the kit only reads
 * declarations from these classes and never runs them.
 *
 * <p>A jar resource's URL is {@code jar:<jar uri>!/<path>} and reads through this loader's open jar.
 */
public final class ProjectClassLoader extends ClassLoader implements Closeable {
    static {
        registerAsParallelCapable();
    }

    /** One classpath entry: a directory, or an open jar. */
    private record Entry(Path dir, JarFile jar, String jarUri) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final URLStreamHandler jarHandler = new URLStreamHandler() {
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            String spec = u.getPath();
            int sep = spec.indexOf("!/");
            String uri = spec.substring(0, sep);
            String name = spec.substring(sep + 2);
            for (Entry e : entries) {
                if (e.jar() != null && e.jarUri().equals(uri)) {
                    JarEntry je = e.jar().getJarEntry(name);
                    if (je == null) {
                        throw new IOException("no " + name + " in " + uri);
                    }
                    return new URLConnection(u) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() throws IOException {
                            return e.jar().getInputStream(je);
                        }
                    };
                }
            }
            throw new IOException("jar " + uri + " is not on the project classpath");
        }
    };

    /**
     * @param classpath the entries in lookup order; missing entries are skipped
     * @throws UncheckedIOException if a jar cannot be opened
     */
    public ProjectClassLoader(List<Path> classpath, ClassLoader parent) {
        super("ixchel-kit-project", parent);
        try {
            for (Path p : classpath) {
                if (Files.isDirectory(p)) {
                    entries.add(new Entry(p, null, null));
                } else if (Files.isRegularFile(p)) {
                    JarFile jar = new JarFile(p.toFile(), false, ZipFile.OPEN_READ, Runtime.version());
                    entries.add(new Entry(null, jar, p.toUri().toString()));
                }
            }
        } catch (IOException e) {
            close();
            throw new UncheckedIOException("cannot open a classpath entry: " + e.getMessage(), e);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        try {
            for (Entry e : entries) {
                byte[] bytes = read(e, path);
                if (bytes != null) {
                    return defineClass(name, bytes, 0, bytes.length);
                }
            }
        } catch (IOException ex) {
            throw new ClassNotFoundException(name + ": " + ex.getMessage(), ex);
        }
        throw new ClassNotFoundException(name);
    }

    @Override
    protected URL findResource(String name) {
        for (Entry e : entries) {
            URL url = url(e, name);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) {
        List<URL> out = new ArrayList<>();
        for (Entry e : entries) {
            URL url = url(e, name);
            if (url != null) {
                out.add(url);
            }
        }
        return Collections.enumeration(out);
    }

    /** Closes the open jars; classes already loaded stay usable, resources of jars do not. */
    @Override
    public void close() {
        for (Entry e : entries) {
            if (e.jar() != null) {
                try {
                    e.jar().close();
                } catch (IOException ignored) {
                    // Read-only jars: nothing is lost if closing fails.
                }
            }
        }
    }

    private static byte[] read(Entry e, String path) throws IOException {
        if (e.dir() != null) {
            Path file = e.dir().resolve(path);
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        }
        JarEntry je = e.jar().getJarEntry(path);
        if (je == null || je.isDirectory()) {
            return null;
        }
        try (InputStream in = e.jar().getInputStream(je)) {
            return in.readAllBytes();
        }
    }

    @SuppressWarnings("deprecation") // URL(String, String, int, String, URLStreamHandler): the only way to attach a handler on Java 17
    private URL url(Entry e, String name) {
        try {
            if (e.dir() != null) {
                Path file = e.dir().resolve(name);
                return Files.exists(file) ? file.toUri().toURL() : null;
            }
            if (e.jar().getJarEntry(name) == null) {
                return null;
            }
            return new URL("jar", "", -1, e.jarUri() + "!/" + name, jarHandler);
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("cannot name " + name + " in a classpath entry", ex);
        }
    }
}
