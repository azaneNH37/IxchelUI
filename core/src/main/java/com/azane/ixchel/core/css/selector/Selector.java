package com.azane.ixchel.core.css.selector;

import com.azane.ixchel.core.dom.Element;

import java.util.List;

/**
 * A complex selector: {@code compounds} left to right, {@code combinators.get(i)} sits between
 * compound {@code i} and {@code i + 1}. Elements whose tag starts with {@code #} (the implicit
 * document root) never match.
 */
public record Selector(List<Compound> compounds, List<Combinator> combinators, int specificity) {

    public enum Combinator { DESCENDANT, CHILD }

    /** {@code [name]} when {@code value} is {@code null}, else {@code [name="value"]}; values compare case-sensitively. */
    public record AttributeMatch(String name, String value) {
        boolean matches(Element e) {
            String actual = e.attribute(name);
            return actual != null && (value == null || value.equals(actual));
        }
    }

    /**
     * {@code tag} and {@code id} are {@code null} when absent; {@code stateMask} uses {@link Element} state bits;
     * {@code root} is {@code :root}, a top-level element of the template (a child of the implicit root).
     */
    public record Compound(String tag, String id, List<String> classes, List<AttributeMatch> attributes, int stateMask,
                           boolean root) {
        public boolean matches(Element e) {
            if (e.tag().startsWith("#")) {
                return false;
            }
            if (root && (e.parent() == null || !e.parent().tag().startsWith("#"))) {
                return false;
            }
            if (tag != null && !tag.equals(e.tag())) {
                return false;
            }
            if (id != null && !id.equals(e.attribute("id"))) {
                return false;
            }
            for (int i = 0; i < classes.size(); i++) {
                if (!e.hasClass(classes.get(i))) {
                    return false;
                }
            }
            for (int i = 0; i < attributes.size(); i++) {
                if (!attributes.get(i).matches(e)) {
                    return false;
                }
            }
            for (int bit = 1; bit != 0 && bit <= stateMask; bit <<= 1) {
                if ((stateMask & bit) != 0 && !e.hasState(bit)) {
                    return false;
                }
            }
            return true;
        }
    }

    public Compound rightmost() {
        return compounds.get(compounds.size() - 1);
    }

    public boolean matches(Element e) {
        return matchAt(compounds.size() - 1, e);
    }

    private boolean matchAt(int i, Element e) {
        if (!compounds.get(i).matches(e)) {
            return false;
        }
        if (i == 0) {
            return true;
        }
        Element p = e.parent();
        if (combinators.get(i - 1) == Combinator.CHILD) {
            return p != null && matchAt(i - 1, p);
        }
        for (; p != null; p = p.parent()) {
            if (matchAt(i - 1, p)) {
                return true;
            }
        }
        return false;
    }

    /** Packed (ids, classes + attributes + pseudo-classes, tags), each saturating at 1023. */
    static int specificity(List<Compound> compounds) {
        int ids = 0, classes = 0, tags = 0;
        for (Compound c : compounds) {
            if (c.id != null) {
                ids++;
            }
            classes += c.classes.size() + c.attributes.size() + Integer.bitCount(c.stateMask) + (c.root ? 1 : 0);
            if (c.tag != null) {
                tags++;
            }
        }
        return Math.min(ids, 1023) << 20 | Math.min(classes, 1023) << 10 | Math.min(tags, 1023);
    }
}
