package com.azane.ixchel.kit.project;

import com.azane.ixchel.mc.kitfixture.FixtureMcLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A stand-in mc layer: a classpath root with the class file the kit finds the mc layer by and the
 * fixture's library holder. The classes and the template still load from the test classes.
 */
public final class FixtureLibrary {
    private FixtureLibrary() {
    }

    /** Writes the stand-in root into {@code dir} and returns it. */
    public static Path mcLayer(Path dir) throws IOException {
        Path marker = dir.resolve("com/azane/ixchel/mc/element/McElements.class");
        Files.createDirectories(marker.getParent());
        Files.write(marker, new byte[0]);
        String path = FixtureMcLibrary.class.getName().replace('.', '/') + ".class";
        Path copy = dir.resolve(path);
        Files.createDirectories(copy.getParent());
        Files.copy(ClassFiles.root(FixtureMcLibrary.class).resolve(path), copy);
        return dir;
    }
}
