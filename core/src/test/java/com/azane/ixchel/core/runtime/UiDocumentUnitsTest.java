package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.html.HtmlElements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentUnitsTest {
    private static UiDocument load(String html, String css) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
    }

    @Test
    void emIsOfTheParentForFontSizeAndOfTheElementOtherwiseEvenThroughVarRemOfTheRootElement() {
        UiDocument doc = load("<div class=\"r\"><div class=\"a\"></div><div class=\"b\"></div><div class=\"c\"></div><div class=\"d\"></div></div>", """
                .r { font-size: 2rem }
                .a, .b, .c, .d { height: 2px; background-color: #fff }
                .a { font-size: 2em; width: 1em }
                .b { font-size: 4px; width: 1rem }
                .c { font-size: 50%; width: calc(1em + 1px) }
                .d { --w: 2em; width: var(--w) }
                """);
        assertEquals("""
                rect 0 0 36 2 #FFFFFFFF
                rect 0 2 18 2 #FFFFFFFF
                rect 0 4 10 2 #FFFFFFFF
                rect 0 6 36 2 #FFFFFFFF""", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void mathFunctionsOverPercentagesResolveAgainstTheContainingBlock() {
        UiDocument doc = load("<div class=\"r\"><div class=\"a\"></div><div class=\"b\"></div><div class=\"c\"></div></div>", """
                .r { width: 100% }
                .a, .b, .c { height: 2px; background-color: #fff }
                .a { width: calc((50% - 10px) / 2 * 2) }
                .b { width: min(50%, 30px) }
                .c { width: clamp(10px, 5%, max(20px, 1px)) }
                """);
        assertEquals("""
                rect 0 0 40 2 #FFFFFFFF
                rect 0 2 30 2 #FFFFFFFF
                rect 0 4 10 2 #FFFFFFFF""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals("""
                rect 0 0 190 2 #FFFFFFFF
                rect 0 2 30 2 #FFFFFFFF
                rect 0 4 20 2 #FFFFFFFF""", GoldenTest.print(doc.frame(400, 100)));
    }

    @Test
    void viewportUnitsFollowAViewportChange() {
        UiDocument doc = load("<div class=\"a\"></div>", ".a { width: 50vw; height: calc(10vh + 1px); background-color: #fff }");
        assertEquals("rect 0 0 50 11 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        assertEquals("rect 0 0 30 3 #FFFFFFFF", GoldenTest.print(doc.frame(60, 20)));
    }

    @Test
    void malformedLengthsAreReportedWhereTheyAreWritten() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", """
                div { width: 2pt }
                div { width: calc(1px+2px) }
                div { width: calc(1px * 2px) }
                div { padding: 1em 2em 3em 4em 5em }
                div { width: min(1px, 2) }
                """));
        assertEquals(List.of("css/unsupported-unit@t.css:1:14", "css/function-arguments@t.css:2:22",
                "css/function-arguments@t.css:3:23", "css/value-count@t.css:4:32", "css/function-arguments@t.css:5:14"),
                DiagCodes.of(e));
    }
}
