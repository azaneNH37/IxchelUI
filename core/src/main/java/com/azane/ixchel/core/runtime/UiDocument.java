package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.anim.Transitions;
import com.azane.ixchel.core.binding.Bindings;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.selector.SelectorParser;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.ImageMeasurer;
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
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * One loaded UI: template tree under an implicit viewport-sized root, its styles, bindings, and the
 * per-frame pipeline bindings → transitions → style → layout → paint. A frame in which nothing
 * changed and no transition runs does no work and keeps the previous {@link DisplayList}; one in
 * which only {@linkplain Element#setScrollTop scroll offsets} changed repaints without style or layout.
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
    private final Bindings bindings;
    private final DisplayList displayList = new DisplayList();
    private final EventDispatcher events;
    private final Transitions transitions;
    private LongSupplier clock = System::nanoTime;
    private float viewportWidth = Float.NaN;
    private float viewportHeight = Float.NaN;
    private boolean textInvalidated;
    /** Frames that ran style and layout; observed by tests. */
    int pipelineRuns;

    private UiDocument(Element root, Diagnostics diagnostics, StyleEngine style, LayoutEngine layout, Bindings bindings,
                       TagRegistry tags, ImageMeasurer images, Transitions transitions) {
        this.root = root;
        this.diagnostics = diagnostics;
        this.style = style;
        this.layout = layout;
        this.painter = new Painter(layout, tags, images, diagnostics);
        this.bindings = bindings;
        this.events = new EventDispatcher(root);
        this.transitions = transitions;
    }

    /** Loads a document without a view model; any {@code bind:*} attribute is an error. */
    public static UiDocument load(String template, String templateFile, List<CssSource> sheets,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images) {
        return load(template, templateFile, sheets, tags, measurer, images, null, Bindings.PLAIN_TEXT);
    }

    /**
     * Parses and validates everything up front; external sheets apply before inline {@code <style>}
     * blocks. Every error found is reported in one {@link UiLoadException}; warnings stay readable
     * through {@link #diagnostics()}. Bindings are resolved and their initial values written here.
     *
     * @param images natural image sizes, for {@code background-size: auto}
     * @param model  the view model {@code bind:*} attributes refer to, or {@code null}
     * @param textOf converts bound values to text (the mc layer flattens components)
     */
    public static UiDocument load(String template, String templateFile, List<CssSource> sheets,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images, ViewModel model,
                                  Function<Object, String> textOf) {
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
        Bindings bindings = Bindings.compile(root, model, textOf, tags, CssProperties.REGISTRY, diag);
        Transitions transitions = new Transitions(CssProperties.REGISTRY, root);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, compiled, diag, transitions);
        style.restyle(root);
        diag.throwIfErrors();
        LayoutEngine layout = new LayoutEngine(new TextShaper(measurer), CssProperties.REGISTRY);
        return new UiDocument(root, diag, style, layout, bindings, tags, images, transitions);
    }

    /** The implicit root; its children are the template's top-level elements. */
    public Element root() {
        return root;
    }

    /**
     * The mounted elements matching a selector list, in document order, as in the DOM; the selector
     * syntax is the stylesheets'. Elements inside an unmounted {@code if} or not yet created by
     * {@code each} are not in the tree.
     *
     * @throws IllegalArgumentException if {@code selectors} is not a valid selector list
     */
    public List<Element> querySelectorAll(String selectors) {
        Diagnostics diag = new Diagnostics();
        SourcePos pos = new SourcePos("querySelectorAll", 1, 1);
        List<Selector> list = SelectorParser.parseList(CssParser.parseValue(selectors, pos, diag), pos, diag);
        if (diag.hasErrors()) {
            throw new IllegalArgumentException("invalid selector '" + selectors + "': " + diag.entries());
        }
        List<Element> out = new ArrayList<>();
        collect(root, list, out);
        return out;
    }

    private static void collect(Element parent, List<Selector> selectors, List<Element> out) {
        for (Node n : parent.children()) {
            if (n instanceof Element e) {
                for (Selector s : selectors) {
                    if (s.matches(e)) {
                        out.add(e);
                        break;
                    }
                }
                collect(e, selectors, out);
            }
        }
    }

    /**
     * Load warnings, plus problems found after load: inline styles compiled on first use, derived
     * values that threw and invalid {@code bind:style:*} values (the binding keeps its last value), and
     * background images that could not be sized or tiled when painted.
     */
    public Diagnostics diagnostics() {
        return diagnostics;
    }

    /**
     * Input against the last frame's layout, in GUI pixels; each method returns whether the UI took
     * the input (see {@link EventDispatcher}). State changes show from the next frame on.
     */
    public EventDispatcher events() {
        return events;
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
        boolean animating = transitions.advance(clock.getAsLong());
        boolean dirty = bindings.update() || animating || textInvalidated
                || root.isStyleDirty() || root.hasStyleDirtyDescendant() || root.hasTreeChange()
                || viewportWidth != this.viewportWidth || viewportHeight != this.viewportHeight;
        if (!dirty) {
            if (root.needsRepaint()) {
                painter.paint(root, displayList);
                events.afterLayout();
            }
            return displayList;
        }
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        textInvalidated = false;
        pipelineRuns++;
        style.restyle(root);
        layout.layout(root, viewportWidth, viewportHeight);
        painter.paint(root, displayList);
        events.afterLayout();
        return displayList;
    }

    /** Replaces the transition clock ({@link System#nanoTime} by default); for tests. */
    void clock(LongSupplier clock) {
        this.clock = clock;
    }
}
