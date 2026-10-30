package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentScrollTest {
    /** Five 10px rows in a 30px list: 20px of scroll range. */
    private static final String LIST = """
            <div id="list"><div id="r0">a</div><div id="r1">b</div><div id="r2">c</div><div id="r3">d</div><div id="r4">e</div></div>
            """;
    private static final String LIST_CSS = """
            #list { width: 50px; height: 30px; overflow: auto }
            #list > div { height: 10px; flex-shrink: 0 }
            """;

    private static UiDocument load(String html, String css) {
        UiDocument doc = UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css),
                TagRegistry.HTML, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        doc.frame(100, 100);
        return doc;
    }

    private static Element byId(Element e, String id) {
        if (id.equals(e.attribute("id"))) {
            return e;
        }
        for (Node n : e.children()) {
            if (n instanceof Element c) {
                Element f = byId(c, id);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static void style(Element e, String declarations) {
        e.setInlineStyle(CssParser.parseDeclarations(declarations, new SourcePos("t.html", 1, 1), new Diagnostics()));
    }

    @Test
    void scrolledContentIsClippedAndPaintedHigherWithAThumbWhileItOverflows() {
        UiDocument doc = load(LIST, LIST_CSS);
        Element list = byId(doc.root(), "list");
        assertEquals(20, list.maxScrollTop());
        assertEquals("""
                clip 0 0 50 30
                text 0 0 9 #FFFFFFFF "a"
                text 0 10 9 #FFFFFFFF "b"
                text 0 20 9 #FFFFFFFF "c"
                text 0 30 9 #FFFFFFFF "d"
                text 0 40 9 #FFFFFFFF "e"
                rect 46 0 4 18 #FFA0A0A0
                unclip""", GoldenTest.print(doc.frame(100, 100)));

        list.setScrollTop(18);
        assertEquals("""
                clip 0 0 50 30
                text 0 -18 9 #FFFFFFFF "a"
                text 0 -8 9 #FFFFFFFF "b"
                text 0 2 9 #FFFFFFFF "c"
                text 0 12 9 #FFFFFFFF "d"
                text 0 22 9 #FFFFFFFF "e"
                rect 46 11 4 18 #FFA0A0A0
                unclip""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(10, byId(doc.root(), "r1").box().y(), "layout boxes stay unscrolled");

        style(list, "height: 60px");
        assertEquals("""
                clip 0 0 50 60
                text 0 0 9 #FFFFFFFF "a"
                text 0 10 9 #FFFFFFFF "b"
                text 0 20 9 #FFFFFFFF "c"
                text 0 30 9 #FFFFFFFF "d"
                text 0 40 9 #FFFFFFFF "e"
                unclip""", GoldenTest.print(doc.frame(100, 100)), "auto: no thumb once everything fits");
        assertEquals(0, list.scrollTop(), "a grown box clamps the offset");
    }

    @Test
    void overflowScrollAlwaysDrawsTrackAndThumbAndHiddenDrawsNone() {
        UiDocument doc = load("<div id=\"s\"></div><div id=\"h\"><div></div></div>", """
                div { flex-shrink: 0 }
                #s { width: 20px; height: 10px; overflow: scroll }
                #h { width: 20px; height: 10px; overflow: hidden }
                #h > div { height: 30px }
                """);
        assertEquals("""
                clip 0 0 20 10
                rect 16 0 4 10 #80000000
                rect 16 0 4 10 #FFA0A0A0
                unclip
                clip 0 10 20 10
                unclip""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(20, byId(doc.root(), "h").maxScrollTop(), "hidden can still be scrolled by the host");
    }

    @Test
    void aScrollContainerIsNotSizedByItsContent() {
        String html = "<div id=\"head\"></div><div id=\"list\"><div></div><div></div><div></div></div>";
        String rows = """
                #head { height: 20px; flex-shrink: 0 }
                #list { flex-grow: 1; padding-bottom: 5px }
                #list > div { height: 50px; flex-shrink: 0 }
                """;
        UiDocument visible = load(html, rows);
        assertEquals(155, byId(visible.root(), "list").box().height(), "visible: min-height is the content");

        UiDocument scroll = load(html, rows + "#list { overflow: auto }");
        Element list = byId(scroll.root(), "list");
        assertEquals(80, list.box().height());
        assertEquals(75, list.maxScrollTop(), "rows plus the bottom padding, minus the box");
    }

    @Test
    void scrollRangeCoversNestedAndAbsoluteContentButNotDisplayNone() {
        UiDocument doc = load("""
                <div id="list"><div id="wrap"><div id="deep"></div></div><div id="abs"></div><div id="gone"></div></div>
                """, """
                #list { width: 50px; height: 30px; overflow: auto }
                #wrap { height: 10px; flex-shrink: 0 }
                #deep { height: 40px; flex-shrink: 0 }
                #abs { position: absolute; top: 45px; height: 5px; width: 5px }
                #gone { height: 500px; flex-shrink: 0; display: none }
                """);
        assertEquals(20, byId(doc.root(), "list").maxScrollTop(), "abs reaches 50; deep overflows wrap to 40");
    }

    @Test
    void hitTestingAndHoverFollowTheScrolledContent() {
        UiDocument doc = load(LIST, LIST_CSS);
        EventDispatcher ev = doc.events();
        Element root = doc.root();
        assertSame(root, ev.hitTest(5, 35), "row d sticks out below the list but is clipped");

        ev.mouseMoved(5, 5);
        assertTrue(byId(root, "r0").hasState(Element.HOVER));
        assertTrue(ev.mouseScrolled(5, 5, -1));
        assertEquals(18, byId(root, "list").scrollTop());
        assertEquals("r2", ev.hitTest(5, 5).attribute("id"));
        assertTrue(byId(root, "r2").hasState(Element.HOVER), "hover moved with the content");
        assertFalse(byId(root, "r0").hasState(Element.HOVER));
        assertEquals("r4", ev.hitTest(5, 25).attribute("id"));
    }

    @Test
    void wheelStopsAtTheLimitsAndIsLeftToTheHostOutsideTheUi() {
        UiDocument doc = load(LIST, LIST_CSS);
        EventDispatcher ev = doc.events();
        Element list = byId(doc.root(), "list");
        assertTrue(ev.mouseScrolled(5, 5, 1), "over an element, even if nothing can scroll up");
        assertEquals(0, list.scrollTop());
        ev.mouseScrolled(5, 5, -1);
        ev.mouseScrolled(5, 5, -1);
        assertEquals(20, list.scrollTop(), "clamped to the range");
        ev.mouseScrolled(5, 5, 0.5);
        assertEquals(11, list.scrollTop(), "half a unit, rounded to whole pixels");
        assertFalse(ev.mouseScrolled(80, 80, -1));
        assertEquals(11, list.scrollTop());
    }

    @Test
    void wheelChainsToTheOuterContainerAtTheInnerLimitAndSkipsHidden() {
        UiDocument doc = load("""
                <div id="outer"><div id="clip"><div id="inner"><div class="row"></div><div class="row"></div></div></div><div class="row"></div><div class="row"></div></div>
                """, """
                div { flex-shrink: 0 }
                #outer { width: 50px; height: 40px; overflow: scroll }
                #clip { height: 20px; overflow: hidden }
                #inner { height: 20px; overflow: auto }
                .row { height: 20px }
                """);
        EventDispatcher ev = doc.events();
        Element outer = byId(doc.root(), "outer");
        Element inner = byId(doc.root(), "inner");
        assertEquals(20, inner.maxScrollTop());
        assertEquals(20, outer.maxScrollTop());

        ev.mouseScrolled(5, 5, -1);
        assertEquals(18, inner.scrollTop());
        ev.mouseScrolled(5, 5, -1);
        assertEquals(20, inner.scrollTop());
        assertEquals(0, outer.scrollTop(), "the inner one could still move by 2px");
        ev.mouseScrolled(5, 5, -1);
        assertEquals(18, outer.scrollTop(), "inner at its limit: outer scrolls; hidden #clip is skipped");
        assertEquals(0, byId(doc.root(), "clip").scrollTop());

        ev.mouseScrolled(5, 25, 1);
        assertEquals(0, outer.scrollTop(), "pointer now over #outer's own row below #clip");
        assertEquals(20, inner.scrollTop());
    }

    static final class Rows extends ViewModel {
        final Value<List<String>> rows = state(List.of("a", "b", "c", "d", "e"));
        final Action<Void> swallow = action(() -> {
        });
    }

    private static UiDocument loadRows(Rows m) {
        UiDocument doc = UiDocument.load("""
                <link rel="stylesheet" href="t.css"/>
                <div id="list" on:wheel="swallow"><for each="rows" as="r" key="r"><div bind:text="r"></div></for></div><div id="free"></div>
                """, "t.html", GoldenTest.sheet(LIST_CSS + "#free { height: 10px }"),
                TagRegistry.HTML, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        doc.frame(100, 100);
        return doc;
    }

    @Test
    void aHandlerThatConsumesTheWheelPreventsScrolling() {
        UiDocument doc = loadRows(new Rows());
        Element list = byId(doc.root(), "list");
        assertTrue(doc.events().mouseScrolled(5, 5, -1));
        assertEquals(0, list.scrollTop());
    }

    @Test
    void keyedEachUpdatesKeepTheOffsetUntilTheContentShrinksBelowIt() {
        Rows m = new Rows();
        UiDocument doc = loadRows(m);
        Element list = byId(doc.root(), "list");
        list.setScrollTop(15);
        doc.frame(100, 100);

        m.rows.set(List.of("z", "e", "d", "c", "b", "a"));
        doc.frame(100, 100);
        assertEquals(30, list.maxScrollTop());
        assertEquals(15, list.scrollTop(), "reordered and added rows keep the offset");

        m.rows.set(List.of("a", "b", "c", "d"));
        doc.frame(100, 100);
        assertEquals(10, list.scrollTop(), "clamped to the shrunk range");
        assertEquals("""
                clip 0 0 50 30
                text 0 -10 9 #FFFFFFFF "a"
                text 0 0 9 #FFFFFFFF "b"
                text 0 10 9 #FFFFFFFF "c"
                text 0 20 9 #FFFFFFFF "d"
                rect 46 7 4 23 #FFA0A0A0
                unclip""", GoldenTest.print(doc.frame(100, 100)));

        m.rows.set(List.of());
        doc.frame(100, 100);
        assertEquals(0, list.scrollTop());
    }

    @Test
    void scrollingRepaintsWithoutRestyleOrRelayout() {
        UiDocument doc = load(LIST, LIST_CSS);
        Element list = byId(doc.root(), "list");
        int runs = doc.pipelineRuns;
        long version = doc.frame(100, 100).version();

        list.setScrollTop(5);
        assertNotEquals(version, doc.frame(100, 100).version());
        assertEquals(runs, doc.pipelineRuns);
        long scrolled = doc.frame(100, 100).version();
        assertEquals(scrolled, doc.frame(100, 100).version(), "the repaint request is used up");

        list.setScrollTop(5);
        assertEquals(scrolled, doc.frame(100, 100).version(), "an unchanged offset requests nothing");
        assertEquals(runs, doc.pipelineRuns);
    }

    static final class Scrolled extends ViewModel {
        final Value<List<String>> rows = state(List.of("a", "b", "c", "d", "e"));
        final Value<Integer> scroll = state(30);
    }

    @Test
    void bindScrolltopFollowsBothWaysAndWritesBackTheClampedOffset() {
        Scrolled m = new Scrolled();
        UiDocument doc = UiDocument.load(GoldenTest.linked("""
                <div id="list" bind:scrolltop="scroll"><for each="rows" as="r" key="r"><div bind:text="r"></div></for></div>
                """), "t.html", GoldenTest.sheet(LIST_CSS), TagRegistry.HTML, GoldenTest.MONO, GoldenTest.NO_IMAGES,
                m, Bindings.PLAIN_TEXT);
        doc.frame(100, 100);
        Element list = byId(doc.root(), "list");
        assertEquals(20, list.scrollTop(), "30 is past the 20px range of the first layout");
        assertEquals(20, m.scroll.get(), "the clamped offset is written back");

        doc.events().mouseScrolled(5, 5, 1);
        assertEquals(2, m.scroll.get(), "a wheel scroll writes back at once");

        m.rows.set(List.of("a", "b", "c", "d", "e", "f", "g", "h"));
        m.scroll.set(45);
        doc.frame(100, 100);
        assertEquals(45, list.scrollTop(), "the offset is clamped against content bound in the same frame");
        assertEquals(45, m.scroll.get());
    }
}
