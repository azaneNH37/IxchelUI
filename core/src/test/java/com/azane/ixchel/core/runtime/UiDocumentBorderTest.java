package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.view.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentBorderTest {
    private static UiDocument load(String html, String css) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
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
        assertEquals(List.of("css/repeated-component@t.css:1:32"), DiagCodes.of(e));
    }

    @Test
    void borderStyleRejectsAnUnsupportedStyle() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", "div { border-style: dashed }"));
        assertEquals(List.of("css/invalid-value@t.css:1:34"), DiagCodes.of(e));
    }

    @Test
    void outlineRingsTheBorderBoxOverTheChildrenWithoutTakingRoomOrHits() {
        UiDocument doc = load("<div class=\"o\"><div class=\"c\"></div></div><div class=\"n\"></div>", """
                .o { width: 10px; height: 10px; outline: 2px solid #f00; outline-offset: 1px }
                .c { width: 4px; height: 4px; background-color: #00f }
                .n { width: 4px; height: 4px; background-color: #0f0 }
                """);
        assertEquals("""
                rect 0 0 4 4 #FF0000FF
                rect -3 -3 16 2 #FFFF0000
                rect -3 11 16 2 #FFFF0000
                rect -3 -1 2 12 #FFFF0000
                rect 11 -1 2 12 #FFFF0000
                rect 0 10 4 4 #FF00FF00""", GoldenTest.print(doc.frame(100, 100)));
        assertSame(doc.root(), doc.events().hitTest(-2, 5), "on the outline");
        assertSame(doc.root().children().get(1), doc.events().hitTest(2, 11), "under the outline, the next box");
    }

    @Test
    void outlineFollowsTheCornersGrownByItsOffsetAndANegativeOffsetMovesItInside() {
        UiDocument doc = load("<div class=\"r\"></div><div class=\"i\"></div><div class=\"z\"></div>", """
                .r { width: 10px; height: 10px; border-radius: 3px; corner-shape: bevel; outline: 1px solid #fff }
                .i { width: 10px; height: 10px; outline: 1px solid #fff; outline-offset: -3px }
                .z { width: 10px; height: 10px; outline-width: 4px; outline-color: #fff }
                """);
        assertEquals("""
                border -1 -1 12 12 radii 4/4 4/4 4/4 4/4 corners bevel bevel bevel bevel widths 1 1 1 1 #FFFFFFFF #FFFFFFFF #FFFFFFFF #FFFFFFFF
                rect 2 12 6 1 #FFFFFFFF
                rect 2 17 6 1 #FFFFFFFF
                rect 2 13 1 4 #FFFFFFFF
                rect 7 13 1 4 #FFFFFFFF""", GoldenTest.print(doc.frame(100, 100)), "z's style is none");
    }

    @Test
    void outlineRejectsUnsupportedStyles() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", """
                .a { outline: 1px dashed red }
                .b { outline-style: auto }
                .c { outline-offset: 10% }
                """));
        assertEquals(List.of("css/unsupported-value@t.css:1:32", "css/unsupported-value@t.css:2:21",
                "css/invalid-value@t.css:3:22"), DiagCodes.of(e));
    }
}
