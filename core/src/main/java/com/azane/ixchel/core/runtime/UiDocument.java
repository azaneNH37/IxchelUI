package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.anim.Transitions;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.selector.SelectorParser;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.paint.Painter;
import com.azane.ixchel.core.paint.RoundedClipCheck;
import com.azane.ixchel.core.style.StyleEngine;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.TemplateCode;
import com.azane.ixchel.core.template.TemplateLoader;
import com.azane.ixchel.core.template.Translations;
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
 * user-agent stylesheet with the defaults of the inline elements, the native elements, the form
 * controls, {@code <dialog>}, {@code <ix-tooltip>} and {@code <ix-drag-preview>} (see
 * {@link LibrarySheets}). Open selects and dialogs, and the tooltip or drag preview shown at the
 * pointer, live in the document's {@link TopLayer}, which layout, paint and input share.
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
    private final DisplayList atPointerList = new DisplayList();
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
                       LayoutEngine layout, Bindings bindings, ImageMeasurer images,
                       Transitions transitions, TopLayer topLayer) {
        this.root = root;
        this.files = files;
        this.diagnostics = diagnostics;
        this.style = style;
        this.layout = layout;
        this.painter = new Painter(layout, images, diagnostics);
        this.bindings = bindings;
        this.topLayer = topLayer;
        this.events = new EventDispatcher(root, layout, topLayer);
        this.transitions = transitions;
    }

    /** Loads a document without a view model; it sees only its local states. */
    public static UiDocument load(String template, String templateFile, Function<String, String> files,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images) {
        return load(template, templateFile, files, tags, Translations.NONE, measurer, images, null, Bindings.PLAIN_TEXT);
    }

    /** Loads a document whose template translates nothing. */
    public static UiDocument load(String template, String templateFile, Function<String, String> files,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images, ViewModel model,
                                  Bindings.TextConverter text) {
        return load(template, templateFile, files, tags, Translations.NONE, measurer, images, model, text);
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
     * @param translations the language's translations; its language is the root's {@code lang}
     * @param images       natural image sizes, for {@code background-size: auto}
     * @param model        the view model {@code bind:*} attributes refer to, or {@code null}
     * @param text         converts bound values to text (the mc layer keeps a component's styles)
     */
    public static UiDocument load(String template, String templateFile, Function<String, String> files,
                                  TagRegistry tags, Translations translations, TextMeasurer measurer, ImageMeasurer images,
                                  ViewModel model, Bindings.TextConverter text) {
        Diagnostics diag = new Diagnostics();
        Parsed p = parse(template, templateFile, files, tags, translations, diag);
        Bindings bindings = Bindings.compile(p.root, model, p.loaded.components(), text, tags, CssProperties.REGISTRY,
                p.topLayer, diag);
        Transitions transitions = new Transitions(CssProperties.REGISTRY, p.root);
        StyleEngine style = p.style(tags, diag, transitions);
        style.restyle(p.root);
        diag.throwIfErrors();
        LayoutEngine layout = new LayoutEngine(new TextShaper(measurer), CssProperties.REGISTRY, diag);
        return new UiDocument(p.root, p.loaded.files(), diag, style, layout, bindings, images, transitions,
                p.topLayer);
    }

    /**
     * Lints a template as {@link #load} would load it, without a view model instance and without
     * laying it out: the template, every linked file, every binding against {@code shape}'s declared
     * types, and the cascade. Never throws for problems; they are all in the result. Problems that
     * need values or a viewport are not found.
     *
     * @param shape   the view model class's shape, or {@code null} for a document without one
     * @param enabled the {@linkplain DiagCode#optIn() opt-in} checks to run
     */
    public static Diagnostics lint(String template, String templateFile, Function<String, String> files,
                                   TagRegistry tags, Translations translations, ModelShape shape,
                                   Set<? extends DiagCode> enabled) {
        Diagnostics diag = new Diagnostics(enabled);
        Parsed p = parse(template, templateFile, files, tags, translations, diag);
        Bindings.lint(p.root, shape, p.loaded.components(), Bindings.PLAIN_TEXT, tags, CssProperties.REGISTRY,
                p.topLayer, diag);
        p.style(tags, diag, new Transitions(CssProperties.REGISTRY, p.root)).restyle(p.root);
        return diag;
    }

    /** A template and its stylesheets parsed, its roots under the implicit document root. */
    private record Parsed(TemplateLoader.Loaded loaded, Element root, TopLayer topLayer, List<StyleSheet> components,
                          List<StyleSheet> themes, List<StyleSheet> author) {
        StyleEngine style(TagRegistry tags, Diagnostics diag, Transitions transitions) {
            List<StyleSheet> userAgent = new ArrayList<>(List.of(LibrarySheets.USER_AGENT));
            userAgent.addAll(LibrarySheets.nativeElements(tags));
            RoundedClipCheck roundedClip = new RoundedClipCheck(diag);
            StyleEngine.Hook hook = (e, previous, next) -> {
                roundedClip.computed(e, previous, next);
                transitions.computed(e, previous, next);
            };
            return new StyleEngine(CssProperties.REGISTRY, userAgent, components, themes, author, diag, hook);
        }
    }

    private static Parsed parse(String template, String templateFile, Function<String, String> files,
                                TagRegistry tags, Translations translations, Diagnostics diag) {
        TemplateLoader.Loaded loaded = TemplateLoader.load(template, templateFile, files, tags, translations, diag);
        List<StyleSheet> components = sheets(loaded.componentSheets(), diag);
        List<StyleSheet> themes = library(loaded.library(), diag);
        List<StyleSheet> author = sheets(loaded.pageSheets(), diag);
        Element root = new Element(ElementType.plain("#root"), new SourcePos(templateFile, 1, 1));
        if (translations.language() != null) {
            root.setAttribute("lang", translations.language());
        }
        for (Node n : loaded.page().roots()) {
            root.appendChild(n);
        }
        return new Parsed(loaded, root, new TopLayer(), components, themes, author);
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
                diag.report(TemplateCode.UNKNOWN_LIBRARY_SHEET, l.pos(), "no library stylesheet '" + TemplateLoader.LIBRARY + l.name() + "'; there are "
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

    /** Open selects and dialogs, bottom first, and the shown tooltip. */
    public TopLayer topLayer() {
        return topLayer;
    }

    /**
     * Shows {@code tip}, an {@code <ix-tooltip>} of this document, at the pointer {@code (x, y)} from the
     * next {@link #frame} on, or hides the tooltip when {@code null}. The host picks it before each frame;
     * moving it only places it again. While an element is dragged the pointer shows its drag preview
     * instead, and this does nothing.
     */
    public void setTooltip(Element tip, float x, float y) {
        if (tip != null && tip.type().facet(LayerFacet.KEY).pointer() != LayerFacet.Pointer.TOOLTIP) {
            throw new IllegalArgumentException("not an <ix-tooltip>: <" + tip.tag() + ">");
        }
        if (events.dragSource() == null) {
            topLayer.showAtPointer(tip, x, y);
        }
    }

    /**
     * The display list of the tooltip or drag preview shown at the pointer, painted above
     * {@link #frame}'s; brought up to date by {@link #frame}, same instance every frame, empty while
     * neither is shown.
     */
    public DisplayList atPointerDisplayList() {
        return atPointerList;
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
        Element shown = topLayer.atPointer();
        if (shown != null && !isConnected(shown)) {
            topLayer.showAtPointer(null, 0, 0); // a binding removed it this frame
            dirty = true;
        }
        boolean moved = topLayer.takeMoved();
        if (!dirty) {
            boolean repaint = root.needsRepaint() || blinked;
            if (moved) {
                layout.placeAtPointer(topLayer);
            }
            if (repaint) {
                painter.paint(root, topLayer, displayList);
                events.afterLayout();
            }
            if (repaint || moved) {
                painter.paintAtPointer(topLayer, atPointerList);
            }
            return displayList;
        }
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        textInvalidated = false;
        pipelineRuns++;
        if (style.viewport(viewportWidth, viewportHeight)) {
            root.markStyleDirty();
        }
        style.restyle(root);
        layout.layout(root, viewportWidth, viewportHeight, topLayer);
        painter.paint(root, topLayer, displayList);
        painter.paintAtPointer(topLayer, atPointerList);
        events.afterLayout();
        return displayList;
    }

    private boolean isConnected(Element e) {
        Node n = e;
        while (n.parent() != null) {
            n = n.parent();
        }
        return n == root;
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
