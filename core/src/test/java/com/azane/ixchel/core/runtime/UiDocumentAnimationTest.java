package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.html.HtmlElements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentAnimationTest {
    private static final String GROW = "@keyframes grow { from { width: 10px } 50% { width: 30px } to { width: 20px } }\n";
    private long nowMs;

    private UiDocument load(String html, String css) {
        UiDocument doc = UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), HtmlElements.TAGS,
                GoldenTest.MONO, GoldenTest.NO_IMAGES);
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
    void keyframesInterpolateFromTheFirstFrameAndFillForwardsThenFramesStop() {
        UiDocument doc = load("<div class=\"box\"></div>", GROW + """
                .box { height: 10px; background-color: #fff; animation: grow 100ms linear forwards }
                """);
        // The document loads at time 0 of a clock that is far ahead by the first frame.
        nowMs = 5000;
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 5000));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 5025));
        assertEquals("rect 0 0 30 10 #FFFFFFFF", frameAt(doc, 5050));
        assertEquals("rect 0 0 25 10 #FFFFFFFF", frameAt(doc, 5075));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 5100));
        long version = doc.frame(100, 100).version();
        nowMs = 5200;
        assertEquals(version, doc.frame(100, 100).version());
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 5300));
    }

    @Test
    void missingEndKeyframesTakeTheCascadedValueAndAKeyframeEasingRunsToTheNextOne() {
        UiDocument doc = load("<div class=\"box\"></div>", """
                @keyframes k { 50% { width: 30px; animation-timing-function: steps(2) } }
                .box { width: 10px; height: 10px; background-color: #fff; animation: k 100ms linear 50ms backwards }
                """);
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 0));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 75));
        assertEquals("rect 0 0 30 10 #FFFFFFFF", frameAt(doc, 110));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 130));
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 150));
    }

    @Test
    void stepsJumpAtTheirPositionsAndIterationsAlternate() {
        UiDocument doc = load("<div class=\"box\"></div>", """
                @keyframes w { from { width: 0 } to { width: 40px } }
                .box { height: 10px; background-color: #fff;
                       animation: w 100ms steps(4, jump-start) 2 alternate forwards }
                """);
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 0));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 30));
        assertEquals("rect 0 0 40 10 #FFFFFFFF", frameAt(doc, 99));
        // The second iteration runs backwards: progress 0.3 back from the end is 0.7, step 3 of 4 with the jump at the start.
        assertEquals("rect 0 0 30 10 #FFFFFFFF", frameAt(doc, 130));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 160));
        assertEquals("rect 0 0 10 10 #FFFFFFFF", frameAt(doc, 200));
    }

    @Test
    void discreteValuesSwitchHalfwayAndImportantDeclarationsWin() {
        UiDocument doc = load("<div class=\"box\"></div>", """
                @keyframes d { from { width: auto } to { width: 20px } }
                .box { height: 10px; background-color: #fff; animation: d 100ms linear infinite }
                .imp { width: 50px !important }
                """);
        frameAt(doc, 0);
        assertEquals("rect 0 0 100 10 #FFFFFFFF", frameAt(doc, 49));
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 51));
        first(doc).addClass("imp");
        assertEquals("rect 0 0 50 10 #FFFFFFFF", frameAt(doc, 60));
    }

    @Test
    void aPausedAnimationHoldsItsTimeAndResumesFromIt() {
        UiDocument doc = load("<div class=\"box\"></div>", GROW + """
                .box { height: 10px; background-color: #fff; animation: grow 100ms linear }
                .paused { animation-play-state: paused }
                """);
        frameAt(doc, 0);
        assertEquals("rect 0 0 20 10 #FFFFFFFF", frameAt(doc, 25));
        first(doc).addClass("paused");
        assertEquals("rect 0 0 22 10 #FFFFFFFF", frameAt(doc, 30));
        long version = doc.frame(100, 100).version();
        nowMs = 500;
        assertEquals(version, doc.frame(100, 100).version());
        first(doc).removeClass("paused");
        assertEquals("rect 0 0 22 10 #FFFFFFFF", frameAt(doc, 500));
        assertEquals("rect 0 0 30 10 #FFFFFFFF", frameAt(doc, 520));
    }

    @Test
    void animationEndBubblesOncePerFiniteAnimationIncludingPseudoElements() {
        UiDocument doc = load("""
                <div state:n="0" on:animationend="n = n + 1"><div class="box"></div><div class="spin"></div><span bind:text="n"></span></div>
                """, GROW + """
                .box { height: 10px; animation: grow 100ms 2 }
                .box::before { content: ""; animation: grow 50ms }
                .spin { height: 10px; animation: grow 10ms infinite }
                """);
        frameAt(doc, 0);
        frameAt(doc, 60);
        assertTrue(frameAt(doc, 70).endsWith("\"1\""), "the ::before animation ended");
        frameAt(doc, 200);
        assertTrue(frameAt(doc, 210).endsWith("\"2\""), "the element's two iterations ended");
        assertTrue(frameAt(doc, 1000).endsWith("\"2\""), "an infinite animation never ends");
    }

    @Test
    void invalidKeyframesAreLoadErrors() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", """
                @keyframes { to { width: 1px } }
                @keyframes k { 120% { width: 1px } to { display: none; width: 1px !important; animation-timing-function: steps(0) } }
                .a { animation: k 1s 2s 3s }
                @media (x) { }
                """));
        assertEquals(List.of("css/invalid-keyframes-name@t.css:1:1", "css/invalid-keyframe-selector@t.css:2:16",
                "css/important-in-keyframe@t.css:2:56", "css/unsupported-at-rule@t.css:4:1",
                "css/repeated-component@t.css:3:25", "css/not-animatable@t.css:2:41",
                "css/function-arguments@t.css:2:112"), DiagCodes.of(e));
    }
}
