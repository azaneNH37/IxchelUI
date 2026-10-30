package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.selector.Selector;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;

import java.util.ArrayList;
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
     * the changed values. Used by transitions.
     */
    @FunctionalInterface
    public interface Hook {
        /** @param previous the element's style before this restyle, {@code null} on its first */
        void computed(Element e, ComputedStyle previous, ComputedStyle next);
    }

    private final PropertyRegistry registry;
    private final Hook hook;
    private final Diagnostics diag;
    private final Map<String, List<Entry>> byId = new HashMap<>();
    private final Map<String, List<Entry>> byClass = new HashMap<>();
    private final Map<String, List<Entry>> byTag = new HashMap<>();
    private final List<Entry> universal = new ArrayList<>();
    /** Weakly keyed, so entries leave with their elements; a new inline style replaces its element's entry. */
    private final Map<Element, Inline> inlineCache = new WeakHashMap<>();
    private final List<Entry> scratch = new ArrayList<>();

    /** {@code diag} receives errors from inline styles, compiled when an element is restyled with a new one. */
    public StyleEngine(PropertyRegistry registry, List<StyleSheet> sheets, Diagnostics diag) {
        this(registry, List.of(), List.of(), List.of(), sheets, diag, (e, previous, next) -> {
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
                Selector.Compound key = r.selector().rightmost();
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
        }
    }

    /**
     * Brings computed styles under {@code root} up to date. {@code root} itself gets initial values;
     * it stands for the viewport and is never matched by selectors.
     */
    public void restyle(Element root) {
        if (root.isStyleDirty() || root.computed() == null) {
            root.setComputed(ComputedStyle.initial(registry));
            restyleChildren(root, true);
        } else if (root.hasStyleDirtyDescendant()) {
            restyleChildren(root, false);
        }
        root.clearStyleDirty();
    }

    private void restyleChildren(Element parent, boolean force) {
        for (Node n : parent.children()) {
            if (!(n instanceof Element e)) {
                continue;
            }
            boolean self = force || e.isStyleDirty();
            if (self) {
                ComputedStyle next = compute(e, parent.computed());
                hook.computed(e, e.computed(), next);
                e.setComputed(next);
            }
            if (self || e.hasStyleDirtyDescendant()) {
                restyleChildren(e, self);
            }
            e.clearStyleDirty();
        }
    }

    private ComputedStyle compute(Element e, ComputedStyle parent) {
        List<Entry> matched = scratch;
        matched.clear();
        String id = e.attribute("id");
        if (id != null) {
            collect(byId.get(id), e, matched);
        }
        for (String c : e.classes()) {
            collect(byClass.get(c), e, matched);
        }
        collect(byTag.get(e.tag()), e, matched);
        collect(universal, e, matched);
        matched.sort(CASCADE_ORDER);

        Declarations.Compiled inline = inline(e);
        Map<PropertyDef<?>, Object> bound = e.boundStyle();
        boolean anyBound = !bound.isEmpty();

        ComputedStyle cs = ComputedStyle.inheriting(registry, parent, custom(e, matched, inline, parent.custom()));
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
                }
            }
            if (!important && anyBound) {
                for (Map.Entry<PropertyDef<?>, Object> b : bound.entrySet()) {
                    set(cs, b.getKey(), b.getValue(), parent);
                }
            }
        }
        matched.clear();
        return cs;
    }

    /**
     * The element's custom properties: {@code inherited} overridden by its cascaded declarations,
     * each with its {@code var()}s substituted; {@code inherited} itself when nothing declares one.
     */
    private Map<String, List<ComponentValue>> custom(Element e, List<Entry> matched, Declarations.Compiled inline,
                                                     Map<String, List<ComponentValue>> inherited) {
        Map<String, List<ComponentValue>> bound = e.boundCustom();
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

    private static void collect(List<Entry> candidates, Element e, List<Entry> out) {
        if (candidates == null) {
            return;
        }
        for (Entry c : candidates) {
            if (c.selector.matches(e)) {
                out.add(c);
            }
        }
    }

    private void apply(List<Declarations.Longhand> decls, boolean important, ComputedStyle cs, ComputedStyle parent) {
        for (Declarations.Longhand d : decls) {
            if (d.important() == important) {
                set(cs, d.property(), d.value(), parent);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void set(ComputedStyle cs, PropertyDef<T> def, Object value, ComputedStyle parent) {
        if (value instanceof Declarations.Pending p) {
            value = p.resolve(cs.custom(), diag);
            if (value == null) {
                value = def.inherited() ? Declarations.Wide.INHERIT : Declarations.Wide.INITIAL;
            }
        }
        if (value == Declarations.Wide.INHERIT) {
            cs.set(def, parent.get(def));
        } else if (value == Declarations.Wide.INITIAL) {
            cs.set(def, def.initial());
        } else {
            cs.set(def, (T) value);
        }
    }
}
