package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * The relative length units {@code em}, {@code rem}, {@code vw} and {@code vh}, which depend on the
 * element: a value with them is rewritten into pixels for each element before it is parsed, so the
 * property syntaxes only see {@code px}. {@code em} is the font size the value is relative to (the
 * parent's for {@code font-size}, else the element's own), {@code rem} that of the element's
 * top-level ancestor (the {@code :root} element).
 */
final class Units {
    float em;
    float rem;
    float vw;
    float vh;
    /** Set when a rewrite used {@code vw} or {@code vh}; the caller clears it. */
    boolean viewportUsed;

    Units(float em, float rem, float vw, float vh) {
        this.em = em;
        this.rem = rem;
        this.vw = vw;
        this.vh = vh;
    }

    /** Stand-in sizes for checking a declaration when no element is at hand. */
    static Units sample() {
        return new Units(9, 9, 100, 100);
    }

    static boolean contains(List<ComponentValue> values) {
        for (ComponentValue v : values) {
            if (v instanceof Token t ? basis(t) != null
                    : v instanceof ComponentValue.Function f ? contains(f.args())
                    : v instanceof ComponentValue.Block b && contains(b.contents())) {
                return true;
            }
        }
        return false;
    }

    /** {@code values} with each relative length in pixels; {@code values} itself when it has none. */
    List<ComponentValue> rewrite(List<ComponentValue> values) {
        if (!contains(values)) {
            return values;
        }
        List<ComponentValue> out = new ArrayList<>(values.size());
        for (ComponentValue v : values) {
            out.add(rewrite(v));
        }
        return out;
    }

    private ComponentValue rewrite(ComponentValue v) {
        if (v instanceof Token t) {
            String unit = basis(t);
            if (unit == null) {
                return t;
            }
            float b = switch (unit) {
                case "em" -> em;
                case "rem" -> rem;
                case "vw" -> vw / 100;
                default -> vh / 100;
            };
            if (unit.charAt(0) == 'v') {
                viewportUsed = true;
            }
            return new Token(Token.Kind.DIMENSION, "", t.number() * b, "px", t.pos());
        }
        if (v instanceof ComponentValue.Function f) {
            return new ComponentValue.Function(f.name(), rewrite(f.args()), f.pos());
        }
        if (v instanceof ComponentValue.Block b) {
            return new ComponentValue.Block(b.open(), rewrite(b.contents()), b.pos());
        }
        return v;
    }

    /** The relative unit of {@code t}, lower-cased; {@code null} when it has none. */
    private static String basis(Token t) {
        if (!t.is(Token.Kind.DIMENSION)) {
            return null;
        }
        String u = t.unit();
        for (String r : RELATIVE) {
            if (r.equalsIgnoreCase(u)) {
                return r;
            }
        }
        return null;
    }

    private static final String[] RELATIVE = {"em", "rem", "vw", "vh"};
}
