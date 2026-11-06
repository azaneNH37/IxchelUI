package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Plain data shared by the pipeline stages: the tree, classes, attributes and interaction state come
 * from the view, bindings and events; {@link #computed()} and {@link #box()} are written by the
 * style and layout passes.
 *
 * <p>Style invalidation: a class, state, attribute (attributes take part in {@code [attr]}
 * selectors), inline style or bound style change marks the element style-dirty, which means the
 * element and its whole subtree are restyled; its ancestors get a descendant-dirty mark so the style
 * pass can skip clean subtrees. An already styled element appended under a parent is marked
 * style-dirty too, since inherited values and combinator matches depend on its ancestors.
 *
 * <p>Tree changes (child-list changes here or text changes in a child) mark the element and its
 * ancestors as {@linkplain #hasTreeChange() changed} until the layout pass clears them.
 *
 * <p>A {@linkplain #setScrollTop scroll offset} change only moves painted content: it marks the tree's
 * top element as {@linkplain #needsRepaint() needing a repaint}, without style or layout work, as does
 * a change of a {@link #textField()}. Each scroll offset change, the layout pass clamping one included,
 * also marks the element {@linkplain #takeScrolled() scrolled} for the {@code scroll} event.
 */
@HostApi
public final class Element extends Node {
    public static final int HOVER = 1;
    public static final int ACTIVE = 1 << 1;
    public static final int FOCUS = 1 << 2;
    /**
     * Set while this element or an ancestor has a {@code disabled} attribute, as under a {@code <fieldset>};
     * {@code :disabled}. A change restyles the whole subtree, and an element moved under another
     * parent is restyled, so the walk up happens only where the state is asked for.
     */
    public static final int DISABLED = 1 << 3;
    /**
     * A checked checkbox ({@link FormControl#setChecked}); {@code :checked}. An {@code <option>}
     * matches it while it is its select's {@linkplain FormControl#selectedOption() selected option}.
     */
    public static final int CHECKED = 1 << 4;
    /**
     * A {@code <select>} or {@code <dialog>} in the {@link TopLayer}, or its shown tooltip or drag preview;
     * a {@code <details>} while it has an {@code open} attribute; {@code :open}.
     */
    public static final int OPEN = 1 << 5;
    /** The root of a {@link Presence} unit in its first frame of entering, or while exiting; {@code :-ix-absent}. */
    public static final int ABSENT = 1 << 6;
    /** The root of a {@link Presence} unit from its insertion until its subtree stops moving; {@code :-ix-entering}. */
    public static final int ENTERING = 1 << 7;
    /** The root of a {@link Presence} unit from its removal until its subtree stops moving; {@code :-ix-exiting}. */
    public static final int EXITING = 1 << 8;
    /** The type of every {@code ::before} box; see {@link #before()}. */
    public static final ElementType BEFORE = ElementType.plain("::before");
    /** The type of every {@code ::after} box; see {@link #after()}. */
    public static final ElementType AFTER = ElementType.plain("::after");

    private static final int STYLE_DIRTY = 1;
    private static final int DESCENDANT_STYLE_DIRTY = 1 << 1;
    private static final int TREE_CHANGED = 1 << 2;
    /** Only meaningful on the top element of a tree. */
    private static final int REPAINT = 1 << 3;
    private static final int SCROLLED = 1 << 4;
    /** Only meaningful on the top element of a tree: some element below is {@link #SCROLLED}. */
    private static final int SCROLL_PENDING = 1 << 5;

    private final ElementType type;
    private final List<Node> children = new ArrayList<>();
    private final List<Node> childrenView = Collections.unmodifiableList(children);
    private final Set<String> classes = new LinkedHashSet<>();
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private final List<Directive> directives = new ArrayList<>();
    private int stateBits;
    private int dirty = STYLE_DIRTY | TREE_CHANGED;
    private ComputedStyle computed;
    private List<Rule.Declaration> inlineStyle = List.of();
    private Object payload;
    private Object hostState;
    private float scrollTop;
    private float maxScrollTop;
    /** A {@link #requestScrollTop} the next layout applies, or -1. */
    private float scrollRequest = -1;
    private Map<PropertyDef<?>, Object> boundStyle;
    private Map<PropertyDef<?>, Object> boundStyleView = Map.of();
    private Map<String, List<ComponentValue>> boundCustom;
    private Map<String, List<ComponentValue>> boundCustomView = Map.of();
    private List<Registration> listeners;
    /** Created on first use, once the view has set {@code type}. */
    private FormControl control;
    private boolean controlResolved;
    private Element before;
    private Element after;
    private boolean inert;

    private record Registration(UiEvent.Type type, boolean capture, UiEvent.Listener listener) {
    }

    public Element(ElementType type, SourcePos pos) {
        super(pos);
        this.type = type;
    }

    public ElementType type() {
        return type;
    }

    public String tag() {
        return type.tag();
    }

    /**
     * The form state of an {@code <input>} of a supported type or a {@code <select>}; {@code null} for
     * every other element. The kind follows the {@code type} attribute when first asked.
     */
    public FormControl control() {
        if (!controlResolved) {
            controlResolved = true;
            FormControl.Kind kind = type.facet(FormFacet.KEY).kind(this);
            control = kind == null ? null : new FormControl(this, kind);
        }
        return control;
    }

    /** The editing state of a text or number {@code <input>}; {@code null} for every other element. */
    public TextField textField() {
        FormControl c = control();
        return c == null ? null : c.text();
    }

    /**
     * How far the scroll containers above this element move it up where it is painted and hit: the
     * sum of their {@link #scrollTop()}s, up to a {@link TopLayer} entry's placed content (an open
     * dialog, the options of an open select, the shown tooltip), whose boxes layout already wrote
     * where they show.
     */
    public float scrollOffset() {
        float sum = 0;
        for (Element a = this; a.parent != null && !a.isPlaced(); a = a.parent) {
            sum += a.parent.scrollTop;
        }
        return sum;
    }

    /** An open dialog, the shown tooltip or drag preview, or an option of an open select: laid out against the viewport. */
    public boolean isPlaced() {
        if (rendersInTopLayer()) return hasState(OPEN);
        if (parent == null || !parent.hasState(OPEN)) return false;
        LayerFacet layer = parent.type.facet(LayerFacet.KEY);
        return !layer.selfContent() && layer.contains(parent, this);
    }

    /**
     * Whether the element is content of the {@link TopLayer} rather than of its parent's flow: its
     * presentation hides it in normal flow. Layout, paint, hit
     * testing and focus order skip it where it sits in the tree.
     */
    public boolean rendersInTopLayer() {
        return type.facet(LayerFacet.KEY).hiddenInFlow();
    }

    /** A tooltip or drag preview: shown by itself at the pointer, in a layout tree of its own. */
    public boolean isShownAtPointer() {
        return type.facet(LayerFacet.KEY).atPointer();
    }

    /** Unmodifiable live view. */
    public List<Node> children() {
        return childrenView;
    }

    /**
     * The {@code ::before} box the style pass generated for this element, or {@code null}. A
     * pseudo-element is not among the {@link #children()}, so queries, views and bindings never
     * see it; its {@link #parent()} is this element, so it inherits from it and layout and paint
     * place it before the first child.
     */
    public Element before() {
        return before;
    }

    /** The {@code ::after} box, placed after the last child; see {@link #before()}. */
    public Element after() {
        return after;
    }

    /** Called by the style pass: the {@code ::before} or {@code ::after} box, created when it was not generated. */
    public Element generatePseudo(boolean isAfter) {
        Element p = isAfter ? after : before;
        if (p == null) {
            p = new Element(isAfter ? AFTER : BEFORE, pos());
            p.parent = this;
            if (isAfter) {
                after = p;
            } else {
                before = p;
            }
            markTreeChanged();
        }
        return p;
    }

    /** Called by the style pass when the {@code ::before} or {@code ::after} box is no longer generated. */
    public void dropPseudo(boolean isAfter) {
        Element p = isAfter ? after : before;
        if (p != null) {
            p.parent = null;
            if (isAfter) {
                after = null;
            } else {
                before = null;
            }
            markTreeChanged();
        }
    }

    /** The text of every descendant text node, in document order, as in the DOM. */
    public String textContent() {
        StringBuilder sb = new StringBuilder();
        appendText(sb);
        return sb.toString();
    }

    private void appendText(StringBuilder sb) {
        for (Node n : children) {
            if (n instanceof TextNode t) {
                sb.append(t.text());
            } else if (n instanceof Element e) {
                e.appendText(sb);
            }
        }
    }

    /** Appends {@code child}, detaching it from its current parent first. */
    public void appendChild(Node child) {
        insertChild(child.parent == this ? children.size() - 1 : children.size(), child);
    }

    /**
     * Inserts {@code child} at {@code index}, detaching it from its current parent first; the index
     * counts children after that removal.
     */
    public void insertChild(int index, Node child) {
        if (child instanceof Element e) {
            for (Element a = this; a != null; a = a.parent) {
                if (a == e) {
                    throw new IllegalArgumentException(e.type + " cannot become its own descendant");
                }
            }
        }
        if (child.parent != null) {
            child.parent.removeChild(child);
        }
        children.add(index, child);
        child.parent = this;
        markTreeChanged();
        if (child instanceof Element e) {
            if (e.computed != null) {
                e.dirty |= STYLE_DIRTY;
            }
            if (e.dirty != 0) {
                markDescendantStyleDirty(this);
            }
        }
    }

    public void removeChild(Node child) {
        if (child.parent != this) {
            throw new IllegalArgumentException("not a child of " + type);
        }
        children.remove(child);
        child.parent = null;
        markTreeChanged();
    }

    @Override
    public Element copyTemplate() {
        Element e = new Element(type, pos());
        e.classes.addAll(classes);
        e.attributes.putAll(attributes);
        if (attributes.containsKey("disabled")) {
            e.stateBits |= DISABLED;
        }
        e.inlineStyle = inlineStyle;
        e.directives.addAll(directives);
        for (Node n : children) {
            Node c = n.copyTemplate();
            e.children.add(c);
            c.parent = e;
        }
        return e;
    }

    public Set<String> classes() {
        return Collections.unmodifiableSet(classes);
    }

    public boolean hasClass(String name) {
        return classes.contains(name);
    }

    public void addClass(String name) {
        if (classes.add(name)) {
            markStyleDirty();
        }
    }

    public void removeClass(String name) {
        if (classes.remove(name)) {
            markStyleDirty();
        }
    }

    /** Plain attributes; {@code class} and {@code style} are not stored here. */
    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public String attribute(String name) {
        return attributes.get(name);
    }

    /**
     * Sets a plain attribute; a change marks the element style-dirty. {@code disabled} is a boolean
     * attribute: present (any value) sets {@link #DISABLED}.
     *
     * @throws IllegalArgumentException for {@code class} (use {@link #addClass}/{@link #removeClass})
     *         and {@code style} (use {@link #setInlineStyle})
     */
    public void setAttribute(String name, String value) {
        switch (name) {
            case "class" -> throw new IllegalArgumentException("use addClass/removeClass instead of setAttribute(\"class\")");
            case "style" -> throw new IllegalArgumentException("use setInlineStyle instead of setAttribute(\"style\")");
            default -> {
            }
        }
        String old = attributes.put(name, value);
        if (!value.equals(old)) {
            markStyleDirty();
        }
        if (name.equals("disabled")) {
            setState(DISABLED, true);
        }
    }

    public void removeAttribute(String name) {
        if (attributes.remove(name) == null) {
            return;
        }
        markStyleDirty();
        if (name.equals("disabled")) {
            setState(DISABLED, false);
        }
    }

    /** The view's directives ({@code bind:*}, {@code on:*}, {@code state:*}, block attributes), in source order. */
    public List<Directive> directives() {
        return Collections.unmodifiableList(directives);
    }

    public void addDirective(Directive directive) {
        directives.add(directive);
    }

    /** The host object a drawn element is drawn from; set by its payload binding. */
    public Object payload() {
        return payload;
    }

    /** Sets the payload. Like plain attributes it marks nothing dirty; the binding that writes it requests the repaint. */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /** State the element's painter keeps per element; {@code null} until set. The core never reads it. */
    public Object hostState() {
        return hostState;
    }

    /** Sets {@link #hostState}; marks nothing dirty. */
    public void setHostState(Object state) {
        this.hostState = state;
    }

    /** Adds a listener; {@code capture} listeners run on the way down, the others while bubbling up. */
    public void addEventListener(UiEvent.Type type, boolean capture, UiEvent.Listener listener) {
        if (listeners == null) {
            listeners = new ArrayList<>(2);
        }
        listeners.add(new Registration(type, capture, listener));
    }

    /**
     * Runs this element's listeners for {@code event} in its current phase: capture listeners while
     * capturing, the others while bubbling, all of them in registration order at the target.
     */
    public void runListeners(UiEvent event) {
        if (listeners == null) {
            return;
        }
        UiEvent.Phase phase = event.phase();
        for (int i = 0, n = listeners.size(); i < n; i++) {
            Registration r = listeners.get(i);
            if (r.type == event.type() && (phase == UiEvent.Phase.TARGET || r.capture == (phase == UiEvent.Phase.CAPTURE))) {
                r.listener.handle(event);
            }
        }
    }

    public boolean hasState(int bit) {
        if ((stateBits & bit) != 0) {
            return true;
        }
        if (bit == DISABLED) {
            for (Element a = parent; a != null; a = a.parent) {
                if ((a.stateBits & DISABLED) != 0) {
                    return true;
                }
            }
            return false;
        }
        if (bit == OPEN) {
            return type.facet(FormFacet.KEY).openByAttribute() && attributes.containsKey("open");
        }
        return bit == CHECKED && type.facet(FormFacet.KEY).checked(this);
    }

    public void setState(int bit, boolean on) {
        int next = on ? stateBits | bit : stateBits & ~bit;
        if (next != stateBits) {
            stateBits = next;
            markStyleDirty();
        }
    }

    /**
     * Whether this element, the root of an exiting {@link Presence} unit, is inert: it and its subtree
     * are never hit nor focused. Not a style state.
     */
    public boolean isInert() {
        return inert;
    }

    void setInert(boolean inert) {
        this.inert = inert;
    }

    /** Declarations from the {@code style} attribute, parsed by the view loader. */
    public List<Rule.Declaration> inlineStyle() {
        return inlineStyle;
    }

    public void setInlineStyle(List<Rule.Declaration> declarations) {
        inlineStyle = List.copyOf(declarations);
        markStyleDirty();
    }

    /**
     * Values written by {@code bind:style:*}, keyed by longhand; unmodifiable live view. Each acts as
     * this element's only inline declaration of its property: it replaces the {@code style}
     * attribute's declaration of that property and is never {@code !important}.
     */
    public Map<PropertyDef<?>, Object> boundStyle() {
        return boundStyleView;
    }

    /**
     * Sets the bound value of {@code property} (its value type, or a CSS-wide keyword from
     * {@code style.Declarations}); {@code null} removes it, so the cascade applies again. A change marks
     * the element style-dirty.
     */
    public void setBoundStyle(PropertyDef<?> property, Object value) {
        if (boundStyle == null) {
            if (value == null) {
                return;
            }
            boundStyle = new IdentityHashMap<>(4);
            boundStyleView = Collections.unmodifiableMap(boundStyle);
        }
        Object old = value == null ? boundStyle.remove(property) : boundStyle.put(property, value);
        if (!Objects.equals(old, value)) {
            markStyleDirty();
        }
    }

    /**
     * Custom property values written by {@code bind:style:--name}, as {@link #boundStyle()} is for
     * longhands; unmodifiable live view.
     */
    public Map<String, List<ComponentValue>> boundCustom() {
        return boundCustomView;
    }

    /** Sets the bound value of the custom property {@code name}; {@code null} removes it. A change marks the element style-dirty. */
    public void setBoundCustom(String name, List<ComponentValue> value) {
        if (boundCustom == null) {
            if (value == null) {
                return;
            }
            boundCustom = new HashMap<>(4);
            boundCustomView = Collections.unmodifiableMap(boundCustom);
        }
        Object old = value == null ? boundCustom.remove(name) : boundCustom.put(name, List.copyOf(value));
        if (!Objects.equals(old, value)) {
            markStyleDirty();
        }
    }

    public ComputedStyle computed() {
        return computed;
    }

    public void setComputed(ComputedStyle computed) {
        this.computed = computed;
    }

    /**
     * How far the content of this scroll container ({@code overflow} other than {@code visible}) is
     * moved up, in whole GUI pixels; descendants paint and hit-test that much higher than their
     * {@linkplain #box() layout box}. Always 0 for other elements.
     */
    public float scrollTop() {
        return scrollTop;
    }

    /**
     * The largest {@link #scrollTop()} the last layout allows: the scrollable overflow below the box.
     * 0 when the content fits, for elements that are not scroll containers, and before the first layout.
     */
    public float maxScrollTop() {
        return maxScrollTop;
    }

    /**
     * Sets the scroll offset, rounded to whole pixels and clamped to {@code [0, maxScrollTop()]}. A
     * change is painted by the next frame without restyle or relayout.
     */
    public void setScrollTop(float top) {
        float next = Math.max(0, Math.min(Math.round(top), maxScrollTop));
        scrollRequest = -1;
        if (next != scrollTop) {
            scrollTop = next;
            markScrolled();
        }
    }

    /**
     * Asks for scroll offset {@code top} against the range of the next layout, for content that
     * changes in the same frame: the offset moves now as far as the current range allows, and the
     * next {@link #setMaxScrollTop} clamps the request into its new range. A later
     * {@link #setScrollTop} cancels it.
     */
    public void requestScrollTop(float top) {
        setScrollTop(top);
        scrollRequest = Math.max(0, Math.round(top));
    }

    /**
     * Called by the layout pass with the new scrollable range; the offset (or a pending request) is
     * clamped into it. A request that could not be met counts as a scroll even when the offset stays,
     * so whoever asked learns the offset it got.
     */
    public void setMaxScrollTop(float max) {
        maxScrollTop = Math.max(0, max);
        float request = scrollRequest;
        scrollRequest = -1;
        float next = Math.min(request >= 0 ? request : scrollTop, maxScrollTop);
        if (next != scrollTop || request >= 0 && request != next) {
            scrollTop = next;
            markScrolled();
        }
    }

    private void markScrolled() {
        dirty |= SCROLLED;
        top().dirty |= REPAINT | SCROLL_PENDING;
    }

    /** Whether the scroll offset changed since the last call; clears the mark. */
    public boolean takeScrolled() {
        boolean was = (dirty & SCROLLED) != 0;
        dirty &= ~SCROLLED;
        return was;
    }

    /** On the top element of a tree: whether an element below may be {@linkplain #takeScrolled() scrolled}; clears the mark. */
    public boolean takeScrollPending() {
        boolean was = (dirty & SCROLL_PENDING) != 0;
        dirty &= ~SCROLL_PENDING;
        return was;
    }

    void requestRepaint() {
        top().dirty |= REPAINT;
    }

    private Element top() {
        Element t = this;
        while (t.parent != null) {
            t = t.parent;
        }
        return t;
    }

    /** On the top element of a tree: whether a scroll offset or a text field below changed since the last paint. */
    public boolean needsRepaint() {
        return (dirty & REPAINT) != 0;
    }

    /** Called by the paint pass on the element it painted from. */
    public void clearRepaint() {
        dirty &= ~REPAINT;
    }

    public boolean isStyleDirty() {
        return (dirty & STYLE_DIRTY) != 0;
    }

    public boolean hasStyleDirtyDescendant() {
        return (dirty & DESCENDANT_STYLE_DIRTY) != 0;
    }

    /** Called by the style pass once this element and its subtree are up to date. */
    public void clearStyleDirty() {
        dirty &= ~(STYLE_DIRTY | DESCENDANT_STYLE_DIRTY);
    }

    public void markStyleDirty() {
        dirty |= STYLE_DIRTY;
        markDescendantStyleDirty(parent);
    }
    private static void markDescendantStyleDirty(Element from) {
        for (Element a = from; a != null && (a.dirty & DESCENDANT_STYLE_DIRTY) == 0; a = a.parent) {
            a.dirty |= DESCENDANT_STYLE_DIRTY;
        }
    }

    /** Whether the child list of this element or a descendant, or the text of a descendant, changed. */
    public boolean hasTreeChange() {
        return (dirty & TREE_CHANGED) != 0;
    }

    /** Called by the layout pass once it has synced this element's children. */
    public void clearTreeChange() {
        dirty &= ~TREE_CHANGED;
    }

    void markTreeChanged() {
        for (Element a = this; a != null && (a.dirty & TREE_CHANGED) == 0; a = a.parent) {
            a.dirty |= TREE_CHANGED;
        }
    }
}
