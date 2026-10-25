package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** CSS Syntax Level 3 §4 tokenizer. The returned list always ends with an {@code EOF} token. */
final class CssTokenizer {
    private static final int EOF = -1;

    private final char[] src;
    private final int[] lines;
    private final int[] cols;
    private final String file;
    private final Diagnostics diag;
    private int i;

    private CssTokenizer(String source, SourcePos start, Diagnostics diag) {
        this.file = start.file();
        this.diag = diag;
        int n = source.length();
        char[] out = new char[n];
        int[] ln = new int[n + 1];
        int[] cl = new int[n + 1];
        int len = 0;
        int line = start.line();
        int col = start.column();
        for (int k = 0; k < n; k++) {
            char c = source.charAt(k);
            if (c == '\r' && k + 1 < n && source.charAt(k + 1) == '\n') {
                k++;
            }
            if (c == '\r' || c == '\f') {
                c = '\n';
            } else if (c == 0) {
                c = '\uFFFD';
            }
            out[len] = c;
            ln[len] = line;
            cl[len] = col;
            len++;
            if (c == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        ln[len] = line;
        cl[len] = col;
        this.src = len == n ? out : Arrays.copyOf(out, len);
        this.lines = ln;
        this.cols = cl;
    }

    /** Positions are offset so line 1 col 1 of {@code source} maps to {@code start}. */
    static List<Token> tokenize(String source, SourcePos start, Diagnostics diag) {
        CssTokenizer t = new CssTokenizer(source, start, diag);
        List<Token> tokens = new ArrayList<>();
        Token tok;
        do {
            tok = t.next();
            tokens.add(tok);
        } while (tok.kind() != Token.Kind.EOF);
        return tokens;
    }

    private SourcePos pos(int at) {
        return new SourcePos(file, lines[at], cols[at]);
    }

    private int peek(int k) {
        int at = i + k;
        return at < src.length ? src[at] : EOF;
    }

    private static Token token(Token.Kind kind, String value, SourcePos pos) {
        return new Token(kind, value, 0, "", pos);
    }

    private Token next() {
        skipComments();
        int startIdx = i;
        SourcePos p = pos(i);
        int c = peek(0);
        if (c == EOF) {
            return token(Token.Kind.EOF, "", p);
        }
        if (isWhitespace(c)) {
            while (isWhitespace(peek(0))) {
                i++;
            }
            return token(Token.Kind.WHITESPACE, " ", p);
        }
        switch (c) {
            case '"':
            case '\'':
                i++;
                return string((char) c, p);
            case '#':
                if (isIdentChar(peek(1)) || isValidEscape(peek(1), peek(2))) {
                    i++;
                    boolean id = wouldStartIdent(peek(0), peek(1), peek(2));
                    return token(id ? Token.Kind.HASH_ID : Token.Kind.HASH, identSequence(), p);
                }
                break;
            case '(':
                i++;
                return token(Token.Kind.LPAREN, "(", p);
            case ')':
                i++;
                return token(Token.Kind.RPAREN, ")", p);
            case '[':
                i++;
                return token(Token.Kind.LBRACKET, "[", p);
            case ']':
                i++;
                return token(Token.Kind.RBRACKET, "]", p);
            case '{':
                i++;
                return token(Token.Kind.LBRACE, "{", p);
            case '}':
                i++;
                return token(Token.Kind.RBRACE, "}", p);
            case ',':
                i++;
                return token(Token.Kind.COMMA, ",", p);
            case ':':
                i++;
                return token(Token.Kind.COLON, ":", p);
            case ';':
                i++;
                return token(Token.Kind.SEMICOLON, ";", p);
            case '+':
            case '.':
                if (startsNumber(c, peek(1), peek(2))) {
                    return numeric(p);
                }
                break;
            case '-':
                if (startsNumber(c, peek(1), peek(2))) {
                    return numeric(p);
                }
                if (peek(1) == '-' && peek(2) == '>') {
                    i += 3;
                    return token(Token.Kind.CDC, "-->", p);
                }
                if (wouldStartIdent(c, peek(1), peek(2))) {
                    return identLike(p);
                }
                break;
            case '<':
                if (peek(1) == '!' && peek(2) == '-' && peek(3) == '-') {
                    i += 4;
                    return token(Token.Kind.CDO, "<!--", p);
                }
                break;
            case '@':
                if (wouldStartIdent(peek(1), peek(2), peek(3))) {
                    i++;
                    return token(Token.Kind.AT_KEYWORD, identSequence(), p);
                }
                break;
            case '\\':
                if (isValidEscape(c, peek(1))) {
                    return identLike(p);
                }
                diag.error(p, "invalid escape");
                break;
            default:
                if (isDigit(c)) {
                    return numeric(p);
                }
                if (isIdentStart(c)) {
                    return identLike(p);
                }
        }
        i = startIdx + 1;
        return token(Token.Kind.DELIM, String.valueOf((char) c), p);
    }

    private void skipComments() {
        while (peek(0) == '/' && peek(1) == '*') {
            SourcePos p = pos(i);
            i += 2;
            while (true) {
                if (peek(0) == EOF) {
                    diag.warning(p, "unclosed comment");
                    return;
                }
                if (peek(0) == '*' && peek(1) == '/') {
                    i += 2;
                    break;
                }
                i++;
            }
        }
    }

    private Token string(char ending, SourcePos p) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = peek(0);
            if (c == EOF) {
                diag.warning(p, "unclosed string");
                return token(Token.Kind.STRING, sb.toString(), p);
            }
            if (c == ending) {
                i++;
                return token(Token.Kind.STRING, sb.toString(), p);
            }
            if (c == '\n') {
                diag.error(p, "unterminated string");
                return token(Token.Kind.BAD_STRING, sb.toString(), p);
            }
            if (c == '\\') {
                int n = peek(1);
                if (n == EOF) {
                    i++;
                } else if (n == '\n') {
                    i += 2;
                } else {
                    i++;
                    sb.appendCodePoint(escape());
                }
                continue;
            }
            sb.append((char) c);
            i++;
        }
    }

