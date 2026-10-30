package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.style.StyleSheet;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stylesheets shipped in the library, read from the classpath next to this class (so they
 * survive relocation) and compiled once; a problem in one is a bug in the library. They do not
 * follow resource reloads.
 *
 * <p>The user-agent sheet holds the defaults of the form controls and {@code <dialog>}. A theme is
 * {@code themes/theme.css}, the rules every theme shares, written against custom properties, then
 * the theme's own sheet, which sets those properties on {@code :root}.
 */
final class LibrarySheets {
    static final StyleSheet USER_AGENT = compile("user-agent.css");

    /** Library stylesheet name, as linked after {@code @ixchel/}, to its files in cascade order. */
    private static final Map<String, List<String>> THEMES = Map.of(
            "vanilla.css", List.of("themes/theme.css", "themes/vanilla.css"),
            "dark.css", List.of("themes/theme.css", "themes/dark.css"));

    private static final Map<String, StyleSheet> COMPILED = new ConcurrentHashMap<>();

    private LibrarySheets() {
    }

    /** The files of the library stylesheet {@code name}, {@code null} if there is none. */
    static List<String> files(String name) {
        return THEMES.get(name);
    }

    /** The names a page can link after {@code @ixchel/}, sorted; for error messages. */
    static List<String> names() {
        return THEMES.keySet().stream().sorted().toList();
    }

    static StyleSheet sheet(String file) {
        return COMPILED.computeIfAbsent(file, LibrarySheets::compile);
    }

    private static StyleSheet compile(String file) {
        try (InputStream in = LibrarySheets.class.getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("missing " + file);
            }
            Diagnostics diag = new Diagnostics();
            String css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(css, file, diag), CssProperties.REGISTRY, diag);
            if (diag.hasErrors()) {
                throw new IllegalStateException("invalid " + file + ": " + diag.entries());
            }
            return sheet;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
