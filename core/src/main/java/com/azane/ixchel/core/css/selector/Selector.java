package com.azane.ixchel.core.css.selector;

import com.azane.ixchel.core.dom.Element;

import java.util.List;
import java.util.Locale;

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
     * {@code root} is {@code :root}, a top-level element of the template (a child of the implicit root);
     * {@code langs} are the lowercase ranges of {@code :lang()}, empty when absent.
     */
    public record Compound(String tag, String id, List<String> classes, List<AttributeMatch> attributes, int stateMask,
                           boolean root, List<String> langs) {
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
            return langs.isEmpty() || matchesLang(e);
        }

        /**
         * The element's language is the {@code lang} of it or its nearest ancestor (the document root
         * carries the game's); a range matches it exactly or as a prefix ending at a {@code -}, ignoring case.
         */
        private boolean matchesLang(Element e) {
            String lang = null;
            for (Element a = e; a != null && lang == null; a = a.parent()) {
                lang = a.attribute("lang");
            }
            if (lang == null) {
                return false;
            }
            lang = lang.toLowerCase(Locale.ROOT);
            for (int i = 0; i < langs.size(); i++) {
                String r = langs.get(i);
                if (lang.equals(r) || lang.startsWith(r) && lang.charAt(r.length()) == '-') {
                    return true;
                }
            }
            return false;
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
            classes += c.classes.size() + c.attributes.size() + Integer.bitCount(c.stateMask) + (c.root ? 1 : 0)
                    + (c.langs.isEmpty() ? 0 : 1);
            if (c.tag != null) {
                tags++;
            }
        }
        return Math.min(ids, 1023) << 20 | Math.min(classes, 1023) << 10 | Math.min(tags, 1023);
    }
}
