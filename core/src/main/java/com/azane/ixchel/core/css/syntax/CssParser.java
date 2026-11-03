package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSS Syntax Level 3 §5 parser restricted to what IxchelUI supports: qualified rules whose blocks hold
 * declarations and nested rules (CSS Nesting), and {@code @keyframes}. Other at-rules are reported as
 * errors and skipped.
 *
 * <p>Nested rules are expanded here into plain rules, each after the rule it is nested in:
 *
 * <pre>{@code
 * .row { color: red; &:hover { … } &.on { … } .name { … } > .icon { … } }
 * // .row { color: red }  .row:hover { … }  .row.on { … }  .row .name { … }  .row > .icon { … }
 * }</pre>
 *
 * {@code &} is replaced by the parent selector, once per selector of a parent list, rather than
 * standing for {@code :is(parent)}; so each expanded selector has its own specificity, not the
 * highest of the parent list's.
 */
public final class CssParser {
    private final List<Token> tokens;
    private final Diagnostics diag;
    private final List<ParsedSheet.Keyframes> keyframes = new ArrayList<>();
    private int i;
    private boolean warnedUnclosed;

    private CssParser(List<Token> tokens, Diagnostics diag) {
        this.tokens = tokens;
        this.diag = diag;
    }

    public static ParsedSheet parseStylesheet(String source, String file, Diagnostics diag) {
        return parseStylesheet(source, new SourcePos(file, 1, 1), diag);
    }

    /** {@code start} is where an inline {@code <style>} body begins. */
    public static ParsedSheet parseStylesheet(String source, SourcePos start, Diagnostics diag) {
        CssParser p = new CssParser(CssTokenizer.tokenize(source, start, diag), diag);
        List<Rule> rules = p.ruleList();
        return new ParsedSheet(List.copyOf(rules), List.copyOf(p.keyframes));
    }

    /** For a `style="..."` attribute; positions are offset so line 1 col 1 of `source` maps to `start`. */
    public static List<Rule.Declaration> parseDeclarations(String source, SourcePos start, Diagnostics diag) {
        CssParser p = new CssParser(CssTokenizer.tokenize(source, start, diag), diag);
        List<ComponentValue> values = new ArrayList<>();
        while (!p.peek().is(Token.Kind.EOF)) {
            values.add(p.componentValue());
        }
        return p.declarationList(values, null, null);
    }

    /**
     * A lone declaration value, such as a {@code bind:style:*} value from code: its component values
     * with leading and trailing whitespace removed. Positions are offset as in {@link #parseDeclarations}.
     */
    public static List<ComponentValue> parseValue(String source, SourcePos start, Diagnostics diag) {
        CssParser p = new CssParser(CssTokenizer.tokenize(source, start, diag), diag);
        List<ComponentValue> values = new ArrayList<>();
        while (!p.peek().is(Token.Kind.EOF)) {
            values.add(p.componentValue());
        }
        return List.copyOf(trim(values));
    }

    private Token peek() {
        return tokens.get(i);
    }

    private Token take() {
        Token t = tokens.get(i);
        if (t.kind() != Token.Kind.EOF) {
            i++;
        }
        return t;
    }

    private List<Rule> ruleList() {
        List<Rule> rules = new ArrayList<>();
        while (true) {
            Token t = peek();
            switch (t.kind()) {
                case EOF:
                    return rules;
                case WHITESPACE:
                case CDO:
                case CDC:
                    take();
                    break;
                case AT_KEYWORD:
                    atRule();
                    break;
                default:
                    qualifiedRule(rules);
            }
        }
    }

    /** A top-level at-rule: {@code @keyframes} is read, the rest skipped. */
    private void atRule() {
        Token at = peek();
        if (!at.value().equalsIgnoreCase("keyframes")) {
            skipAtRule();
            return;
        }
        take();
        List<ComponentValue> prelude = new ArrayList<>();
        while (!peek().is(Token.Kind.LBRACE)) {
            if (peek().is(Token.Kind.EOF) || peek().is(Token.Kind.SEMICOLON)) {
                diag.report(CssSyntaxCode.EXPECTED_BLOCK, at.pos(), "expected '{' after @" + at.value());
                take();
                return;
            }
            prelude.add(componentValue());
        }
        keyframes(at, trim(prelude), simpleBlock().contents());
    }

