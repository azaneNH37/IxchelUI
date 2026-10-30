package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.anim.Transitions;
import com.azane.ixchel.core.binding.compile.Bindings;
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
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.paint.Painter;
import com.azane.ixchel.core.style.StyleEngine;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.TemplateLoader;
import com.azane.ixchel.core.text.TextMeasurer;
import com.azane.ixchel.core.text.TextShaper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * One loaded UI: template tree under an implicit viewport-sized root, its styles, bindings, and the
 * per-frame pipeline bindings → transitions → style → layout → paint. A frame in which nothing
 * changed and no transition runs does no work and keeps the previous {@link DisplayList}; one in
 * which only {@linkplain Element#setScrollTop scroll offsets}, {@linkplain Element#control() form
 * control} values that do not restyle (text, a range) or the caret's blink changed repaints without
 * style or layout. The caret of a focused {@code <input>} blinks every {@value #BLINK_MS} ms,
 * restarting visible on each edit or move.
 *
 * <p>Below the template's stylesheets sit the theme layer, the library stylesheets the page links
 * as {@code @ixchel/<name>}, the component layer, the stylesheets component files link, and a
 * user-agent stylesheet with the defaults of the form controls and {@code <dialog>} (see
 * {@link LibrarySheets}). Open selects and dialogs live in the document's {@link TopLayer}, which
 * layout, paint and input share.
 */
public final class UiDocument {
    static final long BLINK_MS = 500;

    private final Element root;
    private final List<String> files;
    private final Diagnostics diagnostics;
    private final StyleEngine style;
    private final LayoutEngine layout;
    private final Painter painter;
    private final Bindings bindings;
    private final TopLayer topLayer;
    private final DisplayList displayList = new DisplayList();
    private final EventDispatcher events;
    private final Transitions transitions;
    private LongSupplier clock = System::nanoTime;
    private float viewportWidth = Float.NaN;
    private float viewportHeight = Float.NaN;
    private boolean textInvalidated;
    private Element blinkInput;
    private String blinkValue;
    private int blinkCaret;
    private int blinkAnchor;
    private long blinkStart;
    /** Frames that ran style and layout; observed by tests. */
    int pipelineRuns;

    private UiDocument(Element root, List<String> files, Diagnostics diagnostics, StyleEngine style,
                       LayoutEngine layout, Bindings bindings, TagRegistry tags, ImageMeasurer images,
                       Transitions transitions, TopLayer topLayer) {
        this.root = root;
        this.files = files;
        this.diagnostics = diagnostics;
        this.style = style;
        this.layout = layout;
        this.painter = new Painter(layout, tags, images, diagnostics);
        this.bindings = bindings;
        this.topLayer = topLayer;
        this.events = new EventDispatcher(root, layout, topLayer);
        this.transitions = transitions;
    }

    /** Loads a document without a view model; it sees only its local states. */
    public static UiDocument load(String template, String templateFile, Function<String, String> files,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images) {
        return load(template, templateFile, files, tags, measurer, images, null, Bindings.PLAIN_TEXT);
    }

    /**
     * Parses and validates everything up front, with every file the template links directly or
     * through components; stylesheets apply in document order, {@code <link rel="stylesheet">} and
     * {@code <style>} interleaved. Every error found is reported in one {@link UiLoadException};
     * warnings stay readable through {@link #diagnostics()}. Bindings are resolved and their initial
     * values written here.
     *
     * @param templateFile the template's id, {@code namespace:path} or a bare path; links resolve against it
     * @param files        the text of a file id, {@code null} if there is none; a {@link RuntimeException}
     *                     is reported as a load error at the link
     * @param images       natural image sizes, for {@code background-size: auto}
     * @param model        the view model {@code bind:*} attributes refer to, or {@code null}
     * @param textOf       converts bound values to text (the mc layer flattens components)
     */
    public static UiDocument load(String template, String templateFile, Function<String, String> files,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images, ViewModel model,
                                  Function<Object, String> textOf) {
        Diagnostics diag = new Diagnostics();
        TemplateLoader.Loaded loaded = TemplateLoader.load(template, templateFile, files, tags, diag);
        List<StyleSheet> components = sheets(loaded.componentSheets(), diag);
        List<StyleSheet> themes = library(loaded.library(), diag);
        List<StyleSheet> author = sheets(loaded.pageSheets(), diag);
        Element root = new Element("#root", new SourcePos(templateFile, 1, 1));
        for (Node n : loaded.page().roots()) {
            root.appendChild(n);
        }
        TopLayer topLayer = new TopLayer();
        Bindings bindings = Bindings.compile(root, model, loaded.components(), textOf, tags, CssProperties.REGISTRY,
                topLayer, diag);
        Transitions transitions = new Transitions(CssProperties.REGISTRY, root);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, List.of(LibrarySheets.USER_AGENT), components, themes, author,
                diag, transitions);
        style.restyle(root);
        diag.throwIfErrors();
        LayoutEngine layout = new LayoutEngine(new TextShaper(measurer), CssProperties.REGISTRY);
        return new UiDocument(root, loaded.files(), diag, style, layout, bindings, tags, images, transitions,
                topLayer);
    }

    private static List<StyleSheet> sheets(List<TemplateLoader.Sheet> sources, Diagnostics diag) {
        List<StyleSheet> out = new ArrayList<>(sources.size());
        for (TemplateLoader.Sheet s : sources) {
            out.add(StyleSheet.compile(CssParser.parseStylesheet(s.css(), s.start(), diag), CssProperties.REGISTRY, diag));
        }
        return out;
    }

    /** The library stylesheets' files in cascade order, each once; an unknown name is reported at its link. */
    private static List<StyleSheet> library(List<TemplateLoader.Library> links, Diagnostics diag) {
        Set<String> files = new LinkedHashSet<>();
        for (TemplateLoader.Library l : links) {
            List<String> f = LibrarySheets.files(l.name());
            if (f == null) {
                diag.error(l.pos(), "no library stylesheet '" + TemplateLoader.LIBRARY + l.name() + "'; there are "
                        + String.join(", ", LibrarySheets.names()));
            } else {
                files.addAll(f);
            }
        }
        List<StyleSheet> out = new ArrayList<>(files.size());
        for (String f : files) {
            out.add(LibrarySheets.sheet(f));
        }
        return out;
    }

    /**
     * The ids of the template and every file it linked, directly or through components, including
     * links that failed to load; a host reloads the document when one of them changes.
     */
    public List<String> files() {
        return files;
    }

    /** Open selects and dialogs, bottom first. */
    public TopLayer topLayer() {
        return topLayer;
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
        long now = clock.getAsLong();
        boolean animating = transitions.advance(now);
        boolean dirty = bindings.update() || animating || textInvalidated
                || root.isStyleDirty() || root.hasStyleDirtyDescendant() || root.hasTreeChange()
                || viewportWidth != this.viewportWidth || viewportHeight != this.viewportHeight;
        boolean caret = caretVisible(now);
        boolean blinked = caret != painter.caretVisible();
        painter.setCaretVisible(caret);
        if (!dirty) {
            if (root.needsRepaint() || blinked) {
                painter.paint(root, topLayer, displayList);
                events.afterLayout();
            }
            return displayList;
        }
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        textInvalidated = false;
        pipelineRuns++;
        style.restyle(root);
        layout.layout(root, viewportWidth, viewportHeight, topLayer);
        painter.paint(root, topLayer, displayList);
        events.afterLayout();
        return displayList;
    }

    /** Whether the focused input's caret is in the visible half of its blink; restarts on any change of the field. */
    private boolean caretVisible(long now) {
        Element f = events.focused();
        if (f == null || f.textField() == null) {
            blinkInput = null;
            return false;
        }
        var field = f.textField();
        if (f != blinkInput || !field.value().equals(blinkValue) || field.caret() != blinkCaret
                || field.anchor() != blinkAnchor) {
            blinkInput = f;
            blinkValue = field.value();
            blinkCaret = field.caret();
            blinkAnchor = field.anchor();
            blinkStart = now;
        }
        return (now - blinkStart) / (BLINK_MS * 1_000_000) % 2 == 0;
    }

    /** Replaces the transition clock ({@link System#nanoTime} by default); for tests. */
    void clock(LongSupplier clock) {
        this.clock = clock;
    }
}
