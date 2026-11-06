package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.paint.ImageMeasurer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code clip-path}, {@code mask-image} and {@code background-clip}. */
class UiDocumentClipMaskTest {
    /** Every image is 16 by 16. */
    private static final ImageMeasurer SIXTEEN = src -> new ImageMeasurer.Size(16, 16);

    private static UiDocument load(String html, String css, ImageMeasurer images) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), HtmlElements.TAGS, GoldenTest.MONO, images);
    }

    private static Element child(UiDocument doc, int i) {
        return (Element) doc.root().children().get(i);
    }

    /**
     * Fails if a shape resolves against the wrong box or keyword, a clip-path does not open a layer, or
     * the layer is not cut to the shape (a child sticking out would grow it).
     */
    @Test
    void eachBasicShapeResolvesInTheBorderBoxAndCutsItsLayer() {
        UiDocument doc = load("""
                <div class="i"><div class="out"></div></div><div class="c"></div><div class="e"></div><div class="p"></div>""", """
                div { flex-shrink: 0 }
                .i { width: 20px; height: 10px; clip-path: inset(1px 10% 20% round 2px 50%) border-box; background-color: #f00 }
                .out { width: 40px; height: 4px; background-color: #0f0 }
                .c { width: 20px; height: 10px; clip-path: circle(closest-side at 5px 50%); background-color: #f00 }
                .e { width: 20px; height: 10px; clip-path: ellipse(25% farthest-side at right top); background-color: #f00 }
                .p { width: 20px; height: 10px; clip-path: polygon(evenodd, 0 0, 100% 0, 50% 100%); background-color: #f00 }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                layer 1 0 18 9 1 clip 2 1 16 7 radii 2/2 10/5 2/2 10/5
                rect 0 0 20 10 #FFFF0000
                rect 0 0 40 4 #FF00FF00
                unlayer
                layer -1 9 12 12 1 clip 0 10 10 10 radii 5/5 5/5 5/5 5/5
                rect 0 10 20 10 #FFFF0000
                unlayer
                layer 14 19 7 12 1 clip 15 10 10 20 radii 5/10 5/10 5/10 5/10
                rect 0 20 20 10 #FFFF0000
                unlayer
                layer -1 29 22 12 1 clip evenodd 0 30 20 30 10 40
                rect 0 30 20 10 #FFFF0000
                unlayer""", GoldenTest.print(doc.frame(100, 100)));
    }

    /** Fails if hit testing ignores the shape, lets a descendant be hit outside it, or misses the element's transform. */
    @Test
    void clipPathCutsHitTestingForTheElementAndItsDescendants() {
        UiDocument doc = load("<div class=\"c\"><div class=\"kid\"></div></div><div class=\"t\"></div>", """
                div { flex-shrink: 0 }
                .c { width: 20px; height: 20px; clip-path: circle(10px) }
                .kid { width: 40px; height: 10px }
                .t { width: 20px; height: 20px; clip-path: polygon(0 0, 100% 0, 0 100%); transform: translate(30px, 0) }
                """, GoldenTest.NO_IMAGES);
        doc.frame(100, 100);
        Element c = child(doc, 0);
        Element kid = (Element) c.children().get(0);
        Element t = child(doc, 1);
        assertSame(kid, doc.events().hitTest(10, 5), "inside the circle, on the child");
        assertSame(c, doc.events().hitTest(10, 15), "inside the circle, below the child");
        assertSame(doc.root(), doc.events().hitTest(1, 1), "the corner is outside the circle");
        assertSame(doc.root(), doc.events().hitTest(30, 5), "the child sticks out of the shape there");
        assertSame(t, doc.events().hitTest(32, 22), "local (2, 2), in the triangle");
        assertSame(doc.root(), doc.events().hitTest(48, 38), "local (18, 18), past the diagonal");
    }

    /** Fails if same-function shapes do not interpolate, or ones CSS cannot interpolate do not jump. */
    @Test
    void shapesOfTheSameFunctionAndArityInterpolate() {
        long[] now = {0};
        UiDocument doc = load("<div class=\"a\"></div><div class=\"b\"></div><div class=\"k\"></div>", """
                div { flex-shrink: 0; width: 20px; height: 20px; background-color: #f00; transition: clip-path 100ms linear }
                .a { clip-path: inset(0 round 0) }
                .a.on { clip-path: inset(4px 2px round 10px) }
                .b { clip-path: polygon(0 0, 20px 0, 0 20px) }
                .b.on { clip-path: polygon(0 0, 10px 10px, 0 100%) }
                .k { clip-path: circle(closest-side) }
                .k.on { clip-path: circle(8px) }
                """, GoldenTest.NO_IMAGES);
        doc.clock(() -> now[0] * 1_000_000);
        doc.frame(100, 100);
        for (int i = 0; i < 3; i++) {
            child(doc, i).addClass("on");
        }
        doc.frame(100, 100);
        now[0] = 50;
        String painted = GoldenTest.print(doc.frame(100, 100));
        assertEquals(List.of(
                "layer 0 1 20 18 1 clip 1 2 18 16 radii 5/5 5/5 5/5 5/5",
                "layer -1 19 17 22 1 clip polygon 0 20 15 25 0 40",
                "layer 1 41 18 18 1 clip 2 42 16 16 radii 8/8 8/8 8/8 8/8"),
                painted.lines().filter(l -> l.startsWith("layer")).toList(), "a and b halfway; k jumps to its end");
    }

    /**
     * Fails if the mask does not follow the content in the same layer, is not sized and tiled as a
     * background layer in the border box, or an element with only a mask paints no layer.
     */
    @Test
    void maskLayersFollowTheContentInTheBorderBox() {
        UiDocument doc = load("<div class=\"g\">a</div><div class=\"u\"></div>", """
                div { flex-shrink: 0 }
                .g { width: 20px; height: 10px; padding: 2px; border-radius: 3px; background-color: #f00;
                     mask-image: linear-gradient(transparent, #000); mask-mode: alpha; mask-composite: add }
                .u { width: 20px; height: 10px; background-color: #f00; transform: scale(2);
                     mask-image: url(x:icon.png), none; mask-mode: match-source, alpha }
                """, SIXTEEN);
        assertEquals("""
                layer -1 -1 22 12 1 masked
                fill 0 0 20 10 radii 3/3 3/3 3/3 3/3 clip 0 0 20 10 #FFFF0000
                text 2 2 9 #FFFFFFFF "a"
                mask
                fill 0 0 20 10 radii 3/3 3/3 3/3 3/3 clip 0 0 20 10 gradient 10 0 10 10 0:#00000000 1:#FF000000
                unlayer
                layer -11 4 42 22 1 masked
                transform 2 0 0 2 -10 -15
                rect 0 10 20 10 #FFFF0000
                untransform
                mask
                transform 2 0 0 2 -10 -15
                image 0 10 16 10 x:icon.png uv 0 0 1 0.625 #FFFFFFFF
                image 16 10 4 10 x:icon.png uv 0 0 0.25 0.625 #FFFFFFFF
                untransform
                unlayer""", GoldenTest.print(doc.frame(100, 100)));
        assertSame(child(doc, 0), doc.events().hitTest(1, 1), "where the mask is transparent, still hit");
    }

    @Test
    void anUnloadableMaskImageIsReported() {
        UiDocument doc = load("<div class=\"m\"></div>", """
                .m { width: 4px; height: 4px; background-color: #f00; mask-image: url(x:missing.png) }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                layer -1 -1 6 6 1 masked
                rect 0 0 4 4 #FFFF0000
                mask
                unlayer""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(List.of("paint/mask-image-unsized@t.html:2:1"), DiagCodes.of(doc.diagnostics()));
    }

    /**
     * Fails if the background is not masked by the text of the element and its descendants (in their
     * transforms and scroll clips, whatever their color, without shadows), or clipping to the text
     * changes how the text itself paints. Here the flex column puts "c" and "d" on lines of their own.
     */
    @Test
    void backgroundClipTextMasksTheBackgroundWithTheText() {
        UiDocument doc = load("<div class=\"t\">ab<span class=\"s\">c</span><div class=\"n\">d</div></div>", """
                .t { width: 40px; height: 20px; background-image: linear-gradient(90deg, #f00, #00f); background-clip: text;
                     color: transparent; text-shadow: 1px 1px #000 }
                .s { color: #0f0 }
                .n { transform: translate(1px, 0); display: block }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                layer -1 -1 42 22 1 masked
                fill 0 0 40 20 clip 0 0 40 20 gradient 0 10 40 10 0:#FFFF0000 1:#FF0000FF
                mask
                text 0 0 9 #FFFFFFFF "ab"
                text 0 9 9 #FFFFFFFF "c"
                transform 1 0 0 1 1 0
                text 0 18 9 #FFFFFFFF "d"
                untransform
                unlayer
                text 1 1 9 #FF000000 "ab"
                text 1 10 9 #FF000000 "c"
                text 0 9 9 #FF00FF00 "c"
                transform 1 0 0 1 1 0
                text 1 19 9 #FF000000 "d"
                untransform""", GoldenTest.print(doc.frame(100, 100)));
    }

    /** Fails if padding-box or content-box does not shrink the color and the layers to that box. */
    @Test
    void backgroundClipBoxesShrinkTheColorAndLayers() {
        UiDocument doc = load("<div class=\"b\"></div>", """
                .b { width: 20px; height: 20px; border: 2px solid #000; padding: 3px; background-color: #f00;
                     background-image: linear-gradient(#fff, #000), none; background-clip: padding-box, content-box }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                rect 5 5 10 10 #FFFF0000
                fill 2 2 16 16 clip 2 2 16 16 gradient 10 2 10 18 0:#FFFFFFFF 1:#FF000000
                rect 0 0 20 2 #FF000000
                rect 0 18 20 2 #FF000000
                rect 0 2 2 16 #FF000000
                rect 18 2 2 16 #FF000000""", GoldenTest.print(doc.frame(100, 100)));
    }

    /** Unsupported sub-syntax is reported with its code; paint() in a mask also names an effect the page does not link. */
    @Test
    void unsupportedClipAndMaskSyntaxIsALoadErrorWithItsCode() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", """
                .a { clip-path: path("M0 0") }
                .b { clip-path: url(#c) }
                .c { clip-path: circle(4px) padding-box }
                .d { clip-path: border-box }
                .e { clip-path: ellipse(4px) }
                .f { clip-path: inset(1px 2px 3px 4px 5px) }
                .g { mask-image: paint(Glint) }
                .h { mask-mode: luminance }
                .i { mask-composite: subtract }
                .j { background-clip: text, border-box }
                .k { border-image-source: linear-gradient(#000, #fff) }
                .l { clip-path: polygon(0 0, 1px) }
                """, GoldenTest.NO_IMAGES));
        assertEquals(List.of("effect/unknown-alias@t.css:7:24", "css/unsupported-value@t.css:1:30", "css/unsupported-value@t.css:2:17",
                "css/unsupported-value@t.css:3:29", "css/unsupported-value@t.css:4:17", "css/function-arguments@t.css:5:25",
                "css/function-arguments@t.css:6:39", "css/unsupported-value@t.css:7:18", "css/unsupported-value@t.css:8:17",
                "css/unsupported-value@t.css:9:22", "css/unsupported-value@t.css:10:6", "css/unsupported-value@t.css:11:27",
                "css/function-arguments@t.css:12:30"), DiagCodes.of(e));
    }

    @Test
    void aPolygonWithTooManyVerticesIsUnsupported() {
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            points.append(i == 0 ? "" : ", ").append(i).append("px 0");
        }
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>",
                ".a { clip-path: polygon(" + points + ") }\n", GoldenTest.NO_IMAGES));
        assertEquals(1, DiagCodes.of(e).size());
        assertEquals("css/unsupported-value", DiagCodes.of(e).get(0).split("@")[0]);
    }
}