    private void skipAtRule() {
        Token at = take();
        unsupportedAtRule(at);
        while (true) {
            Token t = peek();
            if (t.is(Token.Kind.EOF)) {
                return;
            }
            if (t.is(Token.Kind.SEMICOLON)) {
                take();
                return;
            }
            ComponentValue v = componentValue();
            if (v instanceof ComponentValue.Block b && b.open() == Token.Kind.LBRACE) {
                return;
            }
        }
    }

    private void unsupportedAtRule(Token at) {
        if (at.value().equalsIgnoreCase("starting-style")) {
            diag.report(CssSyntaxCode.UNSUPPORTED_AT_RULE, at.pos(), "unsupported at-rule @" + at.value(),
                    "style an entering element's first frame with :-ix-absent");
        } else {
            diag.report(CssSyntaxCode.UNSUPPORTED_AT_RULE, at.pos(), "unsupported at-rule @" + at.value());
        }
    }

    /** {@code @keyframes name { selectors { declarations } … }}. */
    private void keyframes(Token at, List<ComponentValue> prelude, List<ComponentValue> values) {
        String name = prelude.size() != 1 ? null : keyframesName(prelude.get(0));
        if (name == null) {
            diag.report(CssSyntaxCode.INVALID_KEYFRAMES_NAME, at.pos(), "expected a name after @keyframes");
            return;
        }
        List<ParsedSheet.Keyframe> frames = new ArrayList<>();
        int k = 0;
        int n = values.size();
        while (k < n) {
            ComponentValue v = values.get(k);
            if (isToken(v, Token.Kind.WHITESPACE) || isToken(v, Token.Kind.SEMICOLON)) {
                k++;
                continue;
            }
            int end = blockAt(values, k);
            if (end == n) {
                diag.report(CssSyntaxCode.EXPECTED_BLOCK, v.pos(), "expected '{' after keyframe selector");
                break;
            }
            List<Float> offsets = keyframeOffsets(trim(values.subList(k, end)));
            if (offsets != null) {
                List<Rule.Declaration> decls = declarationList(((ComponentValue.Block) values.get(end)).contents(),
                        null, null);
                List<Rule.Declaration> kept = new ArrayList<>(decls.size());
                for (Rule.Declaration d : decls) {
                    if (d.important()) {
                        diag.report(CssSyntaxCode.IMPORTANT_IN_KEYFRAME, d.pos(), "!important is ignored in a keyframe");
                    } else {
                        kept.add(d);
                    }
                }
                frames.add(new ParsedSheet.Keyframe(offsets, List.copyOf(kept), v.pos()));
            }
            k = end + 1;
        }
        keyframes.add(new ParsedSheet.Keyframes(name, List.copyOf(frames), at.pos()));
    }

