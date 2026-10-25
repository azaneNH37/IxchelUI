package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain data shared by the pipeline stages: the tree, classes, attributes and interaction state come
 * from the template, bindings and events; {@link #computed()} and {@link #box()} are written by the
 * style and layout passes.
 *
 * <p>Style invalidation: a class or state change marks the element style-dirty, which means the
 * element and its whole subtree are restyled; its ancestors get a descendant-dirty mark so the style
 * pass can skip clean subtrees.
 */
public final class Element extends Node {
    public static final int HOVER = 1;
    public static final int ACTIVE = 1 << 1;
    public static final int FOCUS = 1 << 2;

    private static final int STYLE_DIRTY = 1;
    private static final int DESCENDANT_STYLE_DIRTY = 1 << 1;

    private final String tag;
    private final List<Node> children = new ArrayList<>();
    private final Set<String> classes = new LinkedHashSet<>();
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private int stateBits;
    private int dirty = STYLE_DIRTY;
    private ComputedStyle computed;

    public Element(String tag, SourcePos pos) {
        super(pos);
        this.tag = tag;
    }

    public String tag() {
        return tag;
    }

    public List<Node> children() {
        return Collections.unmodifiableList(children);
    }

    /** Appends {@code child}, detaching it from its current parent first. */
    public void appendChild(Node child) {
        if (child instanceof Element e) {
            for (Element a = this; a != null; a = a.parent) {
                if (a == e) {
                    throw new IllegalArgumentException("<" + e.tag + "> cannot become its own descendant");
                }
            }
        }
        if (child.parent != null) {
            child.parent.removeChild(child);
        }
        children.add(child);
        child.parent = this;
        if (child instanceof Element e && e.dirty != 0) {
            markDescendantStyleDirty(this);
        }
    }

    public void removeChild(Node child) {
        if (child.parent != this) {
            throw new IllegalArgumentException("not a child of <" + tag + ">");
        }
        children.remove(child);
        child.parent = null;
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

    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public String attribute(String name) {
        return attributes.get(name);
    }

    public void setAttribute(String name, String value) {
        attributes.put(name, value);
    }

    public boolean hasState(int bit) {
        return (stateBits & bit) != 0;
    }

    public void setState(int bit, boolean on) {
        int next = on ? stateBits | bit : stateBits & ~bit;
        if (next != stateBits) {
            stateBits = next;
            markStyleDirty();
        }
    }

    public ComputedStyle computed() {
        return computed;
    }

    public void setComputed(ComputedStyle computed) {
        this.computed = computed;
    }

    public boolean isStyleDirty() {
        return (dirty & STYLE_DIRTY) != 0;
    }

    public boolean hasStyleDirtyDescendant() {
        return (dirty & DESCENDANT_STYLE_DIRTY) != 0;
    }

    /** Called by the style pass once this element and its subtree are up to date. */
    public void clearStyleDirty() {
        dirty = 0;
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
}
