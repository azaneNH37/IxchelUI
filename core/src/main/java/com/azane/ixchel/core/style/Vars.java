package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** {@code var(--name)} and {@code var(--name, fallback)} anywhere in a value, functions and blocks included. */
final class Vars {
    private Vars() {
    }

    static boolean contains(List<ComponentValue> values) {
        for (ComponentValue v : values) {
            if (v instanceof ComponentValue.Function f && (isVar(f) || contains(f.args()))) {
                return true;
            }
            if (v instanceof ComponentValue.Block b && contains(b.contents())) {
                return true;
            }
        }
        return false;
    }

    /** Whether every {@code var()} in {@code values} is well formed; reports each one that is not. */
    static boolean check(List<ComponentValue> values, Diagnostics diag) {
        boolean ok = true;
        for (ComponentValue v : values) {
            if (v instanceof ComponentValue.Function f) {
                if (isVar(f)) {
                    Call c = call(f);
                    if (c == null) {
                        diag.error(f.pos(), "expected var(--name) or var(--name, fallback)");
                        ok = false;
                    } else if (c.fallback != null) {
                        ok &= check(c.fallback, diag);
                    }
                } else {
                    ok &= check(f.args(), diag);
                }
            } else if (v instanceof ComponentValue.Block b) {
                ok &= check(b.contents(), diag);
            }
        }
        return ok;
    }

    /**
     * {@code values} with each {@code var()} replaced by the value {@code lookup} gives its name, or
     * else its fallback; {@code null} if a name has neither. {@code values} itself when it has no
     * {@code var()}. Every {@code var()} must be well formed ({@link #check}).
     */
    static List<ComponentValue> substitute(List<ComponentValue> values, Function<String, List<ComponentValue>> lookup) {
        if (!contains(values)) {
            return values;
        }
        List<ComponentValue> out = new ArrayList<>(values.size());
        for (ComponentValue v : values) {
            if (v instanceof ComponentValue.Function f) {
                if (isVar(f)) {
                    Call c = call(f);
                    List<ComponentValue> value = lookup.apply(c.name);
                    if (value == null) {
                        value = c.fallback == null ? null : substitute(c.fallback, lookup);
                    }
                    if (value == null) {
                        return null;
                    }
                    out.addAll(value);
                } else {
                    List<ComponentValue> args = substitute(f.args(), lookup);
                    if (args == null) {
                        return null;
                    }
                    out.add(args == f.args() ? f : new ComponentValue.Function(f.name(), args, f.pos()));
                }
            } else if (v instanceof ComponentValue.Block b) {
                List<ComponentValue> contents = substitute(b.contents(), lookup);
                if (contents == null) {
                    return null;
                }
                out.add(contents == b.contents() ? b : new ComponentValue.Block(b.open(), contents, b.pos()));
            } else {
                out.add(v);
            }
        }
        return out;
    }

    /** {@code fallback} is {@code null} without a comma, possibly empty after one. */
    private record Call(String name, List<ComponentValue> fallback) {
    }

    private static boolean isVar(ComponentValue.Function f) {
        return f.name().equalsIgnoreCase("var");
    }

    private static Call call(ComponentValue.Function f) {
        List<ComponentValue> args = f.args();
        int i = skipWs(args, 0);
        if (i >= args.size() || !(args.get(i) instanceof Token name && name.is(Token.Kind.IDENT)
                && name.value().startsWith("--"))) {
            return null;
        }
        i = skipWs(args, i + 1);
        if (i == args.size()) {
            return new Call(name.value(), null);
        }
        if (!(args.get(i) instanceof Token comma && comma.is(Token.Kind.COMMA))) {
            return null;
        }
        int from = skipWs(args, i + 1);
        int to = args.size();
        while (to > from && args.get(to - 1) instanceof Token t && t.is(Token.Kind.WHITESPACE)) {
            to--;
        }
        return new Call(name.value(), args.subList(from, to));
    }

    private static int skipWs(List<ComponentValue> values, int i) {
        while (i < values.size() && values.get(i) instanceof Token t && t.is(Token.Kind.WHITESPACE)) {
            i++;
        }
        return i;
    }
}
