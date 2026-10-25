package com.azane.ixchel.core.css.selector;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Supported: type, {@code *}, {@code .class}, {@code #id}, {@code :hover/:active/:focus}, descendant
 * and child combinators, selector lists. Anything else is a dev-time error.
 */
public final class SelectorParser {
    private final List<ComponentValue> in;
    private final SourcePos rulePos;
    private final Diagnostics diag;
    private int i;

    private SelectorParser(List<ComponentValue> in, SourcePos rulePos, Diagnostics diag) {
        this.in = in;
        this.rulePos = rulePos;
        this.diag = diag;
    }

    /**
     * Parses a rule prelude; an invalid list yields an empty result (the whole rule is dropped, as in
     * CSS). {@code rulePos} locates errors when the prelude is empty.
     */
    public static List<Selector> parseList(List<ComponentValue> prelude, SourcePos rulePos, Diagnostics diag) {
        SelectorParser p = new SelectorParser(prelude, rulePos, diag);
        List<Selector> out = new ArrayList<>();
        int errors = diag.errorCount();
        do {
            Selector s = p.complex();
            if (s != null) {
                out.add(s);
            }
        } while (p.eatComma());
        return diag.errorCount() == errors ? out : List.of();
    }

    private boolean eatComma() {
        if (i < in.size() && in.get(i) instanceof Token t && t.is(Token.Kind.COMMA)) {
            i++;
            return true;
        }
        return false;
    }

    private Selector complex() {
        skipWs();
        List<Selector.Compound> compounds = new ArrayList<>();
        List<Selector.Combinator> combinators = new ArrayList<>();
        while (true) {
            Selector.Compound c = compound();
            if (c == null) {
                return null;
            }
            compounds.add(c);
            boolean ws = skipWs();
            if (i >= in.size() || in.get(i) instanceof Token t && t.is(Token.Kind.COMMA)) {
                break;
            }
            ComponentValue v = in.get(i);
            if (v instanceof Token t && t.isDelim('>')) {
                i++;
                skipWs();
                combinators.add(Selector.Combinator.CHILD);
            } else if (v instanceof Token t && (t.isDelim('+') || t.isDelim('~'))) {
                diag.error(t.pos(), "unsupported combinator '" + t.value() + "'");
                return null;
            } else if (ws) {
                combinators.add(Selector.Combinator.DESCENDANT);
            } else {
                diag.error(v.pos(), "unexpected token in selector");
                return null;
            }
        }
        return new Selector(List.copyOf(compounds), List.copyOf(combinators), Selector.specificity(compounds));
    }

    private Selector.Compound compound() {
        String tag = null, id = null;
        List<String> classes = new ArrayList<>();
        int state = 0;
        int start = i;
        if (i < in.size() && in.get(i) instanceof Token t) {
            if (t.is(Token.Kind.IDENT)) {
                tag = t.value().toLowerCase(Locale.ROOT);
                i++;
            } else if (t.isDelim('*')) {
                i++;
            }
        }
        while (i < in.size()) {
            ComponentValue v = in.get(i);
            if (!(v instanceof Token t)) {
                diag.error(v.pos(), v instanceof ComponentValue.Block
                        ? "attribute selectors are not supported" : "unexpected function in selector");
                return null;
            }
            if (t.is(Token.Kind.HASH_ID)) {
                if (id != null && !id.equals(t.value())) {
                    diag.error(t.pos(), "selector can never match: two different ids");
                    return null;
                }
                id = t.value();
                i++;
            } else if (t.is(Token.Kind.HASH)) {
                diag.error(t.pos(), "invalid id selector #" + t.value());
                return null;
            } else if (t.isDelim('.')) {
                Token name = next(1);
                if (name == null || !name.is(Token.Kind.IDENT)) {
                    diag.error(t.pos(), "expected a class name after '.'");
                    return null;
                }
                classes.add(name.value());
                i += 2;
            } else if (t.is(Token.Kind.COLON)) {
                ComponentValue after = i + 1 < in.size() ? in.get(i + 1) : null;
                int bit = after instanceof Token n && n.is(Token.Kind.IDENT) ? stateBit(n.value()) : 0;
                if (bit == 0) {
                    diag.error(after != null ? after.pos() : t.pos(), describePseudo(after));
                    return null;
                }
                state |= bit;
                i += 2;
            } else {
                break;
            }
        }
        if (i == start) {
            diag.error(i < in.size() ? in.get(i).pos() : endPos(), "expected a selector");
            return null;
        }
        return new Selector.Compound(tag, id, List.copyOf(classes), state);
    }

    private static int stateBit(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "hover":
                return Element.HOVER;
            case "active":
                return Element.ACTIVE;
            case "focus":
                return Element.FOCUS;
            default:
                return 0;
        }
    }

    private static String describePseudo(ComponentValue after) {
        if (after instanceof Token n && n.is(Token.Kind.COLON)) {
            return "pseudo-elements are not supported";
        }
        if (after instanceof Token n && n.is(Token.Kind.IDENT)) {
            return "unsupported pseudo-class :" + n.value();
        }
        if (after instanceof ComponentValue.Function f) {
            return "unsupported pseudo-class :" + f.name() + "()";
        }
        return "expected a pseudo-class name after ':'";
    }

    private Token next(int offset) {
        int j = i + offset;
        return j < in.size() && in.get(j) instanceof Token t ? t : null;
    }

    private SourcePos endPos() {
        return in.isEmpty() ? rulePos : in.get(in.size() - 1).pos();
    }

    private boolean skipWs() {
        boolean any = false;
        while (i < in.size() && in.get(i) instanceof Token t && t.is(Token.Kind.WHITESPACE)) {
            i++;
            any = true;
        }
        return any;
    }
}
