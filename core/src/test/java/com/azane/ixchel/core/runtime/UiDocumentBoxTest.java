package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.view.Translations;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Rounded corners, {@code box-shadow} and {@code border-image}. */
class UiDocumentBoxTest {
    /** Every image is 16 by 16. */
    private static final ImageMeasurer SIXTEEN = src -> new ImageMeasurer.Size(16, 16);

    private static UiDocument load(String html, String css, ImageMeasurer images) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), HtmlElements.TAGS, GoldenTest.MONO, images);
    }

    @Test
    void roundedCornersTurnTheBackgroundIntoAFillAndTheBorderIntoOneRing() {
        UiDocument doc = load("<div class=\"a\"></div><div class=\"b\"></div>", """
                .a { width: 20px; height: 10px; background-color: #000; border: 2px solid #f00; border-left-color: #0f0;
                     border-radius: 4px 50% }
                .b { width: 20px; height: 10px; border: 2px solid #f00; border-radius: 0 }
                """, GoldenTest.NO_IMAGES);
        // A percentage is of the box's side, then the radii shrink together to fit (CSS overlap rule).
        assertEquals("""
                fill 0 0 20 10 radii 4/4 10/5 4/4 10/5 clip 0 0 20 10 #FF000000
                border 0 0 20 10 radii 4/4 10/5 4/4 10/5 widths 2 2 2 2 #FFFF0000 #FFFF0000 #FFFF0000 #FF00FF00
                rect 0 10 20 2 #FFFF0000
                rect 0 18 20 2 #FFFF0000
                rect 0 12 2 6 #FFFF0000
                rect 18 12 2 6 #FFFF0000""", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void shadowsPaintBottomFirstOuterUnderTheBackgroundInsetOverIt() {
        UiDocument doc = load("<div class=\"s\"></div>", """
                .s { width: 10px; height: 10px; margin: 20px; background-color: #000; border: 1px solid #fff;
                     border-radius: 2px; box-shadow: 1px 2px 4px 3px #f00, inset 0 0 2px #00f, 5px 5px #0f0 }
                """, GoldenTest.NO_IMAGES);
        // The spread grows the 2px radius by less than the spread (3px), so a small corner stays tight.
        assertEquals("""
                shadow 25 25 10 10 radii 2/2 2/2 2/2 2/2 sigma 0 #FF00FF00 clip 20 20 10 10 radii 2/2 2/2 2/2 2/2
                shadow 18 19 16 16 radii 4.888889/4.888889 4.888889/4.888889 4.888889/4.888889 4.888889/4.888889 sigma 2 #FFFF0000 clip 20 20 10 10 radii 2/2 2/2 2/2 2/2
                fill 20 20 10 10 radii 2/2 2/2 2/2 2/2 clip 20 20 10 10 #FF000000
                inset-shadow 21 21 8 8 radii 1/1 1/1 1/1 1/1 sigma 1 #FF0000FF clip 21 21 8 8 radii 1/1 1/1 1/1 1/1
                border 20 20 10 10 radii 2/2 2/2 2/2 2/2 widths 1 1 1 1 #FFFFFFFF #FFFFFFFF #FFFFFFFF #FFFFFFFF""",
                GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void aShadowListTransitionsFromNoneThroughATransparentShadow() {
        long[] nowMs = {0};
        UiDocument doc = load("<div class=\"s\"></div>", """
                .s { width: 10px; height: 10px; transition: box-shadow 100ms linear }
                .s:hover { box-shadow: 2px 4px 4px #f00 }
                """, GoldenTest.NO_IMAGES);
        doc.clock(() -> nowMs[0] * 1_000_000);
        doc.frame(100, 100);
        doc.events().mouseMoved(5, 5);
        doc.frame(100, 100);
        nowMs[0] = 50;
        assertEquals("shadow 1 2 10 10 sigma 1 #80FF0000 clip 0 0 10 10",
                GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void borderImageReplacesTheBorderCornersStretchedEdgesRepeatedFromTheCenter() {
        UiDocument doc = load("<div class=\"f\"></div>", """
                .f { width: 32px; height: 14px; border: 4px solid #f00; border-image-source: url(x:frame.png);
                     border-image-slice: 4 fill; border-image-repeat: repeat stretch; image-rendering: pixelated }
                """, SIXTEEN);
        // The 8px edge slices tile the 24px top edge exactly; the 6px-high side edges stretch.
        assertEquals("""
                image 0 0 4 4 x:frame.png uv 0 0 0.25 0.25 #FFFFFFFF nearest
                image 28 0 4 4 x:frame.png uv 0.75 0 1 0.25 #FFFFFFFF nearest
                image 28 10 4 4 x:frame.png uv 0.75 0.75 1 1 #FFFFFFFF nearest
                image 0 10 4 4 x:frame.png uv 0 0.75 0.25 1 #FFFFFFFF nearest
                image 4 0 8 4 x:frame.png uv 0.25 0 0.75 0.25 #FFFFFFFF nearest
                image 12 0 8 4 x:frame.png uv 0.25 0 0.75 0.25 #FFFFFFFF nearest
                image 20 0 8 4 x:frame.png uv 0.25 0 0.75 0.25 #FFFFFFFF nearest
                image 4 10 8 4 x:frame.png uv 0.25 0.75 0.75 1 #FFFFFFFF nearest
                image 12 10 8 4 x:frame.png uv 0.25 0.75 0.75 1 #FFFFFFFF nearest
                image 20 10 8 4 x:frame.png uv 0.25 0.75 0.75 1 #FFFFFFFF nearest
                image 0 4 4 6 x:frame.png uv 0 0.25 0.25 0.75 #FFFFFFFF nearest
                image 28 4 4 6 x:frame.png uv 0.75 0.25 1 0.75 #FFFFFFFF nearest
                image 4 4 8 6 x:frame.png uv 0.25 0.25 0.75 0.75 #FFFFFFFF nearest
                image 12 4 8 6 x:frame.png uv 0.25 0.25 0.75 0.75 #FFFFFFFF nearest
                image 20 4 8 6 x:frame.png uv 0.25 0.25 0.75 0.75 #FFFFFFFF nearest""", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void borderImageRepeatCentersPartialTilesAndRoundFitsWholeOnes() {
        UiDocument doc = load("<div class=\"r\"></div><div class=\"o\"></div>", """
                div { width: 26px; height: 16px; border: 4px solid; border-image: url(x:frame.png) 4 }
                .r { border-image-repeat: repeat }
                .o { border-image-repeat: round }
                """, SIXTEEN);
        String[] lines = GoldenTest.print(doc.frame(100, 100)).split("\n");
        // 18px of top edge in 8px tiles, centered: 3px cut from each side tile.
        assertEquals(List.of(
                "image 4 0 5 4 x:frame.png uv 0.4375 0 0.75 0.25 #FFFFFFFF",
                "image 9 0 8 4 x:frame.png uv 0.25 0 0.75 0.25 #FFFFFFFF",
                "image 17 0 5 4 x:frame.png uv 0.25 0 0.5625 0.25 #FFFFFFFF"), List.of(lines).subList(4, 7));
        // Round: 18 / 8 rounds to 2 tiles of 9px.
        assertEquals(List.of(
                "image 4 16 9 4 x:frame.png uv 0.25 0 0.75 0.25 #FFFFFFFF",
                "image 13 16 9 4 x:frame.png uv 0.25 0 0.75 0.25 #FFFFFFFF"),
                List.of(lines).stream().filter(l -> l.contains(" 16 9 4 ")).toList());
    }

    @Test
    void anUnloadableBorderImageDrawsTheBorderAndIsReported() {
        UiDocument doc = load("<div class=\"f\"></div>", """
                .f { width: 4px; height: 4px; border: 1px solid #f00; border-image-source: url(x:missing.png) }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                rect 0 0 4 1 #FFFF0000
                rect 0 3 4 1 #FFFF0000
                rect 0 1 1 2 #FFFF0000
                rect 3 1 1 2 #FFFF0000""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(List.of("paint/border-image-unsized@t.html:2:1"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void roundedCornersOnAClippingElementAreALintWarning() {
        var diag = UiDocument.lint(GoldenTest.linked("<div class=\"c\"></div><div class=\"v\"></div>"), "t.html", GoldenTest.sheet("""
                .c { overflow: hidden; border-radius: 3px }
                .v { border-radius: 3px }
                """), HtmlElements.TAGS, Translations.NONE, null, Set.of());
        assertEquals(List.of("paint/rounded-overflow-clip@t.html:2:1"), DiagCodes.of(diag));
        assertEquals(0, diag.errorCount());
    }
}
