package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.Easing;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentTransitionTest {
    private long nowMs;

    private UiDocument load(String html, String css) {
        UiDocument doc = UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        doc.clock(() -> nowMs * 1_000_000);
        return doc;
    }

    private String frameAt(UiDocument doc, long ms) {
        nowMs = ms;
        return GoldenTest.print(doc.frame(100, 100));
    }

    private static Element first(UiDocument doc) {
        return (Element) doc.root().children().get(0);
    }

    @Test
    void colorTransitionRunsToItsEndThenFramesStopRepainting() {
        UiDocument doc = load("<div class=\"box\"></div>", """
                .box { width: 10px; height: 10px; background-color: #000; transition: background-color 100ms linear }
                .on { background-color: #fff }
                """);
        assertEquals("rect 0 0 10 10 #FF000000", frameAt(doc, 0));
        first(doc).addClass("on");
        assertEquals("rect 0 0 10 10 #FF000000", frameAt(doc, 0));
        assertEquals("rect 0 0 10 10 #FF808080", frameAt(doc, 50));
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 100));
        long version = doc.frame(100, 100).version();
        nowMs = 200;
        assertEquals(version, doc.frame(100, 100).version());
    }

    @Test
    void reversingHalfwayTakesHalfTheTimeBack() {
        UiDocument doc = load("<div class=\"box\"></div>", """
                .box { width: 10px; height: 10px; background-color: #000; transition: background-color 100ms linear }
                .on { background-color: #fff }
                """);
        frameAt(doc, 0);
        first(doc).addClass("on");
        frameAt(doc, 0);
        assertEquals("rect 0 0 10 10 #FF808080", frameAt(doc, 50));
        first(doc).removeClass("on");
        frameAt(doc, 50);
        assertEquals("rect 0 0 10 10 #FF404040", frameAt(doc, 75));
        assertEquals("rect 0 0 10 10 #FF000000", frameAt(doc, 100));
    }

    @Test
    void longhandListsCycleAndDelayHoldsTheStartValue() {
        UiDocument doc = load("<div class=\"box\"></div>", """
                .box { width: 10px; height: 10px; background-color: #fff;
                       transition-property: width, height; transition-duration: 100ms;
                       transition-delay: 0s, 50ms; transition-timing-function: linear }
                .big { width: 20px; height: 20px }
                """);
        frameAt(doc, 0);
        first(doc).addClass("big");
        frameAt(doc, 0);
        assertEquals("rect 0 0 15 10 #FFFFFFFF", frameAt(doc, 50));
        assertEquals("rect 0 0 20 15 #FFFFFFFF", frameAt(doc, 100));
        assertEquals("rect 0 0 20 20 #FFFFFFFF", frameAt(doc, 150));
    }

    @Test
    void percentageAndPixelsInterpolateAsACalculationButAutoChangesAtOnce() {
        UiDocument doc = load("<div class=\"box\"></div>", """
                .box { width: 50%; height: 10px; background-color: #fff; transition: all 100ms linear }
                .px { width: 20px }
                .auto { width: auto; height: 20px }
                """);
        frameAt(doc, 0);
        first(doc).addClass("px");
        frameAt(doc, 0);
        assertEquals("rect 0 0 35 10 #FFFFFFFF", frameAt(doc, 50));
        first(doc).addClass("auto");
        assertEquals("rect 0 0 100 10 #FFFFFFFF", frameAt(doc, 50));
        assertEquals("rect 0 0 100 15 #FFFFFFFF", frameAt(doc, 100));
    }

    static final class Fill extends ViewModel {
        final Value<Integer> width = state(10);
    }

    @Test
    void aBoundValueChangeRunsTheTransition() {
        Fill m = new Fill();
        UiDocument doc = UiDocument.load(GoldenTest.linked("<div class=\"box\" bind:style:width=\"width\"></div>"), "t.html",
                GoldenTest.sheet(".box { height: 10px; background-color: #fff; transition: width 100ms linear }"), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        doc.clock(() -> nowMs * 1_000_000);
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 0));

        m.width.set(30);
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 0));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 50));
        assertEquals("rect 0 0 30 10 #FFFFFFFF", frameAt(doc, 100));
    }

    @Test
    void unsupportedTransitionValuesAreLoadErrors() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div class=\"a\"></div>", """
                .a { transition: display 1s }
                .b { transition-duration: 0 }
                .c { transition-timing-function: steps(2) }
                .d { transition: none, width 1s }
                .e { transition-property: colr }
                """));
        assertEquals(List.of("css/not-animatable@t.css:1:18", "css/missing-unit@t.css:2:27",
                "css/unsupported-value@t.css:3:34", "css/none-not-alone@t.css:4:18", "css/unknown-property@t.css:5:27"),
                DiagCodes.of(e));
    }

    @Test
    void easeMatchesTheCssCurve() {
        assertEquals(0.8024f, Easing.EASE.apply(0.5f), 1e-3f);
        assertEquals(0.3153f, Easing.EASE_IN.apply(0.5f), 1e-3f);
    }
}
