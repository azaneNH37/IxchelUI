package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.css.syntax.Rule;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses an {@code .ixeffect} file. Its header is the {@code kind} and {@code param} statements at
 * the top, between which whitespace and GLSL comments may stand:
 *
 * <pre>
 * kind box;
 * param speed { syntax: &lt;number&gt;; inherits: false; initial-value: 1; }
 * </pre>
 *
 * The first other word starts the GLSL body, which is kept as written. A param block takes
 * {@code syntax} ({@code <number>}, {@code <length>}, {@code <percentage>}, {@code <color>},
 * {@code <angle>} or {@code <time>}, bare or quoted as in CSS {@code @property}), {@code inherits}
 * ({@code true} or {@code false}) and {@code initial-value}, all required; a length's initial value is
 * in px.
 */
public final class EffectParser {
    private final String src;
    private final String file;
    private final Diagnostics diag;
    private int i;
    private int line = 1;
    private int col = 1;

    private EffectParser(String src, String file, Diagnostics diag) {
        this.src = src;
        this.file = file;
        this.diag = diag;
    }

    /** The effect, or {@code null} after reporting why it cannot be used. */
    public static Effect parse(String text, String id, Diagnostics diag) {
        String name = nameOf(id);
        if (!validName(name)) {
            diag.report(EffectCode.INVALID_NAME, new SourcePos(id, 1, 1), "effect file name '" + name
                    + "' must be lower-case letters, digits and '-', starting with a letter");
            return null;
        }
        return new EffectParser(text, id, diag).parse(id, name);
    }

    /** The name of the effect in file {@code id}: its last path segment without {@value Effect#EXTENSION}. */
    public static String nameOf(String id) {
        String n = id.substring(Math.max(id.lastIndexOf('/'), id.lastIndexOf(':')) + 1);
        return n.endsWith(Effect.EXTENSION) ? n.substring(0, n.length() - Effect.EXTENSION.length()) : n;
    }

    private Effect parse(String id, String name) {
        Effect.Kind kind = null;
        boolean kindSeen = false;
        boolean bad = false;
        List<Effect.Param> params = new ArrayList<>();
        Set<String> names = new HashSet<>();
        while (true) {
            skipTrivia();
            int start = i;
            SourcePos at = pos();
            String w = peekWord();
            if (w.equals("kind")) {
                advance(w.length());
                skipTrivia();
                String k = word();
                skipTrivia();
                if (k.isEmpty() || !consume(';')) {
                    diag.report(EffectCode.INVALID_HEADER, at, "expected 'kind box;' or 'kind layer;'");
                    return null;
                }
                if (kindSeen) {
                    diag.report(EffectCode.INVALID_HEADER, at, "the kind is declared twice");
                    bad = true;
                }
                kindSeen = true;
                switch (k) {
                    case "box" -> kind = Effect.Kind.BOX;
                    case "layer" -> kind = Effect.Kind.LAYER;
                    case "backdrop" -> {
                        diag.report(EffectCode.UNSUPPORTED_KIND, at, "backdrop effects are not supported yet; use box or layer");
                        bad = true;
                    }
                    default -> {
                        diag.report(EffectCode.UNSUPPORTED_KIND, at, "unknown kind '" + k + "'; use box or layer");
                        bad = true;
                    }
                }
            } else if (w.equals("param")) {
                advance(w.length());
                skipTrivia();
                SourcePos namePos = pos();
                String p = word();
                skipTrivia();
                if (p.isEmpty() || !consume('{')) {
                    diag.report(EffectCode.INVALID_HEADER, at, "expected 'param <name> { syntax: …; inherits: …; initial-value: …; }'");
                    return null;
                }
                SourcePos blockPos = pos();
                int close = src.indexOf('}', i);
                if (close < 0) {
                    diag.report(EffectCode.INVALID_HEADER, at, "the block of param '" + p + "' is never closed");
                    return null;
                }
                String block = src.substring(i, close);
                advance(close + 1 - i);
                Effect.Param param = param(p, namePos, block, blockPos);
                if (param == null) {
                    bad = true;
                } else if (!names.add(param.name())) {
                    diag.report(EffectCode.DUPLICATE_PARAM, namePos, "param '" + p + "' is declared twice");
                    bad = true;
                } else {
                    params.add(param);
                }
            } else {
                if (!kindSeen) {
                    diag.report(EffectCode.MISSING_KIND, at, "an effect file starts with its kind: 'kind box;' or 'kind layer;'");
                    return null;
                }
                return bad ? null : new Effect(id, name, kind, params, src.substring(start), at.line(), src);
            }
        }
    }

