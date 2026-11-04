package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.style.StyleFacet;
import com.azane.ixchel.core.style.Declarations;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.library.Library;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The stylesheets shipped in the library, compiled once; a problem in one is a bug in the library.
 * They do not follow resource reloads.
 *
 * <p>The user-agent sheet, next to this class, holds the defaults of the inline elements, the form
 * controls, {@code <dialog>} and {@code <ix-tooltip>}; {@link #nativeElements} adds the display of a
 * registry's native elements. A theme is {@code @ixchel/themes/theme.css}, the rules every theme
 * shares, written against custom properties, then the theme's own sheet, which sets those properties
 * on {@code :root}; both are read through {@link Library#CORE}.
 */
public final class LibrarySheets {
    static final StyleSheet USER_AGENT = compile("user-agent.css", read("user-agent.css"));

    /** Library stylesheet name, as linked after {@code @ixchel/}, to its files in cascade order. */
    private static final Map<String, List<String>> THEMES = Map.of(
            "vanilla.css", List.of(Library.PREFIX + "themes/theme.css", Library.PREFIX + "themes/vanilla.css"),
            "dark.css", List.of(Library.PREFIX + "themes/theme.css", Library.PREFIX + "themes/dark.css"));

    private static final Map<String, StyleSheet> COMPILED = new ConcurrentHashMap<>();

    private LibrarySheets() {
    }

    /** The files of the library stylesheet {@code name}, {@code null} if there is none. */
    static List<String> files(String name) {
        return THEMES.get(name);
    }

    /** The names a page can link after {@code @ixchel/}, sorted. */
    public static List<String> names() {
        return THEMES.keySet().stream().sorted().toList();
    }

    /**
     * The {@code --ix-*} custom properties each theme sets on {@code :root} in its own sheet, sorted,
     * by the name a page links after {@code @ixchel/}: the variables pages and components can read
     * and override. Read from the compiled sheets.
     */
    public static Map<String, List<String>> themeVariables() {
        Map<String, List<String>> out = new TreeMap<>();
        THEMES.forEach((name, files) -> {
            Set<String> vars = new TreeSet<>();
            for (StyleSheet.StyleRule r : sheet(files.get(files.size() - 1)).rules()) {
                if (isRoot(r.selector())) {
                    for (Declarations.Custom c : r.declarations().customs()) {
                        if (c.name().startsWith("--ix-")) {
                            vars.add(c.name());
                        }
                    }
                }
            }
            out.put(name, List.copyOf(vars));
        });
        return Collections.unmodifiableMap(out);
    }

    /** Whether {@code s} is exactly {@code :root}. */
    private static boolean isRoot(Selector s) {
        if (s.compounds().size() != 1 || s.pseudoElement() != Selector.PseudoElement.NONE) {
            return false;
        }
        Selector.Compound c = s.compounds().get(0);
        return c.root() && c.tag() == null && c.id() == null && c.classes().isEmpty() && c.attributes().isEmpty()
                && c.stateMask() == 0 && c.logical().isEmpty();
    }

    static StyleSheet sheet(String id) {
        return COMPILED.computeIfAbsent(id, f -> compile(f, Library.CORE.read(f)));
    }

    /**
     * {@code display: inline} for the native elements of {@code tags} the host draws, which are replaced
     * inline elements like {@code <img>}; a column for those with line content, one line per row.
     */
    static List<StyleSheet> nativeElements(TagRegistry tags) {
        StringBuilder declarations = new StringBuilder();
        for (String tag : new TreeSet<>(tags.tags())) {
            String style = tags.type(tag).facet(StyleFacet.KEY).declarations();
            if (!style.isEmpty()) declarations.append(tag).append(" { ").append(style).append(" }\n");
        }
        String css = declarations.toString();
        if (css.isEmpty()) {
            return List.of();
        }
        return List.of(COMPILED.computeIfAbsent(css, c -> {
            Diagnostics diag = new Diagnostics();
            StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(c, "native elements", diag), CssProperties.REGISTRY, diag);
            if (diag.hasErrors()) {
                throw new IllegalStateException("invalid native element sheet: " + diag.entries());
            }
            return sheet;
        }));
    }

    private static String read(String file) {
        try (InputStream in = LibrarySheets.class.getResourceAsStream(file)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static StyleSheet compile(String file, String css) {
        if (css == null) {
            throw new IllegalStateException("missing " + file);
        }
        Diagnostics diag = new Diagnostics();
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(css, file, diag), CssProperties.REGISTRY, diag);
        if (diag.hasErrors()) {
            throw new IllegalStateException("invalid " + file + ": " + diag.entries());
        }
        return sheet;
    }
}
