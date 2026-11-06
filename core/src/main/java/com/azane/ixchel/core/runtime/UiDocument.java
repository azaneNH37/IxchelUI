package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.anim.Animations;
import com.azane.ixchel.core.anim.Transitions;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.syntax.ParsedSheet;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.selector.SelectorParser;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.effect.Effects;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.Presence;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.layout.Formatting;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.paint.Painter;
import com.azane.ixchel.core.paint.RoundedClipCheck;
import com.azane.ixchel.core.style.ComponentRootTokens;
import com.azane.ixchel.core.style.LibraryNames;
import com.azane.ixchel.core.style.StyleEngine;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.view.ComponentDef;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.core.link.Linker;
import com.azane.ixchel.core.view.Translations;
import com.azane.ixchel.core.text.TextMeasurer;
import com.azane.ixchel.core.text.TextShaper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * One loaded UI: page tree under an implicit viewport-sized root, its styles, bindings, and the
 * per-frame pipeline animations and transitions → {@link Presence} → bindings → style → layout →
 * paint → {@code animationend} events. The document's top-level elements enter as it loads. A frame
 * in which nothing changed and no animation, transition, entry or exit runs does no work and keeps
 * the previous {@link DisplayList}; one in
 * which only {@linkplain Element#setScrollTop scroll offsets}, {@linkplain Element#control() form
 * control} values that do not restyle (text, a range) or the caret's blink changed repaints without
 * style or layout. The caret of a focused {@code <input>} blinks every {@value #BLINK_MS} ms,
 * restarting visible on each edit or move.
 *
 * <p>Above a user-agent stylesheet with the defaults of the inline elements, the native elements,
 * the form controls, {@code <dialog>}, {@code <ix-tooltip>} and {@code <ix-drag-preview>} (see
 * {@link UserAgentSheets}) come the partials, reaching every element, then the components'
 * stylesheets and the page's, each reaching only its own view's elements and hosts (see
 * {@link Linker} and {@link StyleEngine}).
 * Open selects and dialogs, and the tooltip or drag preview shown at the pointer, live in the
 * document's {@link TopLayer}, which layout, paint and input share.
 *
 * <p>The effects the css files link ({@link Effects}) register
 * their params as typed properties of this document only: its styles, partials included, transitions,
 * keyframes and bindings compile against the registry they extend.
 *
 * <p>A {@link UiHosting} adds the library and the host's Java components. Component
 * instances own view models, which end with their instance or with {@link #close()}.
 */
@HostApi
public final class UiDocument {
    static final long BLINK_MS = 500;

    private final Element root;
    private final List<String> files;
    private final Diagnostics diagnostics;
    private final StyleEngine style;
    private final LayoutEngine layout;
    private final Painter painter;
    private final Effects effects;
    private final Bindings bindings;
    private final TopLayer topLayer;
    private final DisplayList displayList = new DisplayList();
    private final DisplayList atPointerList = new DisplayList();
    private final EventDispatcher events;
    private final Animations animations;
    private final Transitions transitions;
    private final Presence presence;
    private final Presence.Unit documentUnit;
    private LongSupplier clock = System::nanoTime;
    private long frameTime;
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
                       LayoutEngine layout, Bindings bindings, ImageMeasurer images, Effects effects,
                       Animations animations, Transitions transitions, TopLayer topLayer, Presence presence,
                       Presence.Unit documentUnit) {
        this.root = root;
        this.files = files;
        this.diagnostics = diagnostics;
        this.style = style;
        this.layout = layout;
        this.effects = effects;
        this.painter = new Painter(layout, images, effects, diagnostics);
        this.bindings = bindings;
        this.topLayer = topLayer;
        this.events = new EventDispatcher(root, layout, topLayer);
        this.animations = animations;
        this.transitions = transitions;
        this.presence = presence;
        this.documentUnit = documentUnit;
    }

    /** Loads a document without a view model; it sees only its local states. */
    public static UiDocument load(String page, String pageFile, Function<String, String> files,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images) {
        return load(page, pageFile, files, tags, Translations.NONE, measurer, images, null, Bindings.PLAIN_TEXT);
    }

    /** Loads a document whose page translates nothing. */
    public static UiDocument load(String page, String pageFile, Function<String, String> files,
                                  TagRegistry tags, TextMeasurer measurer, ImageMeasurer images, ViewModel model,
                                  Bindings.TextConverter text) {
        return load(page, pageFile, files, tags, Translations.NONE, measurer, images, model, text);
    }

    /** Loads a document without anything hosted. */
    public static UiDocument load(String page, String pageFile, Function<String, String> files,
                                  TagRegistry tags, Translations translations, TextMeasurer measurer, ImageMeasurer images,
                                  ViewModel model, Bindings.TextConverter text) {
        return load(page, pageFile, files, tags, translations, measurer, images, model, text, UiHosting.NONE);
    }

    /**
     * Parses and validates everything up front, with every file the page links directly or
     * through other files. Every error found is reported in one {@link UiLoadException};
     * warnings stay readable through {@link #diagnostics()}. Bindings are resolved and their initial
     * values written here.
     *
     * @param pageFile the page's id, {@code namespace:path} or a bare path; links resolve against it
     * @param files        the text of a file id, {@code null} if there is none; a {@link RuntimeException}
     *                     is reported as a load error at the link
     * @param translations the language's translations; its language is the root's {@code lang}
     * @param images       natural image sizes, for {@code background-size: auto}
     * @param model        the view model {@code bind:*} attributes refer to, or {@code null}
     * @param text         converts bound values to text (the mc layer keeps a component's styles)
     * @param hosting      the library and the host's Java components it may use
     */
    public static UiDocument load(String page, String pageFile, Function<String, String> files,
                                  TagRegistry tags, Translations translations, TextMeasurer measurer, ImageMeasurer images,
                                  ViewModel model, Bindings.TextConverter text, UiHosting hosting) {
        Diagnostics diag = new Diagnostics();
        Parsed p = parse(page, pageFile, files, tags, translations, hosting, null, diag);
        Animations animations = new Animations(p.root);
        Transitions transitions = new Transitions(p.effects.registry(), p.root, animations);
        StyleEngine style = p.style(tags, diag, animations, transitions);
        Presence presence = new Presence(e -> animations.finishing(e) || transitions.running(e),
                style.uses(Presence.STATES));
        TopLayer topLayer = new TopLayer(presence);
        Bindings bindings = Bindings.compile(p.root, model, p.loaded.components(), hosting.forBindings(p.loaded.componentFiles()),
                text, tags, p.effects, p.loaded::aliasesOf, topLayer, presence, diag);
        try {
            Presence.Unit documentUnit = presence.enterDocument(p.root);
            style.restyle(p.root);
            presence.styled();
            diag.throwIfErrors();
            LayoutEngine layout = new LayoutEngine(new TextShaper(measurer), p.effects.registry(), diag);
            return new UiDocument(p.root, p.loaded.files(), diag, style, layout, bindings, images, p.effects, animations,
                    transitions, topLayer, presence, documentUnit);
        } catch (RuntimeException ex) {
            bindings.close();
            throw ex;
        }
    }

    /** Lints a document without anything hosted. */
    public static Diagnostics lint(String page, String pageFile, Function<String, String> files,
                                   TagRegistry tags, Translations translations, ModelShape shape,
                                   Set<? extends DiagCode> enabled) {
        return lint(page, pageFile, files, tags, translations, shape, enabled, UiHosting.NONE, null);
    }

    /**
     * Lints a page as {@link #load} would load it, without a view model instance and without
     * laying it out: the page, every linked file, every binding against {@code shape}'s declared
     * types, the cascade, and the inline content rules layout checks ({@link Formatting}). Never
     * throws for problems; they are all in the result. Problems that need values or a viewport are
     * not found, nor those in block content or closed top layer elements, which only values show.
     *
     * @param shape   the view model class's shape, or {@code null} for a document without one
     * @param enabled the {@linkplain DiagCode#optIn() opt-in} checks to run
     * @param hosting    what the document may use; only declarations are read, no factory or value
     * @param vocabulary the names the library exports, which the host's css is checked against;
     *                   {@code null} to skip that check
     */
    public static Diagnostics lint(String page, String pageFile, Function<String, String> files,
                                   TagRegistry tags, Translations translations, ModelShape shape,
                                   Set<? extends DiagCode> enabled, UiHosting hosting, LibraryNames vocabulary) {
        Diagnostics diag = new Diagnostics(enabled);
        Parsed p = parse(page, pageFile, files, tags, translations, hosting, vocabulary, diag);
        Bindings.lint(p.root, shape, p.loaded.components(), hosting.forBindings(p.loaded.componentFiles()),
                Bindings.PLAIN_TEXT, tags, p.effects, p.loaded::aliasesOf, new TopLayer(Presence.instant()), diag);
        Animations animations = new Animations(p.root);
        p.style(tags, diag, animations, new Transitions(p.effects.registry(), p.root, animations)).restyle(p.root);
        new Formatting(diag).check(p.root);
        return diag;
    }

    /** A page and its stylesheets parsed, its roots under the implicit document root. */
    private record Parsed(Linker.Loaded loaded, Effects effects, Element root, List<StyleEngine.Layer> layers) {
        StyleEngine style(TagRegistry tags, Diagnostics diag, Animations animations, Transitions transitions) {
            List<StyleSheet> userAgent = new ArrayList<>(List.of(UserAgentSheets.USER_AGENT));
            userAgent.addAll(UserAgentSheets.drawnElements(tags));
            RoundedClipCheck roundedClip = new RoundedClipCheck(diag);
            StyleEngine.Hook hook = (e, previous, next, cascade) -> {
                roundedClip.computed(e, previous, next, cascade);
                animations.computed(e, previous, next, cascade);
                transitions.computed(e, previous, next, cascade);
            };
            return new StyleEngine(effects.registry(), userAgent, layers, loaded.componentFiles(), diag, hook);
        }
    }

    private static Parsed parse(String page, String pageFile, Function<String, String> files,
                                TagRegistry tags, Translations translations, UiHosting hosting, LibraryNames vocabulary,
                                Diagnostics diag) {
        Linker.Loaded loaded = Linker.load(page, pageFile, files, hosting.library(), tags,
                translations, diag);
        Effects effects = Effects.load(loaded.effects(), CssProperties.REGISTRY, diag);
        List<StyleEngine.Layer> layers = new ArrayList<>(loaded.layers().size());
        for (Linker.Layer l : loaded.layers()) {
            Linker.Sheet s = l.sheet();
            if (!l.partial() && !l.views().contains(pageFile)) {
                ComponentRootTokens.check(s.sheet(), diag);
            }
            if (vocabulary != null) {
                vocabulary.check(s.id(), s.sheet(), diag);
            }
            layers.add(new StyleEngine.Layer(compile(s, effects, diag), l.partial() ? Set.of() : l.views()));
        }
        if (vocabulary != null) {
            vocabulary.inline(loaded.page().roots(), diag);
            for (ComponentDef c : loaded.components().values()) {
                vocabulary.inline(c.body(), diag);
            }
        }
        Element root = new Element(ElementType.plain("#root"), new SourcePos(pageFile, 1, 1));
        if (translations.language() != null) {
            root.setAttribute("lang", translations.language());
        }
        for (Node n : loaded.page().roots()) {
            root.appendChild(n);
        }
        return new Parsed(loaded, effects, root, layers);
    }

    /** Compiles {@code sheet} against the document's registry, reporting its misuses of effects. */
    private static StyleSheet compile(Linker.Sheet sheet, Effects effects, Diagnostics diag) {
        effects.check(sheet.sheet(), sheet.aliases(), diag);
        return StyleSheet.compile(sheet.sheet(), effects.registry(), diag);
    }

    /**
     * The ids of the page and every file it linked, directly or through components, including
     * links that failed to load; a host reloads the document when one of them changes.
     */
    public List<String> files() {
        return files;
    }

    /** The effects the page and its components link, which the host compiles. */
    public Effects effects() {
        return effects;
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

    /** The implicit root; its children are the page's top-level elements. */
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

    /**
     * Starts the exit of the document's top-level elements, as they were when it loaded: they match
     * {@code :-ix-absent:-ix-exiting} and take no input, and bindings stop until {@link #enter}.
     * {@code done} runs from the {@link #frame} that sees nothing in them move any more, or here when
     * no rule uses these states; they keep matching after it. Nothing while the document already exits.
     */
    public void exit(Runnable done) {
        presence.exit(documentUnit, true, () -> {
            presence.rest(documentUnit);
            done.run();
        });
    }

    /** Whether the document {@linkplain #exit exits}. */
    public boolean exiting() {
        return documentUnit.exiting();
    }

    /** Takes an exiting document back from where its exit got to, or enters an exited one again from {@code :-ix-absent}. */
    public void enter() {
        presence.enterAgain(documentUnit);
    }

    /**
     * Ends the document: closes the view model of every component instance, which releases their
     * feeds. The page's view model stays the host's to close. A host replacing the document on reload
     * closes the old one. Idempotent.
     */
    public void close() {
        bindings.close();
    }

    /** Fonts or glyph widths changed (resource reload, Force Unicode, a GUI scale ModernUI lays text out at): re-measure all text next frame. */
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
        frameTime = now;
        boolean exited = exiting();
        boolean animating = animations.advance(now) | transitions.advance(now);
        boolean presenceChanged = presence.update();
        boolean dirty = !exited && bindings.update() || animating || presenceChanged || textInvalidated
                || root.isStyleDirty() || root.hasStyleDirtyDescendant() || root.hasTreeChange()
                || viewportWidth != this.viewportWidth || viewportHeight != this.viewportHeight;
        boolean caret = caretVisible(now);
        boolean blinked = caret != painter.caretVisible();
        painter.setCaretVisible(caret);
        Element shown = topLayer.atPointer();
        if (shown != null && !isConnected(shown)) {
            topLayer.dropAtPointer(); // a binding removed it this frame
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
        presence.styled();
        layout.layout(root, viewportWidth, viewportHeight, topLayer);
        painter.paint(root, topLayer, displayList);
        painter.paintAtPointer(topLayer, atPointerList);
        events.afterLayout();
        animations.dispatchEnded(events::animationEnded);
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

    /**
     * When the last {@link #frame} ran, in nanoseconds of the clock transitions and animations run on:
     * the time effects see.
     */
    public long frameTime() {
        return frameTime;
    }

    /**
     * Sets the time source of transitions, animations, the caret's blink and the effects' time
     * ({@link #frameTime}), in nanoseconds; {@link System#nanoTime} until set. Hosts set the game's,
     * so that whatever controls the game's time (a recording stepping it frame by frame) controls the
     * document's too.
     */
    public void clock(LongSupplier clock) {
        this.clock = clock;
    }
}
