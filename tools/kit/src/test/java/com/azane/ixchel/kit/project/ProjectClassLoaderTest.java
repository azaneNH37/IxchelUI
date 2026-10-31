package com.azane.ixchel.kit.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProjectClassLoaderTest {
    private static final String PAGE = "assets/kitjar/ui/page.html";

    @Test
    void readsResourcesOfJarsAndDirectories(@TempDir Path tmp) throws IOException {
        Path jar = tmp.resolve("lib.jar");
        try (OutputStream file = Files.newOutputStream(jar); JarOutputStream out = new JarOutputStream(file)) {
            out.putNextEntry(new JarEntry(PAGE));
            out.write("<p>jar</p>".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        Path dir = tmp.resolve("classes");
        Files.createDirectories(dir.resolve(PAGE).getParent());
        Files.writeString(dir.resolve(PAGE), "<p>dir</p>");

        try (ProjectClassLoader loader = new ProjectClassLoader(List.of(jar, dir, tmp.resolve("missing")),
                ProjectClassLoaderTest.class.getClassLoader())) {
            URL url = loader.getResource(PAGE);
            assertEquals("jar", url.getProtocol());
            try (InputStream in = url.openStream()) {
                assertEquals("<p>jar</p>", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals(jar, ClassFiles.root(url, PAGE));
            assertEquals(2, Collections.list(loader.getResources(PAGE)).size());
            assertNull(loader.getResource("assets/kitjar/ui/none.html"));

            Project project = new Project(List.of(), List.of(), loader);
            assertEquals("<p>jar</p>", project.read("kitjar:ui/page.html"));
        }
    }
}
