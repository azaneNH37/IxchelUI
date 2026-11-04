package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.style.StyleFacet;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.view.TagRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The user-agent stylesheets, compiled once; a problem in one is a bug in the library. The sheet next
 * to this class holds the defaults of the inline elements, the form controls, {@code <dialog>} and
 * {@code <ix-tooltip>}; {@link #drawnElements} adds the display of a registry's drawn elements.
 * Themes are not here: a page links one, and it compiles with the page's effects.
 */
final class UserAgentSheets {
    static final StyleSheet USER_AGENT = compile("user-agent.css", read("user-agent.css"));

    private static final Map<String, StyleSheet> COMPILED = new ConcurrentHashMap<>();

    private UserAgentSheets() {
    }

    /**
     * {@code display: inline} for the drawn elements of {@code tags} the host draws, which are replaced
     * inline elements like {@code <img>}; a column for those with line content, one line per row.
     */
    static List<StyleSheet> drawnElements(TagRegistry tags) {
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
            StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(c, "drawn elements", diag), CssProperties.REGISTRY, diag);
            if (diag.hasErrors()) {
                throw new IllegalStateException("invalid drawn element sheet: " + diag.entries());
            }
            return sheet;
        }));
    }

    private static String read(String file) {
        try (InputStream in = UserAgentSheets.class.getResourceAsStream(file)) {
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