    private Effect.Param param(String name, SourcePos namePos, String block, SourcePos blockPos) {
        if (!validName(name)) {
            diag.report(EffectCode.INVALID_NAME, namePos, "param name '" + name
                    + "' must be lower-case letters, digits and '-', starting with a letter");
            return null;
        }
        ParamType type = null;
        Boolean inherits = null;
        Rule.Declaration initial = null;
        boolean bad = false;
        for (Rule.Declaration d : CssParser.parseDeclarations(block, blockPos, diag)) {
            List<ComponentValue> v = significant(d.value());
            switch (d.name().toLowerCase(Locale.ROOT)) {
                case "syntax" -> {
                    String s = syntaxName(v);
                    type = s == null ? null : ParamType.of(s);
                    if (type == null) {
                        diag.report(EffectCode.UNSUPPORTED_SYNTAX, d.pos(), "unsupported syntax"
                                + (s == null ? "" : " '" + s + "'") + "; use <number>, <length>, <percentage>, <color>, <angle> or <time>");
                        bad = true;
                    }
                }
                case "inherits" -> {
                    if (v.size() == 1 && v.get(0) instanceof Token t && (t.isIdent("true") || t.isIdent("false"))) {
                        inherits = t.isIdent("true");
                    } else {
                        diag.report(EffectCode.INVALID_HEADER, d.pos(), "inherits takes true or false");
                        bad = true;
                    }
                }
                case "initial-value" -> initial = d;
                default -> {
                    diag.report(EffectCode.INVALID_HEADER, d.pos(), "unknown param descriptor '" + d.name()
                            + "'; a param takes syntax, inherits and initial-value");
                    bad = true;
                }
            }
        }
        if (bad) {
            return null;
        }
        if (type == null || inherits == null || initial == null) {
            List<String> missing = new ArrayList<>(3);
            if (type == null) missing.add("syntax");
            if (inherits == null) missing.add("inherits");
            if (initial == null) missing.add("initial-value");
            diag.report(EffectCode.MISSING_DESCRIPTOR, namePos, "param '" + name + "' lacks " + String.join(", ", missing));
            return null;
        }
        Object value = type.syntax().parse(initial.value(), initial.pos(), diag);
        return value == null ? null : new Effect.Param(name, type, inherits, value, namePos);
    }

    /** {@code <name>} written bare or as a string; {@code null} when it is neither. */
    private static String syntaxName(List<ComponentValue> v) {
        if (v.size() == 1 && v.get(0) instanceof Token t && t.is(Token.Kind.STRING)) {
            return t.value().trim();
        }
        if (v.size() == 3 && v.get(0) instanceof Token a && a.isDelim('<') && v.get(1) instanceof Token n
                && n.is(Token.Kind.IDENT) && v.get(2) instanceof Token b && b.isDelim('>')) {
            return "<" + n.value() + ">";
        }
        return null;
    }

    private static List<ComponentValue> significant(List<ComponentValue> value) {
        List<ComponentValue> out = new ArrayList<>(value.size());
        for (ComponentValue c : value) {
            if (!(c instanceof Token t && t.is(Token.Kind.WHITESPACE))) {
                out.add(c);
            }
        }
        return out;
    }

    static boolean validName(String s) {
        if (s.isEmpty() || s.charAt(0) < 'a' || s.charAt(0) > 'z') {
            return false;
        }
        for (int k = 1; k < s.length(); k++) {
            char c = s.charAt(k);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    /** Skips whitespace and {@code //} and {@code /* *\/} comments. */
    private void skipTrivia() {
        while (i < src.length()) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                advance(1);
            } else if (src.startsWith("//", i)) {
                int end = src.indexOf('\n', i);
                advance((end < 0 ? src.length() : end) - i);
            } else if (src.startsWith("/*", i)) {
                int end = src.indexOf("*/", i + 2);
                advance((end < 0 ? src.length() : end + 2) - i);
            } else {
                return;
            }
        }
    }

    private String peekWord() {
        int end = i;
        while (end < src.length() && wordChar(src.charAt(end))) {
            end++;
        }
        return src.substring(i, end);
    }

    private String word() {
        String w = peekWord();
        advance(w.length());
        return w;
    }

    private static boolean wordChar(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_';
    }

    private boolean consume(char c) {
        if (i < src.length() && src.charAt(i) == c) {
            advance(1);
            return true;
        }
        return false;
    }

    private void advance(int n) {
        for (int end = i + n; i < end; i++) {
            if (src.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
    }

    private SourcePos pos() {
        return new SourcePos(file, line, col);
    }
}
