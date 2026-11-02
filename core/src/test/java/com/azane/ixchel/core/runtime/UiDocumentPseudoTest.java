package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.html.HtmlElements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code ::before} and {@code ::after}: decoration boxes outside the element's children. */
class UiDocumentPseudoTest {
    private long nowMs;

    private UiDocument load(String html, String css) {
        UiDocument doc = UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), HtmlElements.TAGS,
                GoldenTest.MONO, GoldenTest.NO_IMAGES);
        doc.clock(() -> nowMs * 1_000_000);
        return doc;
    }

    private static final String ROW = """
            .b { width: 20px; flex-direction: row; align-items: flex-start }
            .c { width: 4px; height: 4px; background-color: #fff }
            .b::before { content: ""; width: 2px; height: 2px; background-color: #f00 }
            .b::after { content: ""; width: 3px; height: 3px; background-color: #00f }
            .c::before { width: 1px; height: 1px; background-color: #0f0 }
            """;

    @Test
    void beforeAndAfterBoxesLayOutAroundTheChildrenButAreNotChildren() {
        UiDocument doc = load("<div class=\"b\"><div class=\"c\"></div></div>", ROW);
        // .c::before has no content, so no box.
        assertEquals("""
                rect 0 0 2 2 #FFFF0000
                rect 2 0 4 4 #FFFFFFFF
                rect 6 0 3 3 #FF0000FF""", GoldenTest.print(doc.frame(100, 100)));
        Element b = (Element) doc.root().children().get(0);
        assertEquals(1, b.children().size());
        assertEquals(List.of(b.children().get(0)), doc.querySelectorAll(".b *"));
        assertNull(((Element) b.children().get(0)).before());
    }

    @Test
    void theElementTakesThePointerOverItsPseudoBoxesAndItsHoverStylesThem() {
        UiDocument doc = load("<div class=\"b\"><div class=\"c\"></div></div>", ROW + """
                .b:hover::after { background-color: #0f0 }
                .b:hover::before { content: none }
                """);
        doc.frame(100, 100);
        Element b = (Element) doc.root().children().get(0);
        assertSame(b, doc.events().hitTest(7, 1));
        doc.events().mouseMoved(7, 1);
        // The ::before box is gone, so the child moves to the start.
        assertEquals("""
                rect 0 0 4 4 #FFFFFFFF
                rect 4 0 3 3 #FF00FF00""", GoldenTest.print(doc.frame(100, 100)));
        assertNull(b.before());
    }

    @Test
    void aPseudoBoxTransitionsLikeAnElement() {
        UiDocument doc = load("<div class=\"b\"><div class=\"c\"></div></div>", ROW + """
                .b::after { transition: width 100ms linear }
                .b:hover::after { width: 13px }
                """);
        doc.frame(100, 100);
        doc.events().mouseMoved(7, 1);
        doc.frame(100, 100);
        nowMs = 50;
        assertEquals("rect 6 0 8 3 #FF0000FF", GoldenTest.print(doc.frame(100, 100)).lines().toList().get(2));
    }

    @Test
    void inABlockAPseudoBoxIsInlineContent() {
        UiDocument doc = load("<div class=\"p\">ab</div>", """
                .p { display: block }
                .p::before { content: ""; display: inline-block; width: 3px; height: 3px; background-color: #f00 }
                """);
        assertEquals("""
                rect 0 4 3 3 #FFFF0000
                text 3 0 9 #FFFFFFFF "ab\"""", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void contentTakesNoText() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", """
                .a::before { content: "x" }
                .b::after { content: attr(title) }
                """));
        assertEquals(List.of("css/unsupported-value@t.css:1:23", "css/unsupported-value@t.css:2:22"), DiagCodes.of(e));
    }
}