    private Token numeric(SourcePos p) {
        int start = i;
        if (peek(0) == '+' || peek(0) == '-') {
            i++;
        }
        skipDigits();
        if (peek(0) == '.' && isDigit(peek(1))) {
            i++;
            skipDigits();
        }
        int e = peek(0);
        if (e == 'e' || e == 'E') {
            int s = peek(1);
            if (isDigit(s)) {
                i++;
                skipDigits();
            } else if ((s == '+' || s == '-') && isDigit(peek(2))) {
                i += 2;
                skipDigits();
            }
        }
        double number = Double.parseDouble(new String(src, start, i - start));
        if (wouldStartIdent(peek(0), peek(1), peek(2))) {
            return new Token(Token.Kind.DIMENSION, "", number, identSequence(), p);
        }
        if (peek(0) == '%') {
            i++;
            return new Token(Token.Kind.PERCENTAGE, "", number, "", p);
        }
        return new Token(Token.Kind.NUMBER, "", number, "", p);
    }

    private void skipDigits() {
        while (isDigit(peek(0))) {
            i++;
        }
    }

    private Token identLike(SourcePos p) {
        String name = identSequence();
        if (peek(0) != '(') {
            return token(Token.Kind.IDENT, name, p);
        }
        i++;
        if (name.equalsIgnoreCase("url")) {
            while (isWhitespace(peek(0)) && isWhitespace(peek(1))) {
                i++;
            }
            int a = peek(0);
            int b = peek(1);
            boolean quoted = a == '"' || a == '\'' || (isWhitespace(a) && (b == '"' || b == '\''));
            if (!quoted) {
                return url(p);
            }
        }
        return token(Token.Kind.FUNCTION, name, p);
    }

    private Token url(SourcePos p) {
        StringBuilder sb = new StringBuilder();
        while (isWhitespace(peek(0))) {
            i++;
        }
        while (true) {
            int c = peek(0);
            if (c == ')') {
                i++;
                return token(Token.Kind.URL, sb.toString(), p);
            }
            if (c == EOF) {
                diag.warning(p, "unclosed url");
                return token(Token.Kind.URL, sb.toString(), p);
            }
            if (isWhitespace(c)) {
                while (isWhitespace(peek(0))) {
                    i++;
                }
                if (peek(0) == ')' || peek(0) == EOF) {
                    continue;
                }
                return badUrl(p, sb);
            }
            if (c == '"' || c == '\'' || c == '(' || isNonPrintable(c)) {
                return badUrl(p, sb);
            }
            if (c == '\\') {
                if (!isValidEscape(c, peek(1))) {
                    return badUrl(p, sb);
                }
                i++;
                sb.appendCodePoint(escape());
                continue;
            }
            sb.append((char) c);
            i++;
        }
    }

    private Token badUrl(SourcePos p, StringBuilder sb) {
        diag.error(p, "invalid url");
        while (true) {
            int c = peek(0);
            if (c == EOF) {
                break;
            }
            if (c == ')') {
                i++;
                break;
            }
            if (isValidEscape(c, peek(1))) {
                i++;
                escape();
                continue;
            }
            i++;
        }
        return token(Token.Kind.BAD_URL, sb.toString(), p);
    }

    private String identSequence() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = peek(0);
            if (isIdentChar(c)) {
                sb.append((char) c);
                i++;
            } else if (isValidEscape(c, peek(1))) {
                i++;
                sb.appendCodePoint(escape());
            } else {
                return sb.toString();
            }
        }
    }

    /** Consumes an escape whose backslash was already consumed. */
    private int escape() {
        int c = peek(0);
        if (c == EOF) {
            return 0xFFFD;
        }
        i++;
        if (!isHex(c)) {
            return c;
        }
        int value = Character.digit(c, 16);
        for (int k = 1; k < 6 && isHex(peek(0)); k++) {
            value = value * 16 + Character.digit(peek(0), 16);
            i++;
        }
        if (isWhitespace(peek(0))) {
            i++;
        }
        if (value == 0 || value > 0x10FFFF || (value >= 0xD800 && value <= 0xDFFF)) {
            return 0xFFFD;
        }
        return value;
    }

    private static boolean startsNumber(int c1, int c2, int c3) {
        if (c1 == '+' || c1 == '-') {
            return isDigit(c2) || (c2 == '.' && isDigit(c3));
        }
        if (c1 == '.') {
            return isDigit(c2);
        }
        return isDigit(c1);
    }

    private static boolean wouldStartIdent(int c1, int c2, int c3) {
        if (c1 == '-') {
            return isIdentStart(c2) || c2 == '-' || isValidEscape(c2, c3);
        }
        if (c1 == '\\') {
            return isValidEscape(c1, c2);
        }
        return isIdentStart(c1);
    }

    private static boolean isValidEscape(int c1, int c2) {
        return c1 == '\\' && c2 != '\n' && c2 != EOF;
    }

    private static boolean isIdentStart(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_' || c >= 0x80;
    }

    private static boolean isIdentChar(int c) {
        return isIdentStart(c) || isDigit(c) || c == '-';
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isHex(int c) {
        return isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static boolean isWhitespace(int c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    private static boolean isNonPrintable(int c) {
        return (c >= 0 && c <= 8) || c == 0x0B || (c >= 0x0E && c <= 0x1F) || c == 0x7F;
    }
}
