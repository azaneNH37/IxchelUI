package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.text.TextMeasurer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentTest {
    private static UiDocument load(String html, String css) {
        return load(html, css, GoldenTest.MONO);
    }

    private static UiDocument load(String html, String css, TextMeasurer measurer) {
        return UiDocument.load(html, "t.html", List.of(new UiDocument.CssSource(css, "t.css")), TagRegistry.HTML, measurer);
    }

    private static String frame(UiDocument doc, float w, float h) {
        return GoldenTest.print(doc.frame(w, h));
    }

    @Test
    void textAndClassChangesReflowSiblingsOnTheNextFrame() {
        UiDocument doc = load("""
                <div class="row"><span class="n">30</span><div class="mark"></div></div>
                """, """
                .row { flex-direction: row; align-items: flex-start }
                .mark { width: 2px; height: 2px; background-color: #fff }
                .gone { display: none }
                """);
        assertEquals("text 0 0 9 #FFFFFFFF \"30\"\nrect 12 0 2 2 #FFFFFFFF", frame(doc, 100, 100));

        Element row = (Element) doc.root().children().get(0);
        Element span = (Element) row.children().get(0);
        ((TextNode) span.children().get(0)).setText("300");
        assertEquals("text 0 0 9 #FFFFFFFF \"300\"\nrect 18 0 2 2 #FFFFFFFF", frame(doc, 100, 100));

        span.addClass("gone");
        assertEquals("rect 0 0 2 2 #FFFFFFFF", frame(doc, 100, 100));
    }

    @Test
    void percentagesResolveAgainstTheViewportAndTextWrapsToItsBox() {
        UiDocument doc = load("""
                <div class="half"></div>
                <div class="narrow">aa bb cc</div>
                """, """
                .half { width: 50%; height: 10px; background-color: #f00 }
                .narrow { width: 30px; align-items: flex-start }
                """);
        assertEquals("""
                rect 0 0 240 10 #FFFF0000
                text 0 10 9 #FFFFFFFF "aa bb"
                text 0 19 9 #FFFFFFFF "cc\"""", frame(doc, 480, 270));
    }

    @Test
    void loadReportsTemplateAndStyleErrorsTogether() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load(
                "<div class=\"a\" style=\"colr: red\"><blink></blink></div>",
                ".a { width: 10 }"));
        String m = e.getMessage();
        assertTrue(m.startsWith("3 errors"), m);
        assertTrue(m.contains("t.html:1:34:") && m.contains("unknown tag"), m);
        assertTrue(m.contains("t.html:1:23:") && m.contains("unsupported property 'colr'"), m);
        assertTrue(m.contains("t.css:1:13:"), m);
    }

    @Test
    void unchangedFrameKeepsTheDisplayListVersionAndAChangeBumpsIt() {
        UiDocument doc = load("<span class=\"n\">30</span>", ".n { color: #f00 }");
        long first = doc.frame(100, 100).version();
        assertEquals(first, doc.frame(100, 100).version());

        ((TextNode) ((Element) doc.root().children().get(0)).children().get(0)).setText("29");
        assertNotEquals(first, doc.frame(100, 100).version());
    }

    @Test
    void changingIdRestylesTheElement() {
        UiDocument doc = load("<span id=\"a\">x</span>", "#b { color: #f00 }");
        assertEquals("text 0 0 9 #FFFFFFFF \"x\"", frame(doc, 100, 100));
        ((Element) doc.root().children().get(0)).setAttribute("id", "b");
        assertEquals("text 0 0 9 #FFFF0000 \"x\"", frame(doc, 100, 100));
    }

    @Test
    void movedElementTakesInheritedValuesFromItsNewParent() {
        UiDocument doc = load("""
                <div class="red"><span>x</span></div>
                <div class="blue"></div>
                """, ".red { color: #f00 } .blue { color: #00f }");
        frame(doc, 100, 100);
        Element red = (Element) doc.root().children().get(0);
        Element blue = (Element) doc.root().children().get(1);
        blue.appendChild(red.children().get(0));
        assertEquals("text 0 0 9 #FF0000FF \"x\"", frame(doc, 100, 100));
    }

    @Test
    void invalidateTextRemeasuresWithTheCurrentFont() {
        float[] advance = {6};
        TextMeasurer font = new TextMeasurer() {
            @Override
            public float width(String text, float size) {
                return text.length() * advance[0];
            }

            @Override
            public float lineHeight(float size) {
                return size;
            }
        };
        UiDocument doc = load("""
                <div class="row"><span>ab</span><div class="mark"></div></div>
                """, """
                .row { flex-direction: row; align-items: flex-start }
                .mark { width: 1px; height: 1px; background-color: #fff }
                """, font);
        assertTrue(frame(doc, 100, 100).endsWith("rect 12 0 1 1 #FFFFFFFF"));
        advance[0] = 8;
        doc.invalidateText();
        assertTrue(frame(doc, 100, 100).endsWith("rect 16 0 1 1 #FFFFFFFF"));
    }
}
