package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.paint.DisplayList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentTest {
    private static UiDocument load(String html, String css) {
        return UiDocument.load(html, "t.html", List.of(new UiDocument.CssSource(css, "t.css")), GoldenTest.MONO);
    }

    private static String frame(UiDocument doc, float w, float h) {
        DisplayList out = new DisplayList();
        doc.frame(w, h, out);
        return GoldenTest.print(out);
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
}
