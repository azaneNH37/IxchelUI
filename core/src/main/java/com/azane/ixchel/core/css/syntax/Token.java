package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.SourcePos;

/**
 * A CSS token. {@code value} holds the unescaped name/string/url/delim character for the kinds that
 * have one; numeric kinds carry {@code number}, and {@code DIMENSION} also {@code unit} (as written).
 */
public record Token(Kind kind, String value, double number, String unit, SourcePos pos) implements ComponentValue {
    public enum Kind {
        IDENT, FUNCTION, AT_KEYWORD,
        /** {@code #name} whose name would be a valid identifier ("id" type flag). */
        HASH_ID,
        /** {@code #name} that is not a valid identifier, e.g. {@code #0af}. */
        HASH,
        STRING, BAD_STRING, URL, BAD_URL, DELIM,
        NUMBER, PERCENTAGE, DIMENSION,
        WHITESPACE, CDO, CDC, COLON, SEMICOLON, COMMA,
        LBRACKET, RBRACKET, LPAREN, RPAREN, LBRACE, RBRACE,
        EOF
    }

    public boolean is(Kind k) {
        return kind == k;
    }

    public boolean isDelim(char c) {
        return kind == Kind.DELIM && value.length() == 1 && value.charAt(0) == c;
    }

    public boolean isIdent(String name) {
        return kind == Kind.IDENT && value.equalsIgnoreCase(name);
    }
}
