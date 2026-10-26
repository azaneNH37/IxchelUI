package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.Painter;
import com.azane.ixchel.core.style.StyleEngine;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.Template;
import com.azane.ixchel.core.template.TemplateParser;
import com.azane.ixchel.core.text.TextMeasurer;
import com.azane.ixchel.core.text.TextShaper;

import java.util.ArrayList;
import java.util.List;

/**
 * One loaded UI: template tree under an implicit viewport-sized root, its styles, and the per-frame
 * pipeline style → layout → paint. A frame in which nothing changed does no work and keeps the
 * previous {@link DisplayList}.
 */
public final class UiDocument {
    /** A stylesheet file; {@code file} is used in error positions. */
    public record CssSource(String css, String file) {
    }

    private final Element root;
    private final Diagnostics diagnostics;
    private final StyleEngine style;
    private final LayoutEngine layout;
    private final Painter painter;
    private final DisplayList displayList = new DisplayList();
    private float viewportWidth = Float.NaN;
    private float viewportHeight = Float.NaN;
    private boolean textInvalidated;

    private UiDocument(Element root, Diagnostics diagnostics, StyleEngine style, LayoutEngine layout) {
        this.root = root;
        this.diagnostics = diagnostics;
        this.style = style;
        this.layout = layout;
        this.painter = new Painter(layout);
    }

    /**
     * Parses and validates everything up front; external sheets apply before inline {@code <style>}
     * blocks. Every error found is reported in one {@link UiLoadException}; warnings stay readable
     * through {@link #diagnostics()}.
     */
    public static UiDocument load(String template, String templateFile, List<CssSource> sheets,
                                  TagRegistry tags, TextMeasurer measurer) {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse(template, templateFile, tags, diag);
        List<StyleSheet> compiled = new ArrayList<>();
        for (CssSource s : sheets) {
            compiled.add(StyleSheet.compile(CssParser.parseStylesheet(s.css(), s.file(), diag), CssProperties.REGISTRY, diag));
        }
        for (Template.StyleSource s : t.styles()) {
            compiled.add(StyleSheet.compile(CssParser.parseStylesheet(s.css(), s.start(), diag), CssProperties.REGISTRY, diag));
        }
        Element root = new Element("#root", new SourcePos(templateFile, 1, 1));
        for (Node n : t.roots()) {
            root.appendChild(n);
        }
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, compiled, diag);
        style.restyle(root);
        diag.throwIfErrors();
        LayoutEngine layout = new LayoutEngine(new TextShaper(measurer), CssProperties.REGISTRY);
        return new UiDocument(root, diag, style, layout);
    }

    /** The implicit root; its children are the template's top-level elements. */
    public Element root() {
        return root;
    }

    /** Load warnings, plus problems found after load (such as inline styles compiled on first use). */
    public Diagnostics diagnostics() {
        return diagnostics;
    }

    /** Fonts or glyph widths changed (resource reload, Force Unicode): re-measure all text next frame. */
    public void invalidateText() {
        layout.invalidateText();
        textInvalidated = true;
    }

    /**
     * Brings the document up to date for a viewport of the given GUI size and returns its display list.
     * The same instance is returned every frame; its {@link DisplayList#version()} changes only when
     * something was repainted.
     */
    public DisplayList frame(float viewportWidth, float viewportHeight) {
        boolean dirty = textInvalidated
                || root.isStyleDirty() || root.hasStyleDirtyDescendant() || root.hasTreeChange()
                || viewportWidth != this.viewportWidth || viewportHeight != this.viewportHeight;
        if (!dirty) {
            return displayList;
        }
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        textInvalidated = false;
        style.restyle(root);
        layout.layout(root, viewportWidth, viewportHeight);
        painter.paint(root, displayList);
        return displayList;
    }
}
