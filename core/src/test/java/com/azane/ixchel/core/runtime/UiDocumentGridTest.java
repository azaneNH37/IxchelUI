package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentGridTest {
    private static UiDocument load(String html, String css) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
    }

    /** {@code x y w h} of each child of the first top-level element, after a frame. */
    private static String boxes(UiDocument doc) {
        doc.frame(200, 200);
        StringBuilder sb = new StringBuilder();
        for (var c : ((Element) doc.root().children().get(0)).children()) {
            LayoutBox b = ((Element) c).box();
            sb.append(sb.isEmpty() ? "" : " | ").append(fmt(b.x())).append(' ').append(fmt(b.y())).append(' ')
                    .append(fmt(b.width())).append(' ').append(fmt(b.height()));
        }
        return sb.toString();
    }

    private static String fmt(float f) {
        return f == Math.rint(f) ? Integer.toString((int) f) : Float.toString(f);
    }

    @Test
    void tracksSizeByLengthFrAndMinmaxAndItemsPlaceByNamedLinesSpansAndAutoFlow() {
        // Columns minmax(10px, 20px) grows to 20, then 2 * 1fr in 100px less two 10px gaps: 20, 30, 30.
        // Lines: left 1, mid 2 and 3, right 4. a takes column 3 of row 2; b spans columns 1-2 of row 1;
        // c fills column 3; d skips to row 2.
        UiDocument doc = load("""
                <div class="g"><div class="a"></div><div class="b"></div><div class="c"></div><div class="d"></div></div>
                """, """
                .g { display: grid; width: 100px; height: 60px; column-gap: 10px;
                     grid-template-columns: [left] minmax(10px, 20px) repeat(2, [mid] 1fr) [right];
                     grid-template-rows: 10px 1fr }
                .a { grid-column: mid 2 / right; grid-row: 2 }
                .b { grid-column: span 2 }
                """);
        assertEquals("70 10 30 50 | 0 0 60 10 | 70 0 30 10 | 0 10 20 50", boxes(doc));
        assertEquals(List.of(), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void areasPlaceItemsAndJustifyAndAlignAlignThemInTheirCells() {
        UiDocument doc = load("""
                <div class="g"><div class="h"></div><div class="s"></div><div class="m"></div></div>
                """, """
                .g { display: grid; width: 90px; height: 40px; grid-template-columns: 30px 60px; grid-template-rows: 20px 20px;
                     grid-template-areas: "h h" "s m"; justify-items: center; align-items: start }
                .h { grid-area: h; width: 10px; height: 5px }
                .s { grid-row: s-start; grid-column: s; width: 10px; height: 5px; justify-self: end }
                .m { grid-area: m; height: 5px; place-self: end stretch }
                """);
        assertEquals("40 0 10 5 | 20 20 10 5 | 30 35 60 5", boxes(doc));
    }

    @Test
    void autoFillRepeatsAsManyTracksAsFitAndAutoFitCollapsesTheEmptyOnes() {
        UiDocument fill = load("""
                <div class="g"><div></div><div></div><div></div><div></div><div></div></div>
                """, """
                .g { display: grid; width: 100px; grid-template-columns: repeat(auto-fill, 30px); grid-auto-rows: 10px }
                """);
        assertEquals("0 0 30 10 | 30 0 30 10 | 60 0 30 10 | 0 10 30 10 | 30 10 30 10", boxes(fill));
        UiDocument fit = load("""
                <div class="g"><div></div><div></div></div>
                """, """
                .g { display: grid; width: 100px; grid-template-columns: repeat(auto-fit, 30px); grid-auto-rows: 10px;
                     justify-content: center }
                """);
        assertEquals("20 0 30 10 | 50 0 30 10", boxes(fit));
    }

    @Test
    void autoFlowColumnFillsColumnsAndAutoTracksCycle() {
        UiDocument doc = load("""
                <div class="g"><div></div><div></div><div></div></div>
                """, """
                .g { display: grid; grid-template-rows: 10px 10px; grid-auto-flow: column; grid-auto-columns: 20px 30px }
                """);
        assertEquals("0 0 20 10 | 0 10 20 10 | 20 0 30 10", boxes(doc));
    }

    @Test
    void alignContentPacksFlexLinesAndGridTracks() {
        UiDocument flex = load("""
                <div class="f"><div></div><div></div></div>
                """, """
                .f { display: flex; flex-direction: row; flex-wrap: wrap; width: 20px; height: 40px; align-content: center }
                .f > div { width: 20px; height: 10px }
                """);
        assertEquals("0 10 20 10 | 0 20 20 10", boxes(flex));
        UiDocument grid = load("""
                <div class="g"><div></div><div></div></div>
                """, """
                .g { display: grid; width: 40px; height: 40px; grid-template-columns: 10px; grid-template-rows: 10px 10px;
                     place-content: end }
                """);
        assertEquals("30 20 10 10 | 30 30 10 10", boxes(grid));
    }

    @Test
    void malformedGridValuesAreLoadErrors() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div class=\"a\"></div>", """
                .a { grid-template-columns: repeat(auto-fill, 1fr) }
                .b { grid-template-columns: repeat(auto-fill, [x] 10px) }
                .c { grid-template-areas: "a b" "b a" }
                .d { grid-row: 0 / 2 }
                .e { grid-template-columns: minmax(1fr, 10px) }
                .f { grid-auto-flow: dense row column }
                """));
        assertEquals(List.of("css/function-arguments@t.css:1:47", "css/unsupported-value@t.css:2:47",
                "css/invalid-value@t.css:3:27", "css/invalid-value@t.css:4:16", "css/function-arguments@t.css:5:36",
                "css/value-count@t.css:6:32"), DiagCodes.of(e));
    }
}
