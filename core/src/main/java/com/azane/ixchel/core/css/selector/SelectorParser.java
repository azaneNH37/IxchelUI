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
 * Supported: type, {@code *}, {@code .class}, {@code #id}, {@code [attr]}, {@code [attr=value]},
 * {@code :hover/:active/:focus/:disabled/:checked/:open}, {@code :-ix-absent/:-ix-entering/:-ix-exiting}
 * (see {@code Presence}), {@code :root}, {@code :lang(range, …)},
 * {@code :not()}, {@code :is()} and {@code :where()} of selector lists (combinators allowed),
 * {@code ::before}, {@code ::after} and {@code ::part(name)} at the end of a selector, the last
 * followed by any of the state pseudo-classes, descendant and child combinators, selector lists.
 * Anything else is a dev-time error.
 */
public final class SelectorParser {
    private final List<ComponentValue> in;
    private final SourcePos rulePos;
    private final Diagnostics diag;
    /** Inside a logical pseudo-class, where pseudo-elements are not allowed. */
    private final boolean nested;
    private int i;
    /** Set by {@link #compound} on reading a pseudo-element, which must end the selector. */
    private Selector.PseudoElement pseudo = Selector.PseudoElement.NONE;
    /** The name in {@code ::part()} and the state pseudo-classes after it. */
    private String part;
    private int partStates;

    private SelectorParser(List<ComponentValue> in, SourcePos rulePos, Diagnostics diag, boolean nested) {
        this.in = in;
        this.rulePos = rulePos;
        this.diag = diag;
        this.nested = nested;
    }

    /**
     * Parses a rule prelude; an invalid list yields an empty result (the whole rule is dropped, as in
     * CSS). {@code rulePos} locates errors when the prelude is empty.
     */
    public static List<Selector> parseList(List<ComponentValue> prelude, SourcePos rulePos, Diagnostics diag) {
        return parse(prelude, rulePos, diag, false);
    }

    private static List<Selector> parse(List<ComponentValue> prelude, SourcePos rulePos, Diagnostics diag, boolean nested) {
        SelectorParser p = new SelectorParser(prelude, rulePos, diag, nested);
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
        pseudo = Selector.PseudoElement.NONE;
        part = null;
        partStates = 0;
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
            if (pseudo != Selector.PseudoElement.NONE) {
                diag.report(SelectorCode.MISPLACED_PSEUDO_ELEMENT, v.pos(), "a pseudo-element must end the selector");
                return null;
            }
            if (v instanceof Token t && t.isDelim('>')) {
                i++;
                skipWs();
                combinators.add(Selector.Combinator.CHILD);
            } else if (v instanceof Token t && (t.isDelim('+') || t.isDelim('~'))) {
                diag.report(SelectorCode.UNSUPPORTED_COMBINATOR, t.pos(), "unsupported combinator '" + t.value() + "'");
                return null;
            } else if (ws) {
                combinators.add(Selector.Combinator.DESCENDANT);
            } else {
                diag.report(SelectorCode.UNEXPECTED_TOKEN, v.pos(), "unexpected token in selector");
                return null;
            }
        }
        return new Selector(List.copyOf(compounds), List.copyOf(combinators), pseudo, part, partStates,
                Selector.specificity(compounds, pseudo, partStates));
    }

    private Selector.Compound compound() {
        String tag = null, id = null;
        List<String> classes = new ArrayList<>();
        List<Selector.AttributeMatch> attributes = new ArrayList<>();
        List<Selector.LogicalMatch> logical = new ArrayList<>();
        int state = 0;
        boolean root = false;
        List<String> langs = List.of();
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
            if (pseudo != Selector.PseudoElement.NONE && !(v instanceof Token w && w.is(Token.Kind.WHITESPACE))
                    && !(v instanceof Token c && c.is(Token.Kind.COMMA))) {
                int bit = pseudo == Selector.PseudoElement.PART && v instanceof Token colon && colon.is(Token.Kind.COLON)
                        && i + 1 < in.size() && in.get(i + 1) instanceof Token n && n.is(Token.Kind.IDENT) ? stateBit(n.value()) : 0;
                if (bit == 0) {
                    diag.report(SelectorCode.MISPLACED_PSEUDO_ELEMENT, v.pos(), pseudo == Selector.PseudoElement.PART
                            ? "only state pseudo-classes such as :hover may follow ::part()" : "a pseudo-element must end the selector");
                    return null;
                }
                partStates |= bit;
                i += 2;
                continue;
            }
            if (v instanceof ComponentValue.Block b && b.open() == Token.Kind.LBRACKET) {
                Selector.AttributeMatch m = attribute(b);
                if (m == null) {
                    return null;
                }
                attributes.add(m);
                i++;
                continue;
            }
            if (!(v instanceof Token t)) {
                diag.report(SelectorCode.UNEXPECTED_TOKEN, v.pos(),
                        "unexpected " + (v instanceof ComponentValue.Block ? "block" : "function") + " in selector");
                return null;
            }
            if (t.is(Token.Kind.HASH_ID)) {
                if (id != null && !id.equals(t.value())) {
                    diag.report(SelectorCode.CONFLICTING_IDS, t.pos(), "selector can never match: two different ids");
                    return null;
                }
                id = t.value();
                i++;
            } else if (t.is(Token.Kind.HASH)) {
                diag.report(SelectorCode.INVALID_ID, t.pos(), "invalid id selector #" + t.value());
                return null;
            } else if (t.isDelim('.')) {
                Token name = next(1);
                if (name == null || !name.is(Token.Kind.IDENT)) {
                    diag.report(SelectorCode.MISSING_CLASS_NAME, t.pos(), "expected a class name after '.'");
                    return null;
                }
                classes.add(name.value());
                i += 2;
            } else if (t.is(Token.Kind.COLON)) {
                ComponentValue after = i + 1 < in.size() ? in.get(i + 1) : null;
                if (after instanceof Token c && c.is(Token.Kind.COLON)) {
                    if (!pseudoElement(t)) {
                        return null;
                    }
                    continue;
                }
                if (after instanceof Token n && n.isIdent("root")) {
                    root = true;
                    i += 2;
                    continue;
                }
                if (after instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("lang")) {
                    if (!langs.isEmpty()) {
                        diag.report(SelectorCode.INVALID_LANG, f.pos(), "a compound selector takes one :lang(); list the ranges in it");
                        return null;
                    }
                    langs = langRanges(f);
                    if (langs == null) {
                        return null;
                    }
                    i += 2;
                    continue;
                }
                Selector.Logical kind = after instanceof ComponentValue.Function f ? logicalKind(f.name()) : null;
                if (kind != null) {
                    ComponentValue.Function f = (ComponentValue.Function) after;
                    Selector.LogicalMatch m = logical(kind, f);
                    if (m == null) {
                        return null;
                    }
                    logical.add(m);
                    i += 2;
                    continue;
                }
                int bit = after instanceof Token n && n.is(Token.Kind.IDENT) ? stateBit(n.value()) : 0;
                if (bit == 0) {
                    reportPseudo(after, after != null ? after.pos() : t.pos());
                    return null;
                }
                state |= bit;
                i += 2;
            } else {
                break;
            }
        }
        if (i == start) {
            diag.report(SelectorCode.EXPECTED_SELECTOR, i < in.size() ? in.get(i).pos() : endPos(), "expected a selector");
            return null;
        }
        return new Selector.Compound(tag, id, List.copyOf(classes), List.copyOf(attributes), state, root, langs,
                List.copyOf(logical));
    }

    /** Reads {@code ::before}, {@code ::after} or {@code ::part(name)} at {@code colon}; false once reported. */
    private boolean pseudoElement(Token colon) {
        ComponentValue name = i + 2 < in.size() ? in.get(i + 2) : null;
        Selector.PseudoElement p;
        String partName = null;
        if (name instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("part")) {
            partName = partName(f);
            if (partName == null) {
                return false;
            }
            p = Selector.PseudoElement.PART;
        } else if (name instanceof Token n && n.is(Token.Kind.IDENT)) {
            p = switch (n.value().toLowerCase(Locale.ROOT)) {
                case "before" -> Selector.PseudoElement.BEFORE;
                case "after" -> Selector.PseudoElement.AFTER;
                default -> null;
            };
            if (p == null) {
                diag.report(SelectorCode.UNSUPPORTED_PSEUDO, n.pos(), "unsupported pseudo-element ::" + n.value(),
                        "only ::before, ::after and ::part() are supported");
                return false;
            }
        } else {
            diag.report(SelectorCode.MISSING_PSEUDO_NAME, colon.pos(), "expected a pseudo-element name after '::'");
            return false;
        }
        if (nested) {
            diag.report(SelectorCode.MISPLACED_PSEUDO_ELEMENT, colon.pos(), "a pseudo-element cannot appear inside :not(), :is() or :where()");
            return false;
        }
        pseudo = p;
        part = partName;
        i += 3;
        return true;
    }

    /** The one identifier in {@code ::part()}; {@code null} once reported. */
    private String partName(ComponentValue.Function f) {
        String out = null;
        for (ComponentValue v : f.args()) {
            if (v instanceof Token t && t.is(Token.Kind.WHITESPACE)) {
                continue;
            }
            if (out != null || !(v instanceof Token t && t.is(Token.Kind.IDENT))) {
                out = null;
                break;
            }
            out = t.value();
        }
        if (out == null) {
            diag.report(SelectorCode.INVALID_PART, f.pos(), "::part() takes one part name, such as ::part(hotbar)");
        }
        return out;
    }

    private static Selector.Logical logicalKind(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "not" -> Selector.Logical.NOT;
            case "is" -> Selector.Logical.IS;
            case "where" -> Selector.Logical.WHERE;
            default -> null;
        };
    }

    /** The selector list inside a logical pseudo-class; {@code null} once reported. */
    private Selector.LogicalMatch logical(Selector.Logical kind, ComponentValue.Function f) {
        boolean empty = true;
        for (ComponentValue v : f.args()) {
            if (!(v instanceof Token t && t.is(Token.Kind.WHITESPACE))) {
                empty = false;
                break;
            }
        }
        if (empty) {
            diag.report(SelectorCode.INVALID_LOGICAL, f.pos(), ":" + f.name() + "() needs a selector list");
            return null;
        }
        int errors = diag.errorCount();
        List<Selector> list = parse(f.args(), f.pos(), diag, true);
        return diag.errorCount() != errors ? null : new Selector.LogicalMatch(kind, list);
    }

    /** The comma-separated identifiers or strings of {@code :lang()}, lowercase; {@code null} once reported. */
    private List<String> langRanges(ComponentValue.Function f) {
        List<String> out = new ArrayList<>();
        boolean expectRange = true;
        for (ComponentValue v : f.args()) {
            if (v instanceof Token t && t.is(Token.Kind.WHITESPACE)) {
                continue;
            }
            if (expectRange && v instanceof Token t && (t.is(Token.Kind.IDENT) || t.is(Token.Kind.STRING)) && !t.value().isEmpty()) {
                out.add(t.value().toLowerCase(Locale.ROOT));
                expectRange = false;
            } else if (!expectRange && v instanceof Token t && t.is(Token.Kind.COMMA)) {
                expectRange = true;
            } else {
                expectRange = true;
                break;
            }
        }
        if (out.isEmpty() || expectRange) {
            diag.report(SelectorCode.INVALID_LANG, f.pos(), "expected :lang() with language ranges such as zh or en-us, separated by commas");
            return null;
        }
        return List.copyOf(out);
    }

    /** {@code [name]} or {@code [name=value]} with an identifier or string value; other operators are errors. */
    private Selector.AttributeMatch attribute(ComponentValue.Block b) {
        List<ComponentValue> c = new ArrayList<>();
        for (ComponentValue v : b.contents()) {
            if (!(v instanceof Token t && t.is(Token.Kind.WHITESPACE))) {
                c.add(v);
            }
        }
        if (c.isEmpty() || !(c.get(0) instanceof Token name && name.is(Token.Kind.IDENT))) {
            diag.report(SelectorCode.INVALID_ATTRIBUTE, b.pos(), "expected an attribute name in [...]");
            return null;
        }
        String attr = name.value().toLowerCase(Locale.ROOT);
        if (c.size() == 1) {
            return new Selector.AttributeMatch(attr, null);
        }
        if (!(c.get(1) instanceof Token op && op.isDelim('='))) {
            diag.report(SelectorCode.UNSUPPORTED_ATTRIBUTE_OPERATOR, c.get(1).pos(), "unsupported attribute selector operator",
                    "only [name] and [name=value] are supported");
            return null;
        }
        if (c.size() != 3 || !(c.get(2) instanceof Token value && (value.is(Token.Kind.IDENT) || value.is(Token.Kind.STRING)))) {
            diag.report(SelectorCode.INVALID_ATTRIBUTE, b.pos(), "expected [" + attr + "=value] with an identifier or a quoted string");
            return null;
        }
        return new Selector.AttributeMatch(attr, value.value());
    }

    private static int stateBit(String name) {
        switch (name.toLowerCase(Locale.ROOT)) {
            case "hover":
                return Element.HOVER;
            case "active":
                return Element.ACTIVE;
            case "focus":
                return Element.FOCUS;
            case "disabled":
                return Element.DISABLED;
            case "checked":
                return Element.CHECKED;
            case "open":
                return Element.OPEN;
            case "-ix-absent":
                return Element.ABSENT;
            case "-ix-entering":
                return Element.ENTERING;
            case "-ix-exiting":
                return Element.EXITING;
            default:
                return 0;
        }
    }

    private void reportPseudo(ComponentValue after, SourcePos at) {
        if (after instanceof Token n && n.is(Token.Kind.IDENT)
                && (n.isIdent("before") || n.isIdent("after"))) {
            diag.report(SelectorCode.UNSUPPORTED_PSEUDO, at, "unsupported pseudo-class :" + n.value(),
                    "write the pseudo-element with two colons: ::" + n.value().toLowerCase(Locale.ROOT));
        } else if (after instanceof Token n && n.is(Token.Kind.IDENT)) {
            diag.report(SelectorCode.UNSUPPORTED_PSEUDO, at, "unsupported pseudo-class :" + n.value());
        } else if (after instanceof ComponentValue.Function f) {
            diag.report(SelectorCode.UNSUPPORTED_PSEUDO, at, "unsupported pseudo-class :" + f.name() + "()");
        } else {
            diag.report(SelectorCode.MISSING_PSEUDO_NAME, at, "expected a pseudo-class name after ':'");
        }
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
