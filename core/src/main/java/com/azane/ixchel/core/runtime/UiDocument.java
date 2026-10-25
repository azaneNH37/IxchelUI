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
import com.azane.ixchel.core.template.Template;
import com.azane.ixchel.core.template.TemplateParser;
import com.azane.ixchel.core.text.TextMeasurer;
import com.azane.ixchel.core.text.TextShaper;

import java.util.ArrayList;
import java.util.List;

/**
 * One loaded UI: template tree under an implicit viewport-sized root, its styles, and the per-frame
 * pipeline style → layout → paint.
 */
public final class UiDocument {
    /** A stylesheet file; {@code file} is used in error positions. */
    public record CssSource(String css, String file) {
    }

    private final Element root;
    private final StyleEngine style;
    private final LayoutEngine layout;
    private final Painter painter;

    private UiDocument(Element root, StyleEngine style, LayoutEngine layout) {
        this.root = root;
        this.style = style;
        this.layout = layout;
        this.painter = new Painter(layout);
    }

    /**
     * Parses and validates everything up front; external sheets apply before inline {@code <style>}
     * blocks. Every problem found is reported in one {@link UiLoadException}.
     */
    public static UiDocument load(String template, String templateFile, List<CssSource> sheets, TextMeasurer measurer) {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse(template, templateFile, diag);
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
        return new UiDocument(root, style, new LayoutEngine(new TextShaper(measurer)));
    }

    /** The implicit root; its children are the template's top-level elements. */
    public Element root() {
        return root;
    }

    /** Runs style, layout and paint for a viewport of the given GUI size; {@code out} is overwritten. */
    public void frame(float viewportWidth, float viewportHeight, DisplayList out) {
        style.restyle(root);
        layout.layout(root, viewportWidth, viewportHeight);
        painter.paint(root, out);
    }
}
