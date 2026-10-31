package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.runtime.DiagCodes;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssTokenizerTest {
    private static List<Token> lex(String css, Diagnostics diag) {
        return CssTokenizer.tokenize(css, new SourcePos("t.css", 1, 1), diag)
                .stream().filter(t -> !t.is(Token.Kind.WHITESPACE)).toList();
    }

    @Test
    void signedExponentNumbersKeepTheirValueAndUnit() {
        List<Token> t = lex("-.5e2px +3 1e3% 10Px", new Diagnostics());
        assertEquals(Token.Kind.DIMENSION, t.get(0).kind());
        assertEquals(-50.0, t.get(0).number());
        assertEquals("px", t.get(0).unit());
        assertEquals(Token.Kind.NUMBER, t.get(1).kind());
        assertEquals(3.0, t.get(1).number());
        assertEquals(Token.Kind.PERCENTAGE, t.get(2).kind());
        assertEquals(1000.0, t.get(2).number());
        assertEquals("Px", t.get(3).unit());
        assertEquals(Token.Kind.EOF, t.get(4).kind());
    }

    @Test
    void hashColorsStartingWithDigitAreNotIds() {
        List<Token> t = lex("#fff #0af", new Diagnostics());
        assertEquals(Token.Kind.HASH_ID, t.get(0).kind());
        assertEquals("fff", t.get(0).value());
        assertEquals(Token.Kind.HASH, t.get(1).kind());
        assertEquals("0af", t.get(1).value());
    }

    @Test
    void hexEscapeConsumesOneTrailingSpace() {
        List<Token> t = lex("\\31 0 a\\:b", new Diagnostics());
        assertEquals(Token.Kind.IDENT, t.get(0).kind());
        assertEquals("10", t.get(0).value());
        assertEquals("a:b", t.get(1).value());
    }

    @Test
    void newlineInsideStringYieldsBadStringAndLexingResumes() {
        Diagnostics diag = new Diagnostics();
        List<Token> t = lex("'abc\ncolor", diag);
        assertEquals(Token.Kind.BAD_STRING, t.get(0).kind());
        assertTrue(t.get(1).isIdent("color"));
        assertEquals(new SourcePos("t.css", 2, 1), t.get(1).pos());
        assertEquals(List.of("css/newline-in-string@t.css:1:1"), DiagCodes.of(diag));
    }

    @Test
    void commentsVanishAndCrlfCountsAsOneLine() {
        Diagnostics diag = new Diagnostics();
        List<Token> t = lex("a/* x\r\n y */b\r\nc", diag);
        assertEquals("a", t.get(0).value());
        assertEquals("b", t.get(1).value());
        assertEquals(new SourcePos("t.css", 2, 6), t.get(1).pos());
        assertEquals(new SourcePos("t.css", 3, 1), t.get(2).pos());
        assertFalse(diag.hasErrors());
    }

    @Test
    void unquotedUrlIsOneTokenButQuotedUrlIsAFunction() {
        List<Token> t = lex("url( img/a.png ) url(\"b.png\")", new Diagnostics());
        assertEquals(Token.Kind.URL, t.get(0).kind());
        assertEquals("img/a.png", t.get(0).value());
        assertEquals(Token.Kind.FUNCTION, t.get(1).kind());
        assertEquals("url", t.get(1).value());
        assertEquals(Token.Kind.STRING, t.get(2).kind());
    }
}
