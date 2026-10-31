package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentBorderTest {
    private static UiDocument load(String html, String css) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), TagRegistry.HTML,
                GoldenTest.MONO, GoldenTest.NO_IMAGES);
    }

    @Test
    void borderTakesLayoutSpaceAndPaintsOverTheBackgroundOneRectPerSide() {
        UiDocument doc = load("<div class=\"a\"><div class=\"c\"></div></div><div class=\"b\"></div>", """
                .a { width: 20px; height: 12px; padding: 1px; background-color: #000;
                     border: 2px solid #f00; border-left: 3px solid #0f0 }
                .c { width: 4px; height: 4px; background-color: #00f }
                .b { width: 6px; height: 6px; border-width: 4px }
                """);
        // .b has a width but no style: its used border is 0, so it sits right below .a's 12px box.
        assertEquals("""
                rect 0 0 20 12 #FF000000
                rect 0 0 20 2 #FFFF0000
                rect 0 10 20 2 #FFFF0000
                rect 0 2 3 8 #FF00FF00
                rect 18 2 2 8 #FFFF0000
                rect 4 3 4 4 #FF0000FF""", GoldenTest.print(doc.frame(100, 100)));
        Element b = (Element) doc.root().children().get(1);
        assertEquals(12, b.box().y());
        assertEquals(6, b.box().height());
    }

    @Test
    void scrollContainerClipsAndHitTestsItsPaddingBoxAndScrollsToItsBottomEdge() {
        UiDocument doc = load("""
                <div id="list"><div id="r0">a</div><div id="r1">b</div><div id="r2">c</div></div>
                """, """
                #list { width: 30px; height: 24px; overflow: auto; border: 2px solid #fff }
                #list > div { height: 10px; flex-shrink: 0 }
                """);
        assertEquals("""
                rect 0 0 30 2 #FFFFFFFF
                rect 0 22 30 2 #FFFFFFFF
                rect 0 2 2 20 #FFFFFFFF
                rect 28 2 2 20 #FFFFFFFF
                clip 2 2 26 20
                text 2 2 9 #FFFFFFFF "a"
                text 2 12 9 #FFFFFFFF "b"
                text 2 22 9 #FFFFFFFF "c"
                rect 24 2 4 13 #FFA0A0A0
                unclip""", GoldenTest.print(doc.frame(100, 100)));
        Element list = (Element) doc.root().children().get(0);
        // 30px of rows in a 20px scrollport.
        assertEquals(10, list.maxScrollTop());
        // y 22.5 is in the third row's box but on the bottom border: the list, not the row.
        assertSame(list, doc.events().hitTest(10, 22.5f));
        assertEquals("r1", doc.events().hitTest(10, 15).attribute("id"));
    }

    @Test
    void borderShorthandRejectsARepeatedWidth() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", "div { border: 1px 2px solid }"));
        assertEquals(List.of("css/repeated-component@t.css:1:19"), DiagCodes.of(e));
    }

    @Test
    void borderStyleRejectsAnUnsupportedStyle() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", "div { border-style: dashed }"));
        assertEquals(List.of("css/invalid-value@t.css:1:21"), DiagCodes.of(e));
    }
}
