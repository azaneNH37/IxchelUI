package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.view.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentBackgroundTest {
    private static UiDocument load(String html, String css, ImageMeasurer images) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, images);
    }

    private static String frame(UiDocument doc) {
        return GoldenTest.print(doc.frame(100, 100));
    }

    @Test
    void spriteOffsetDrawsOnlyThatRegionOfTheSheetAfterTheBackgroundColor() {
        UiDocument doc = load("<div class=\"heart\"></div>", """
                .heart { width: 9px; height: 9px; background-color: #000;
                         background-image: url("minecraft:textures/gui/icons.png");
                         background-size: 256px 256px; background-position: -52px 0; background-repeat: no-repeat }
                .off { background-image: none }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                rect 0 0 9 9 #FF000000
                image 0 0 9 9 minecraft:textures/gui/icons.png uv 0.203125 0 0.23828125 0.03515625 #FFFFFFFF""",
                frame(doc));

        ((Element) doc.root().children().get(0)).addClass("off");
        assertEquals("rect 0 0 9 9 #FF000000", frame(doc));
    }

    @Test
    void multiplyTintsTheImageWithTheBackgroundColorAndPaintsNoColorUnderIt() {
        UiDocument doc = load("<div class=\"w\"></div><div class=\"e\"></div>", """
                div { width: 10px; height: 10px; background-color: #3F76E480; background-blend-mode: multiply;
                      background-size: 10px 10px }
                .w { background-image: url(x:water.png) }
                """, GoldenTest.NO_IMAGES);
        // The tint is opaque (the color's alpha is dropped); without an image the color paints as usual.
        assertEquals("""
                image 0 0 10 10 x:water.png uv 0 0 1 1 #FF3F76E4
                rect 0 10 10 10 #803F76E4""", frame(doc));
    }

    @Test
    void percentagePositionAlignsThatPointOfTheImageWithThatPointOfTheBox() {
        UiDocument doc = load("""
                <div class="a"></div>
                <div class="b"></div>
                <div class="c"></div>
                """, """
                div { background-image: url(x:a.png); background-repeat: no-repeat }
                .a { width: 40px; height: 20px; background-size: 10px 10px; background-position: 50% 100% }
                .b { width: 10px; height: 10px; background-size: 40px 10px; background-position: 100% 0 }
                .c { width: 20px; height: 20px; background-size: 10px 10px; background-position: 10px }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                image 15 10 10 10 x:a.png uv 0 0 1 1 #FFFFFFFF
                image 0 20 10 10 x:a.png uv 0.75 0 1 1 #FFFFFFFF
                image 10 35 10 10 x:a.png uv 0 0 1 1 #FFFFFFFF""", frame(doc));
    }

    @Test
    void noRepeatDrawsTheImageOnceAndClipsItToTheBox() {
        UiDocument doc = load("""
                <div class="a"></div>
                <div class="b"></div>
                """, """
                div { width: 20px; height: 20px; background-image: url(x:a.png); background-size: 8px 8px;
                      background-repeat: no-repeat }
                .b { background-position: 16px 4px }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                image 0 0 8 8 x:a.png uv 0 0 1 1 #FFFFFFFF
                image 16 24 4 8 x:a.png uv 0 0 0.5 1 #FFFFFFFF""", frame(doc));
    }

    @Test
    void repeatTilesFromThePositionInBothDirectionsAndClipsTheEdgeTiles() {
        UiDocument doc = load("""
                <div class="both"></div>
                <div class="x"></div>
                """, """
                div { width: 20px; height: 10px; background-image: url(x:a.png); background-size: 8px 8px }
                .both { background-position: 2px 0 }
                .x { background-repeat: repeat-x }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                image 0 0 2 8 x:a.png uv 0.75 0 1 1 #FFFFFFFF
                image 2 0 8 8 x:a.png uv 0 0 1 1 #FFFFFFFF
                image 10 0 8 8 x:a.png uv 0 0 1 1 #FFFFFFFF
                image 18 0 2 8 x:a.png uv 0 0 0.25 1 #FFFFFFFF
                image 0 8 2 2 x:a.png uv 0.75 0 1 0.25 #FFFFFFFF
                image 2 8 8 2 x:a.png uv 0 0 1 0.25 #FFFFFFFF
                image 10 8 8 2 x:a.png uv 0 0 1 0.25 #FFFFFFFF
                image 18 8 2 2 x:a.png uv 0 0 0.25 0.25 #FFFFFFFF
                image 0 10 8 8 x:a.png uv 0 0 1 1 #FFFFFFFF
                image 8 10 8 8 x:a.png uv 0 0 1 1 #FFFFFFFF
                image 16 10 4 8 x:a.png uv 0 0 0.5 1 #FFFFFFFF""", frame(doc));
    }

    @Test
    void autoSizeAsksTheHostForTheNaturalSizeAndKeepsItsAspectRatio() {
        List<String> asked = new ArrayList<>();
        ImageMeasurer images = src -> {
            asked.add(src);
            return src.equals("x:tall.png") ? new ImageMeasurer.Size(16, 32) : null;
        };
        UiDocument doc = load("""
                <div class="natural"></div>
                <div class="width"></div>
                <div class="explicit"></div>
                """, """
                div { width: 20px; height: 20px; background-image: url(x:tall.png); background-repeat: no-repeat }
                .width { background-size: 8px }
                .explicit { background-size: 4px 4px; background-image: url(x:other.png) }
                """, images);
        assertEquals("""
                image 0 0 16 20 x:tall.png uv 0 0 1 0.625 #FFFFFFFF
                image 0 20 8 16 x:tall.png uv 0 0 1 1 #FFFFFFFF
                image 0 40 4 4 x:other.png uv 0 0 1 1 #FFFFFFFF""", frame(doc));
        assertEquals(List.of("x:tall.png", "x:tall.png"), asked);
    }

    @Test
    void autoSizeOfAnImageTheHostCannotLoadIsReportedOnceAndDrawsNothing() {
        UiDocument doc = load("<div class=\"a\"></div>",
                ".a { width: 20px; height: 20px; background-image: url(x:missing.png) }", GoldenTest.NO_IMAGES);
        assertEquals("", frame(doc));
        assertEquals("", GoldenTest.print(doc.frame(100, 90)));
        assertEquals(List.of("paint/background-image-unsized@t.html:2:1"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void aTileTooSmallToRepeatIsReportedInsteadOfStallingTheFrame() {
        UiDocument doc = load("<div class=\"a\"></div>",
                ".a { width: 100px; height: 100px; background-image: url(x:a.png); background-size: 0.5px 0.5px }",
                GoldenTest.NO_IMAGES);
        assertEquals("", frame(doc));
        assertEquals(List.of("paint/background-too-many-tiles@t.html:2:1"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void layersPaintBottomFirstAndCycleTheShorterLists() {
        UiDocument doc = load("<div></div>", """
                div { width: 20px; height: 10px; background-color: #000;
                      background-image: url(x:top.png), linear-gradient(to right, #f00, #00f);
                      background-size: 10px 10px; background-repeat: no-repeat, repeat-x }
                """, GoldenTest.NO_IMAGES);
        assertEquals("""
                rect 0 0 20 10 #FF000000
                fill 0 0 20 10 clip 0 0 10 10 gradient 0 5 10 5 0:#FFFF0000 1:#FF0000FF
                fill 0 0 20 10 clip 10 0 10 10 gradient 10 5 20 5 0:#FFFF0000 1:#FF0000FF
                image 0 0 10 10 x:top.png uv 0 0 1 1 #FFFFFFFF""", frame(doc));
    }

    @Test
    void gradientLineReachesTheCornersAndFadesInPremultipliedColor() {
        UiDocument doc = load("<div class=\"corner\"></div><div class=\"fade\"></div>", """
                .corner { width: 20px; height: 10px; background-image: linear-gradient(to top right, #fff, #000) }
                .fade { width: 10px; height: 16px; background-image: linear-gradient(180deg, #f00, transparent 8px) }
                """, GoldenTest.NO_IMAGES);
        String[] lines = frame(doc).split("\n");
        assertEquals("fill 0 0 20 10 clip 0 0 20 10 gradient 6 13 14 -3 0:#FFFFFFFF 1:#FF000000", lines[0]);
        // Red stays red as it fades out instead of darkening through transparent black.
        assertEquals("fill 0 10 10 16 clip 0 10 10 16 gradient 5 10 5 26 0:#FFFF0000 0.0625:#DFFF0000 0.125:#BFFF0000"
                + " 0.1875:#9FFF0000 0.25:#80FF0000 0.3125:#60FF0000 0.375:#40FF0000 0.4375:#20FF0000 0.5:#00000000", lines[1]);
    }

    @Test
    void onlyTheBottomLayerMayMultiply() {
        UiDocument doc = load("<div></div>", """
                div { width: 10px; height: 10px; background-image: url(x:a.png), url(x:b.png);
                      background-size: 10px 10px; background-blend-mode: multiply, normal }
                """, GoldenTest.NO_IMAGES);
        frame(doc);
        assertEquals(List.of("paint/blend-not-bottom@t.html:2:1"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void unsupportedBackgroundValuesAreLoadErrors() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div class=\"a\"></div>", """
                .a { background-image: url("Minecraft:Textures/x.png") }
                .b { background-image: url(x:a.png), radial-gradient(red, blue) }
                .c { background-image: linear-gradient(red) }
                .d { background-image: url("") }
                .e { background-size: cover }
                .f { background-size: -1px 2px }
                .g { background-size: 1px 2px 3px }
                .h { background-position: left top }
                .i { background-repeat: space }
                .j { transition: background-position-x 1s }
                .k { background-image: linear-gradient(red, 50%, blue) }
                .l { background-image: linear-gradient(to left left, red, blue) }
                """, GoldenTest.NO_IMAGES));
        assertEquals(List.of("css/invalid-resource-location@t.css:1:41", "css/unsupported-value@t.css:2:38",
                "css/function-arguments@t.css:3:24", "css/invalid-resource-location@t.css:4:28", "css/unsupported-value@t.css:5:23",
                "css/negative-value@t.css:6:23", "css/value-count@t.css:7:31", "css/invalid-value@t.css:8:27",
                "css/invalid-value@t.css:9:25", "css/not-animatable@t.css:10:18", "css/unsupported-value@t.css:11:45",
                "css/function-arguments@t.css:12:48"), DiagCodes.of(e));
    }
}
