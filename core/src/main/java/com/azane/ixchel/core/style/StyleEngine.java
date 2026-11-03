package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.LineHeight;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Selector matching, cascade and inheritance. Precedence, lowest first: user-agent rules, then the
 * author origin's component layer and theme layer, then unlayered author rules, each by
 * (specificity, sheet order, rule order); then inline style. {@code !important} declarations come
 * after all of these, the order of origins and layers reversed: unlayered author, theme layer,
 * component layer, user agent; then inline. Layers are not compared by specificity. An element's
 * {@linkplain Element#boundStyle() bound values} are inline declarations that replace the
 * {@code style} attribute's declarations of the same properties.
 *
 * <p>Custom properties cascade the same way and always inherit. Once an element's custom properties
 * are known, the {@code var()}s in their values and then in its other declarations are substituted;
 * a custom property whose {@code var()} cannot be resolved, or that refers to itself through others,
 * is not defined on the element; a longhand that cannot be resolved or parsed after substitution
 * takes its inherited value if it inherits, else its initial value, and is reported once.
 *
 * <p>An element gets a {@linkplain Element#before() ::before} or {@linkplain Element#after() ::after}
 * box while a rule for it matches and its {@code content} is {@code ""}. The box cascades only
 * those rules, matched against its element, and inherits from its element; it has no inline style.
 * It is restyled whenever its element is.
 */
public final class StyleEngine {
    private static final int USER_AGENT = 0;
    private static final int COMPONENT = 1;
    private static final int THEME = 2;
    private static final int AUTHOR = 3;

    /** {@code origin}: {@link #USER_AGENT} to {@link #AUTHOR}, the normal declarations' order. */
    private record Entry(Selector selector, Declarations.Compiled declarations, int origin, int specificity, int order) {
    }

    /** An element's compiled {@code style} attribute and the list it was compiled from. */
    private record Inline(List<Rule.Declaration> source, Declarations.Compiled compiled) {
    }

    private static final Comparator<Entry> CASCADE_ORDER = Comparator.comparingInt(Entry::origin)
            .thenComparingInt(Entry::specificity).thenComparingInt(Entry::order);

    /**
     * Sees each freshly cascaded style before it is stored, and may change it; children inherit
     * the changed values. Used by animations and transitions.
     */
    @FunctionalInterface
    public interface Hook {
        /**
         * @param previous the element's style before this restyle, {@code null} on its first
         * @param cascade  answers about {@code next} until the hook returns
         */
        void computed(Element e, ComputedStyle previous, ComputedStyle next, Cascade cascade);
    }

    /** What the cascade knows about the style a {@link Hook} is given. */
    public interface Cascade {
        /** Whether an {@code !important} declaration set {@code def}. */
        boolean important(PropertyDef<?> def);

        /** The {@code @keyframes} named {@code name}, the last defined in any sheet, or {@code null}. */
        Keyframes keyframes(String name);

        /**
         * A keyframe's {@code declared} value of {@code def} computed for the element whose style is
         * {@code next}: {@code var()}s, relative units and CSS-wide keywords resolved as in its cascade;
         * {@code null} when invalid after substitution, reported once.
         */
        Object compute(PropertyDef<?> def, Object declared, ComputedStyle next);
    }

    private final PropertyRegistry registry;
    private final Hook hook;
    private final Diagnostics diag;
    private final Index elements = new Index();
    private final Index before = new Index();
    private final Index after = new Index();
    /** The {@link Element} state bits some selector tests. */
    private int states;
    private final Map<String, Keyframes> keyframes = new HashMap<>();
    /** The ids of the longhands an {@code !important} declaration set in the style computed last. */
    private final BitSet important = new BitSet();
    private final HookCascade cascade = new HookCascade();
    /** Weakly keyed, so entries leave with their elements; a new inline style replaces its element's entry. */
    private final Map<Element, Inline> inlineCache = new WeakHashMap<>();
    private final List<Entry> scratch = new ArrayList<>();
    /** The longhands of the style being computed that the cascade left pending. */
    private final List<PropertyDef<?>> deferred = new ArrayList<>();
    private final Units units = new Units(0, 0, 0, 0);
    /** The font size {@code rem} is of for the elements being restyled. */
    private float rem;
    /** Whether a computed style used {@code vw} or {@code vh}. */
    private boolean usesViewport;

    /** {@code diag} receives errors from inline styles, compiled when an element is restyled with a new one. */
    public StyleEngine(PropertyRegistry registry, List<StyleSheet> sheets, Diagnostics diag) {
        this(registry, List.of(), List.of(), List.of(), sheets, diag, (e, previous, next, cascade) -> {
        });
    }

    /**
     * @param userAgent  sheets below every author rule, such as the form controls' defaults
     * @param components the author origin's component layer, the lowest layer
     * @param themes     the author origin's theme layer, above the component layer and below every
     *                   unlayered author rule
     * @param diag       also receives declarations found invalid after {@code var()} substitution
     */
    public StyleEngine(PropertyRegistry registry, List<StyleSheet> userAgent, List<StyleSheet> components,
                       List<StyleSheet> themes, List<StyleSheet> sheets, Diagnostics diag, Hook hook) {
        this.registry = registry;
        this.hook = hook;
        this.diag = diag;
        index(userAgent, USER_AGENT);
        index(components, COMPONENT);
        index(themes, THEME);
        index(sheets, AUTHOR);
    }

    private void index(List<StyleSheet> sheets, int origin) {
        int order = 0;
        for (StyleSheet sheet : sheets) {
            int base = order;
            for (StyleSheet.StyleRule r : sheet.rules()) {
                Entry e = new Entry(r.selector(), r.declarations(), origin, r.selector().specificity(), base + r.order());
                order = Math.max(order, e.order + 1);
                states |= r.selector().states();
                switch (r.selector().pseudoElement()) {
                    case NONE -> elements.add(e);
                    case BEFORE -> before.add(e);
                    case AFTER -> after.add(e);
                }
            }
            keyframes.putAll(sheet.keyframes());
        }
    }

    /** Whether some rule's selector tests one of the {@link Element} state bits in {@code mask}. */
    public boolean uses(int mask) {
        return (states & mask) != 0;
    }

    /**
     * Brings computed styles under {@code root} up to date. {@code root} itself gets initial values;
     * it stands for the viewport and is never matched by selectors.
     */
    public void restyle(Element root) {
        if (root.isStyleDirty() || root.computed() == null) {
            root.setComputed(ComputedStyle.initial(registry));
            restyleChildren(root, true, true);
        } else if (root.hasStyleDirtyDescendant()) {
            restyleChildren(root, false, true);
        }
        root.clearStyleDirty();
    }

    /**
     * Sets the viewport that {@code vw} and {@code vh} are of; {@code true} when it changed and a
     * computed style used it, so everything must be restyled.
     */
    public boolean viewport(float width, float height) {
        if (width == units.vw && height == units.vh) {
            return false;
        }
        units.vw = width;
        units.vh = height;
        return usesViewport;
    }

    private void restyleChildren(Element parent, boolean force, boolean top) {
        for (Node n : parent.children()) {
            if (!(n instanceof Element e)) {
                continue;
            }
            boolean self = force || e.isStyleDirty();
            if (top) {
                rem = parent.computed().get(CssProperties.FONT_SIZE).value();
            }
            if (self) {
                ComputedStyle previous = e.computed();
                Declarations.Compiled inline = inline(e);
                elements.collect(e, false, scratch);
                ComputedStyle next = compute(e, inline, parent.computed());
                cascade.parent = parent.computed();
                hook.computed(e, previous, next, cascade);
                e.setComputed(next);
            }
            if (self || e.hasStyleDirtyDescendant()) {
                if (top) {
                    rem = e.computed().get(CssProperties.FONT_SIZE).value();
                }
                restylePseudo(e, false, self);
                restylePseudo(e, true, self);
                restyleChildren(e, self, false);
            }
            e.clearStyleDirty();
        }
    }

    /** Generates, restyles or drops {@code host}'s {@code ::before} or {@code ::after} box. */
    private void restylePseudo(Element host, boolean isAfter, boolean force) {
        Element p = isAfter ? host.after() : host.before();
        if (!force && (p == null || !p.isStyleDirty())) {
            return;
        }
        Index rules = isAfter ? after : before;
        rules.collect(host, true, scratch);
        if (scratch.isEmpty()) {
            host.dropPseudo(isAfter);
            return;
        }
        ComputedStyle previous = p == null ? null : p.computed();
        ComputedStyle next = compute(null, Declarations.Compiled.EMPTY, host.computed());
        if (next.get(CssProperties.CONTENT) != Keywords.Content.EMPTY) {
            host.dropPseudo(isAfter);
            return;
        }
        p = host.generatePseudo(isAfter);
        cascade.parent = host.computed();
        hook.computed(p, previous, next, cascade);
        p.setComputed(next);
        p.clearStyleDirty();
    }

    /**
     * Cascades the rules in {@link #scratch}, which it sorts and then clears, over {@code inline}
     * and {@code e}'s bound values; {@code e} is {@code null} for a pseudo-element, which has neither.
     */
    private ComputedStyle compute(Element e, Declarations.Compiled inline, ComputedStyle parent) {
        List<Entry> matched = scratch;
        matched.sort(CASCADE_ORDER);
        important.clear();

        Map<PropertyDef<?>, Object> bound = e == null ? Map.of() : e.boundStyle();
        Map<String, List<ComponentValue>> boundCustom = e == null ? Map.of() : e.boundCustom();
        boolean anyBound = !bound.isEmpty();

        ComputedStyle cs = ComputedStyle.inheriting(registry, parent, custom(boundCustom, matched, inline, parent.custom()));
        for (int pass = 0; pass < 2; pass++) {
            boolean important = pass == 1;
            if (important) {
                for (int origin = AUTHOR; origin >= USER_AGENT; origin--) {
                    for (Entry m : matched) {
                        if (m.origin == origin) {
                            apply(m.declarations.longhands(), true, cs, parent);
                        }
                    }
                }
            } else {
                for (Entry m : matched) {
                    apply(m.declarations.longhands(), false, cs, parent);
                }
            }
            for (Declarations.Longhand d : inline.longhands()) {
                if (d.important() == important && !(anyBound && bound.containsKey(d.property()))) {
                    set(cs, d.property(), d.value(), parent);
                    if (important) {
                        this.important.set(d.property().id());
                    }
                }
            }
            if (!important && anyBound) {
                for (Map.Entry<PropertyDef<?>, Object> b : bound.entrySet()) {
                    set(cs, b.getKey(), b.getValue(), parent);
                }
            }
        }
        resolveDeferred(cs, parent);
        matched.clear();
        return cs;
    }

    /**
     * An element's custom properties: {@code inherited} overridden by its cascaded declarations and {@code bound} values,
     * each with its {@code var()}s substituted; {@code inherited} itself when nothing declares one.
     */
    private Map<String, List<ComponentValue>> custom(Map<String, List<ComponentValue>> bound, List<Entry> matched,
                                                     Declarations.Compiled inline, Map<String, List<ComponentValue>> inherited) {
        Map<String, List<ComponentValue>> declared = null;
        for (int pass = 0; pass < 2; pass++) {
            boolean important = pass == 1;
            for (int origin = important ? AUTHOR : USER_AGENT; important ? origin >= USER_AGENT : origin <= AUTHOR;
                 origin += important ? -1 : 1) {
                for (Entry m : matched) {
                    if (m.origin == origin) {
                        declared = declare(m.declarations.customs(), important, null, declared);
                    }
                }
            }
            declared = declare(inline.customs(), important, bound, declared);
            if (!important && !bound.isEmpty()) {
                if (declared == null) {
                    declared = new LinkedHashMap<>();
                }
                declared.putAll(bound);
            }
        }
        if (declared == null) {
            return inherited;
        }
        Map<String, List<ComponentValue>> out = new HashMap<>(inherited);
        Map<String, List<ComponentValue>> resolved = new HashMap<>();
        Set<String> resolving = new HashSet<>();
        for (String name : declared.keySet()) {
            List<ComponentValue> v = resolve(name, declared, inherited, resolved, resolving);
            if (v == null) {
                out.remove(name);
            } else {
                out.put(name, v);
            }
        }
        return Map.copyOf(out);
    }

    /** Puts {@code customs} of the given importance into {@code into}, created on first need; skips names in {@code skip}. */
    private static Map<String, List<ComponentValue>> declare(List<Declarations.Custom> customs, boolean important,
                                                             Map<String, List<ComponentValue>> skip,
                                                             Map<String, List<ComponentValue>> into) {
        for (Declarations.Custom c : customs) {
            if (c.important() == important && (skip == null || !skip.containsKey(c.name()))) {
                if (into == null) {
                    into = new LinkedHashMap<>();
                }
                into.put(c.name(), c.value());
            }
        }
        return into;
    }

    /**
     * The computed value of the declared custom property {@code name}; {@code null} when it is
     * {@code initial}, refers to itself, or has a {@code var()} with neither a value nor a fallback.
     */
    private static List<ComponentValue> resolve(String name, Map<String, List<ComponentValue>> declared,
                                                Map<String, List<ComponentValue>> inherited,
                                                Map<String, List<ComponentValue>> resolved, Set<String> resolving) {
        if (resolved.containsKey(name)) {
            return resolved.get(name);
        }
        if (!resolving.add(name)) {
            return null;
        }
        List<ComponentValue> raw = declared.get(name);
        Declarations.Wide wide = Declarations.wideKeyword(raw);
        List<ComponentValue> v;
        if (wide == Declarations.Wide.INHERIT) {
            v = inherited.get(name);
        } else if (wide == Declarations.Wide.INITIAL) {
            v = null;
        } else {
            v = Vars.substitute(raw, n -> declared.containsKey(n)
                    ? resolve(n, declared, inherited, resolved, resolving) : inherited.get(n));
        }
        resolving.remove(name);
        resolved.put(name, v);
        return v;
    }

    private Declarations.Compiled inline(Element e) {
        List<Rule.Declaration> source = e.inlineStyle();
        if (source.isEmpty()) {
            return Declarations.Compiled.EMPTY;
        }
        Inline cached = inlineCache.get(e);
        if (cached == null || cached.source != source) {
            cached = new Inline(source, Declarations.compile(source, registry, diag));
            inlineCache.put(e, cached);
        }
        return cached.compiled;
    }

    /** Elements with a compiled inline style held for reuse; for tests. */
    int inlineCacheSize() {
        return inlineCache.size();
    }

    /** Rules by their rightmost compound's id, else first class, else tag, else none. */
    private static final class Index {
        private final Map<String, List<Entry>> byId = new HashMap<>();
        private final Map<String, List<Entry>> byClass = new HashMap<>();
        private final Map<String, List<Entry>> byTag = new HashMap<>();
        private final List<Entry> universal = new ArrayList<>();
        private boolean empty = true;

        void add(Entry e) {
            empty = false;
            Selector.Compound key = e.selector.rightmost();
            if (key.id() != null) {
                byId.computeIfAbsent(key.id(), k -> new ArrayList<>()).add(e);
            } else if (!key.classes().isEmpty()) {
                byClass.computeIfAbsent(key.classes().get(0), k -> new ArrayList<>()).add(e);
            } else if (key.tag() != null) {
                byTag.computeIfAbsent(key.tag(), k -> new ArrayList<>()).add(e);
            } else {
                universal.add(e);
            }
        }

        /** Adds to {@code out} the rules matching {@code e}, or with {@code origin}, the pseudo-element rules whose element it is. */
        void collect(Element e, boolean origin, List<Entry> out) {
            if (empty) {
                return;
            }
            String id = e.attribute("id");
            if (id != null) {
                collect(byId.get(id), e, origin, out);
            }
            for (String c : e.classes()) {
                collect(byClass.get(c), e, origin, out);
            }
            collect(byTag.get(e.tag()), e, origin, out);
            collect(universal, e, origin, out);
        }

        private static void collect(List<Entry> candidates, Element e, boolean origin, List<Entry> out) {
            if (candidates == null) {
                return;
            }
            for (Entry c : candidates) {
                if (origin ? c.selector.matchesOrigin(e) : c.selector.matches(e)) {
                    out.add(c);
                }
            }
        }
    }

    private void apply(List<Declarations.Longhand> decls, boolean important, ComputedStyle cs, ComputedStyle parent) {
        for (Declarations.Longhand d : decls) {
            if (d.important() == important) {
                set(cs, d.property(), d.value(), parent);
                if (important) {
                    this.important.set(d.property().id());
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void set(ComputedStyle cs, PropertyDef<?> def, Object value, ComputedStyle parent) {
        if (value instanceof Declarations.Pending) {
            deferred.add(def);
            cs.set((PropertyDef<Object>) def, value);
        } else {
            setValue(cs, def, value, parent);
        }
    }

    /**
     * Resolves what the cascade left {@linkplain #set pending}, {@code font-size} first and the rest
     * relative to it, and computes percentages of {@code font-size} and {@code line-height} to pixels.
     */
    private void resolveDeferred(ComputedStyle cs, ComputedStyle parent) {
        float parentSize = parent.get(CssProperties.FONT_SIZE).value();
        units.rem = rem;
        units.em = parentSize;
        resolve(cs, CssProperties.FONT_SIZE, parent);
        Length size = cs.get(CssProperties.FONT_SIZE);
        if (size.unit() != Length.Unit.PX) {
            cs.set(CssProperties.FONT_SIZE, Length.px(size.resolve(parentSize)));
        }
        units.em = cs.get(CssProperties.FONT_SIZE).value();
        for (PropertyDef<?> def : deferred) {
            resolve(cs, def, parent);
        }
        deferred.clear();
        LineHeight lh = cs.get(CssProperties.LINE_HEIGHT);
        if (lh.kind() == LineHeight.Kind.LENGTH && lh.length().unit() != Length.Unit.PX) {
            cs.set(CssProperties.LINE_HEIGHT, LineHeight.of(Length.px(lh.length().resolve(units.em))));
        }
        if (units.viewportUsed) {
            usesViewport = true;
            units.viewportUsed = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void resolve(ComputedStyle cs, PropertyDef<?> def, ComputedStyle parent) {
        if (cs.get((PropertyDef<Object>) def) instanceof Declarations.Pending p) {
            Object value = p.resolve(cs.custom(), units, diag);
            if (value == null) {
                value = def.inherited() ? Declarations.Wide.INHERIT : Declarations.Wide.INITIAL;
            }
            setValue(cs, def, value, parent);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void setValue(ComputedStyle cs, PropertyDef<T> def, Object value, ComputedStyle parent) {
        if (value == Declarations.Wide.INHERIT) {
            cs.set(def, parent.get(def));
        } else if (value == Declarations.Wide.INITIAL) {
            cs.set(def, def.initial());
        } else {
            cs.set(def, (T) value);
        }
    }

    /** The {@link Cascade} handed to the hook; {@code parent} is the style the hooked one inherits from. */
    private final class HookCascade implements Cascade {
        ComputedStyle parent;

        @Override
        public boolean important(PropertyDef<?> def) {
            return important.get(def.id());
        }

        @Override
        public Keyframes keyframes(String name) {
            return keyframes.get(name);
        }

        @Override
        public Object compute(PropertyDef<?> def, Object declared, ComputedStyle next) {
            float parentSize = parent.get(CssProperties.FONT_SIZE).value();
            float size = next.get(CssProperties.FONT_SIZE).value();
            Object v = declared;
            if (v instanceof Declarations.Pending p) {
                units.rem = rem;
                units.em = def == CssProperties.FONT_SIZE ? parentSize : size;
                v = p.resolve(next.custom(), units, diag);
                if (units.viewportUsed) {
                    usesViewport = true;
                    units.viewportUsed = false;
                }
                if (v == null) {
                    return null;
                }
            }
            if (v == Declarations.Wide.INHERIT) {
                return parent.get(def);
            }
            if (v == Declarations.Wide.INITIAL) {
                return def.initial();
            }
            if (def == CssProperties.FONT_SIZE && v instanceof Length l && l.unit() != Length.Unit.PX) {
                return Length.px(l.resolve(parentSize));
            }
            if (def == CssProperties.LINE_HEIGHT && v instanceof LineHeight lh && lh.kind() == LineHeight.Kind.LENGTH
                    && lh.length().unit() != Length.Unit.PX) {
                return LineHeight.of(Length.px(lh.length().resolve(size)));
            }
            return v;
        }
    }
}