    private static String keyframesName(ComponentValue v) {
        if (v instanceof Token t && t.is(Token.Kind.STRING)) {
            return t.value();
        }
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            String lower = t.value().toLowerCase(Locale.ROOT);
            return switch (lower) {
                case "none", "inherit", "initial", "unset", "default" -> null;
                default -> t.value();
            };
        }
        return null;
    }

    /** The offsets of a keyframe selector list, or {@code null} after reporting it. */
    private List<Float> keyframeOffsets(List<ComponentValue> selector) {
        List<Float> out = new ArrayList<>();
        for (List<ComponentValue> item : splitCommas(selector)) {
            Float offset = null;
            if (item.size() == 1 && item.get(0) instanceof Token t) {
                if (t.isIdent("from")) {
                    offset = 0f;
                } else if (t.isIdent("to")) {
                    offset = 1f;
                } else if (t.is(Token.Kind.PERCENTAGE) && t.number() >= 0 && t.number() <= 100) {
                    offset = (float) (t.number() / 100);
                }
            }
            if (offset == null) {
                SourcePos pos = (item.isEmpty() ? selector : item).get(0).pos();
                diag.report(CssSyntaxCode.INVALID_KEYFRAME_SELECTOR, pos, "expected from, to or a percentage");
                return null;
            }
            out.add(offset);
        }
        return List.copyOf(out);
    }

    /** Index of the first {@code {}} block at or after {@code k}, or {@code values.size()}. */
    private static int blockAt(List<ComponentValue> values, int k) {
        while (k < values.size() && !(values.get(k) instanceof ComponentValue.Block b && b.open() == Token.Kind.LBRACE)) {
            k++;
        }
        return k;
    }

    private void qualifiedRule(List<Rule> out) {
        SourcePos start = peek().pos();
        List<ComponentValue> prelude = new ArrayList<>();
        while (true) {
            Token t = peek();
            if (t.is(Token.Kind.EOF)) {
                diag.report(CssSyntaxCode.EXPECTED_BLOCK, start, "expected '{' after selector");
                return;
            }
            if (t.is(Token.Kind.LBRACE)) {
                ComponentValue.Block block = simpleBlock();
                rule(List.copyOf(trim(prelude)), block.contents(), start, out);
                return;
            }
            prelude.add(componentValue());
        }
    }

    /** Adds the rule {@code selector { body }}, then the rules nested in it. */
    private void rule(List<ComponentValue> selector, List<ComponentValue> body, SourcePos start, List<Rule> out) {
        List<Rule> nested = new ArrayList<>();
        List<Rule.Declaration> declarations = declarationList(body, selector, nested);
        out.add(new Rule(selector, declarations, start));
        out.addAll(nested);
    }

    /** The selector list of a rule nested with {@code child} (not empty) in a rule with {@code parent}. */
    private static List<ComponentValue> nest(List<ComponentValue> parent, List<ComponentValue> child) {
        SourcePos at = child.get(0).pos();
        List<ComponentValue> out = new ArrayList<>();
        for (List<ComponentValue> c : splitCommas(child)) {
            for (List<ComponentValue> p : splitCommas(parent)) {
                if (!out.isEmpty()) {
                    out.add(new Token(Token.Kind.COMMA, ",", 0, "", at));
                }
                if (hasAmpersand(c)) {
                    out.addAll(replaceAmpersand(c, p));
                } else {
                    out.addAll(p);
                    out.add(new Token(Token.Kind.WHITESPACE, " ", 0, "", at));
                    out.addAll(c);
                }
            }
        }
        return List.copyOf(out);
    }

    private static List<List<ComponentValue>> splitCommas(List<ComponentValue> values) {
        List<List<ComponentValue>> out = new ArrayList<>();
        int from = 0;
        for (int k = 0; k <= values.size(); k++) {
            if (k == values.size() || isToken(values.get(k), Token.Kind.COMMA)) {
                out.add(trim(values.subList(from, k)));
                from = k + 1;
            }
        }
        return out;
    }

    private static boolean hasAmpersand(List<ComponentValue> values) {
        for (ComponentValue v : values) {
            if (v instanceof Token t && t.isDelim('&')
                    || v instanceof ComponentValue.Function f && hasAmpersand(f.args())) {
                return true;
            }
        }
        return false;
    }

    private static List<ComponentValue> replaceAmpersand(List<ComponentValue> values, List<ComponentValue> parent) {
        List<ComponentValue> out = new ArrayList<>();
        for (ComponentValue v : values) {
            if (v instanceof Token t && t.isDelim('&')) {
                out.addAll(parent);
            } else if (v instanceof ComponentValue.Function f && hasAmpersand(f.args())) {
                out.add(new ComponentValue.Function(f.name(), replaceAmpersand(f.args(), parent), f.pos()));
            } else {
                out.add(v);
            }
        }
        return out;
    }

    private ComponentValue componentValue() {
        Token t = peek();
        switch (t.kind()) {
            case LBRACE:
            case LBRACKET:
            case LPAREN:
                return simpleBlock();
            case FUNCTION:
                return function();
            default:
                return take();
        }
    }

    private ComponentValue.Block simpleBlock() {
        Token open = take();
        Token.Kind close = switch (open.kind()) {
            case LBRACE -> Token.Kind.RBRACE;
            case LBRACKET -> Token.Kind.RBRACKET;
            default -> Token.Kind.RPAREN;
        };
        List<ComponentValue> contents = new ArrayList<>();
        consumeUntil(close, open.pos(), contents);
        return new ComponentValue.Block(open.kind(), contents, open.pos());
    }

    private ComponentValue.Function function() {
        Token name = take();
        List<ComponentValue> args = new ArrayList<>();
        consumeUntil(Token.Kind.RPAREN, name.pos(), args);
        return new ComponentValue.Function(name.value(), args, name.pos());
    }

    private void consumeUntil(Token.Kind close, SourcePos openPos, List<ComponentValue> out) {
        while (true) {
            Token t = peek();
            if (t.is(close)) {
                take();
                return;
            }
            if (t.is(Token.Kind.EOF)) {
                if (!warnedUnclosed) {
                    warnedUnclosed = true;
                    diag.report(CssSyntaxCode.UNCLOSED_BLOCK, openPos, "unclosed block");
                }
                return;
            }
            out.add(componentValue());
        }
    }

    /**
     * The declarations of a block; a statement ending in a {@code {}} block is a nested rule, added
     * to {@code nested} with {@code parent} as its parent selector, or an error when {@code parent}
     * is {@code null}.
     */
    private List<Rule.Declaration> declarationList(List<ComponentValue> values, List<ComponentValue> parent,
                                                   List<Rule> nested) {
        List<Rule.Declaration> decls = new ArrayList<>();
        int k = 0;
        int n = values.size();
        while (k < n) {
            ComponentValue v = values.get(k);
            if (isToken(v, Token.Kind.WHITESPACE) || isToken(v, Token.Kind.SEMICOLON)) {
                k++;
                continue;
            }
            int end = k;
            boolean isRule = false;
            while (end < n && !isToken(values.get(end), Token.Kind.SEMICOLON)) {
                if (values.get(end) instanceof ComponentValue.Block b && b.open() == Token.Kind.LBRACE) {
                    isRule = true;
                    break;
                }
                end++;
            }
            if (isToken(v, Token.Kind.AT_KEYWORD)) {
                unsupportedAtRule((Token) v);
                k = skipStatement(values, k);
            } else if (isRule) {
                if (parent == null) {
                    diag.report(CssSyntaxCode.NESTED_RULE_NOT_ALLOWED, v.pos(), "nested rules are only allowed in a stylesheet");
                } else {
                    ComponentValue.Block block = (ComponentValue.Block) values.get(end);
                    rule(nest(parent, trim(values.subList(k, end))), block.contents(), v.pos(), nested);
                }
                k = end + 1;
            } else if (isToken(v, Token.Kind.IDENT)) {
                Rule.Declaration d = declaration(values.subList(k, end));
                if (d != null) {
                    decls.add(d);
                }
                k = end;
            } else {
                diag.report(CssSyntaxCode.EXPECTED_DECLARATION, v.pos(), "expected a declaration");
                k = skipStatement(values, k);
            }
        }
        return decls;
    }

    /** Index after the next top-level {@code ;} or {@code {}} block, whichever comes first. */
    private static int skipStatement(List<ComponentValue> values, int k) {
        int n = values.size();
        while (k < n) {
            ComponentValue v = values.get(k++);
            if (isToken(v, Token.Kind.SEMICOLON)
                    || (v instanceof ComponentValue.Block b && b.open() == Token.Kind.LBRACE)) {
                break;
            }
        }
        return k;
    }

    private Rule.Declaration declaration(List<ComponentValue> values) {
        Token nameTok = (Token) values.get(0);
        int k = 1;
        while (k < values.size() && isToken(values.get(k), Token.Kind.WHITESPACE)) {
            k++;
        }
        if (k >= values.size() || !isToken(values.get(k), Token.Kind.COLON)) {
            diag.report(CssSyntaxCode.MISSING_COLON, nameTok.pos(), "expected ':' after property name '" + nameTok.value() + "'");
            return null;
        }
        List<ComponentValue> value = trim(new ArrayList<>(values.subList(k + 1, values.size())));
        boolean important = false;
        int last = value.size() - 1;
        if (last >= 1 && value.get(last) instanceof Token t && t.isIdent("important")) {
            int bang = last - 1;
            while (bang >= 0 && isToken(value.get(bang), Token.Kind.WHITESPACE)) {
                bang--;
            }
            if (bang >= 0 && value.get(bang) instanceof Token b && b.isDelim('!')) {
                important = true;
                value = trim(new ArrayList<>(value.subList(0, bang)));
            }
        }
        String name = nameTok.value();
        if (!name.startsWith("--")) {
            name = name.toLowerCase(Locale.ROOT);
        }
        return new Rule.Declaration(name, List.copyOf(value), important, nameTok.pos());
    }

    private static List<ComponentValue> trim(List<ComponentValue> values) {
        int from = 0;
        int to = values.size();
        while (from < to && isToken(values.get(from), Token.Kind.WHITESPACE)) {
            from++;
        }
        while (to > from && isToken(values.get(to - 1), Token.Kind.WHITESPACE)) {
            to--;
        }
        return values.subList(from, to);
    }

    private static boolean isToken(ComponentValue v, Token.Kind kind) {
        return v instanceof Token t && t.kind() == kind;
    }
}
