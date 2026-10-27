package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentBackgroundTest {
    private static UiDocument load(String html, String css, ImageMeasurer images) {
        return UiDocument.load(html, "t.html", List.of(new UiDocument.CssSource(css, "t.css")), TagRegistry.HTML,
                GoldenTest.MONO, images);
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
        assertEquals(1, doc.diagnostics().errorCount());
        String d = doc.diagnostics().entries().get(0).toString();
        assertTrue(d.startsWith("t.html:1:1:") && d.contains("cannot load background image 'x:missing.png'"), d);
    }

    @Test
    void aTileTooSmallToRepeatIsReportedInsteadOfStallingTheFrame() {
        UiDocument doc = load("<div class=\"a\"></div>",
                ".a { width: 100px; height: 100px; background-image: url(x:a.png); background-size: 0.5px 0.5px }",
                GoldenTest.NO_IMAGES);
        assertEquals("", frame(doc));
        assertEquals(1, doc.diagnostics().errorCount());
        assertTrue(doc.diagnostics().entries().get(0).message().contains("would repeat 40000 times"),
                doc.diagnostics().entries().get(0).message());
    }

    @Test
    void unsupportedBackgroundValuesAreLoadErrors() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div class=\"a\"></div>", """
                .a { background-image: url("Minecraft:Textures/x.png") }
                .b { background-image: url(x:a.png), url(x:b.png) }
                .c { background-image: linear-gradient(red, blue) }
                .d { background-image: url("") }
                .e { background-size: cover }
                .f { background-size: -1px 2px }
                .g { background-size: 1px 2px 3px }
                .h { background-position: left top }
                .i { background-repeat: space }
                .j { transition: background-position-x 1s }
                """, GoldenTest.NO_IMAGES));
        String m = e.getMessage();
        assertTrue(m.startsWith("10 errors"), m);
        assertTrue(m.contains("t.css:1:28:") && m.contains("invalid resource location 'Minecraft:Textures/x.png'"), m);
        assertTrue(m.contains("multiple background layers are not supported"), m);
        assertTrue(m.contains("expected none or url()"), m);
        assertTrue(m.contains("invalid resource location ''"), m);
        assertTrue(m.contains("'cover' is not supported"), m);
        assertTrue(m.contains("negative value not allowed"), m);
        assertTrue(m.contains("expected 1 or 2 values"), m);
        assertTrue(m.contains("t.css:8:27:") && m.contains("expected a length in px, a percentage"), m);
        assertTrue(m.contains("expected one of repeat, repeat-x, repeat-y, no-repeat"), m);
        assertTrue(m.contains("'background-position-x' cannot be transitioned"), m);
    }
}
