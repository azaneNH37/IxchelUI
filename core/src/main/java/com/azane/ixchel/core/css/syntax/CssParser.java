package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CSS Syntax Level 3 §5 parser restricted to what IxchelUI supports: qualified rules whose blocks hold
 * declarations. At-rules and nested rules are reported as errors and skipped.
 */
public final class CssParser {
    private final List<Token> tokens;
    private final Diagnostics diag;
    private int i;
    private boolean warnedUnclosed;

    private CssParser(List<Token> tokens, Diagnostics diag) {
        this.tokens = tokens;
        this.diag = diag;
    }

    public static List<Rule> parseStylesheet(String source, String file, Diagnostics diag) {
        return parseStylesheet(source, new SourcePos(file, 1, 1), diag);
    }

    /** {@code start} is where an inline {@code <style>} body begins. */
    public static List<Rule> parseStylesheet(String source, SourcePos start, Diagnostics diag) {
        CssParser p = new CssParser(CssTokenizer.tokenize(source, start, diag), diag);
        return p.ruleList();
    }

    /** For a `style="..."` attribute; positions are offset so line 1 col 1 of `source` maps to `start`. */
    public static List<Rule.Declaration> parseDeclarations(String source, SourcePos start, Diagnostics diag) {
        CssParser p = new CssParser(CssTokenizer.tokenize(source, start, diag), diag);
        List<ComponentValue> values = new ArrayList<>();
        while (!p.peek().is(Token.Kind.EOF)) {
            values.add(p.componentValue());
        }
        return p.declarationList(values);
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
                    skipAtRule();
                    break;
                default:
                    Rule r = qualifiedRule();
                    if (r != null) {
                        rules.add(r);
                    }
            }
        }
    }

    private void skipAtRule() {
        Token at = take();
        diag.error(at.pos(), "unsupported at-rule @" + at.value());
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

    private Rule qualifiedRule() {
        SourcePos start = peek().pos();
        List<ComponentValue> prelude = new ArrayList<>();
        while (true) {
            Token t = peek();
            if (t.is(Token.Kind.EOF)) {
                diag.error(start, "expected '{' after selector");
                return null;
            }
            if (t.is(Token.Kind.LBRACE)) {
                ComponentValue.Block block = simpleBlock();
                return new Rule(List.copyOf(trim(prelude)), declarationList(block.contents()), start);
            }
            prelude.add(componentValue());
        }
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
                    diag.warning(openPos, "unclosed block");
                }
                return;
            }
            out.add(componentValue());
        }
    }

    private List<Rule.Declaration> declarationList(List<ComponentValue> values) {
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
            while (end < n && !isToken(values.get(end), Token.Kind.SEMICOLON)) {
                end++;
            }
            if (isToken(v, Token.Kind.AT_KEYWORD)) {
                diag.error(v.pos(), "unsupported at-rule @" + ((Token) v).value());
                k = skipStatement(values, k);
            } else if (isToken(v, Token.Kind.IDENT)) {
                Rule.Declaration d = declaration(values.subList(k, end));
                if (d != null) {
                    decls.add(d);
                }
                k = end;
            } else {
                diag.error(v.pos(), "expected a declaration");
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
            diag.error(nameTok.pos(), "expected ':' after property name '" + nameTok.value() + "'");
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
