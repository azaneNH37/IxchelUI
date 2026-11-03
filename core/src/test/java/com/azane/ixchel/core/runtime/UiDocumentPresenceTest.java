package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.html.HtmlElements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code :-ix-absent}, {@code :-ix-entering} and {@code :-ix-exiting} on the document's entering and exiting units. */
class UiDocumentPresenceTest {
    private long nowMs;

    private UiDocument load(String html, String css, ViewModel model) {
        UiDocument doc = UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), HtmlElements.TAGS,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
        doc.clock(() -> nowMs * 1_000_000);
        return doc;
    }

    private void frameAt(UiDocument doc, long ms) {
        nowMs = ms;
        doc.frame(100, 100);
    }

    private static Element child(Element parent, int i) {
        return (Element) parent.children().get(i);
    }

    static final class Shown extends ViewModel {
        final Value<Boolean> shown = state(false);
    }

    @Test
    void thePageEntersFromAbsentAndTheBlocksInsideItDoNotEnterOnTheirOwn() {
        Shown m = new Shown();
        m.shown.set(true);
        UiDocument doc = load("""
                <div class="a"><if test="shown"><div class="in"></div></if></div>
                """, """
                div { height: 10px; flex-shrink: 0 }
                .a { width: 20px; transition: width 100ms linear }
                .a:-ix-absent { width: 0 }
                .in { width: 10px; transition: width 100ms linear }
                .in:-ix-absent { width: 0 }
                """, m);
        Element a = child(doc.root(), 0);
        Element in = child(a, 0);
        frameAt(doc, 0);
        assertEquals(0, a.box().width(), "the first frame starts from absent");
        frameAt(doc, 50);
        assertEquals(10, a.box().width());
        assertTrue(a.hasState(Element.ENTERING));
        assertEquals(10, in.box().width(), "the block inside entered with the page, not on its own");
        assertFalse(in.hasState(Element.ENTERING) || in.hasState(Element.ABSENT));
        frameAt(doc, 100);
        frameAt(doc, 110);
        assertEquals(20, a.box().width());
        assertFalse(a.hasState(Element.ENTERING), "entering ends with the transition");
    }

    @Test
    void anIfCopyEntersExitsInertAndComesBackAsTheSameCopyWhenReversed() {
        Shown m = new Shown();
        UiDocument doc = load("""
                <if test="shown"><div class="b" state:n="0" on:click="n = n + 1"><span bind:text="n"></span></div></if>
                """, """
                .b { width: 40px; height: 10px; transition: width 100ms linear }
                .b:-ix-absent { width: 0 }
                """, m);
        frameAt(doc, 0);
        m.shown.set(true);
        frameAt(doc, 0);
        Element b = child(doc.root(), 0);
        assertEquals(0, b.box().width());
        frameAt(doc, 10);
        frameAt(doc, 60);
        assertEquals(20, b.box().width(), "the transition runs from the absent style");
        frameAt(doc, 110);
        frameAt(doc, 120);
        assertFalse(b.hasState(Element.ENTERING));
        doc.events().mousePressed(1, 1, 0, 0);
        doc.events().mouseReleased(1, 1, 0, 0);
        frameAt(doc, 200);
        assertEquals("1", ((TextNode) child(b, 0).children().get(0)).text());

        m.shown.set(false);
        frameAt(doc, 200);
        assertTrue(b.hasState(Element.EXITING) && b.hasState(Element.ABSENT) && b.isInert());
        assertSame(doc.root(), doc.events().hitTest(1, 1), "an exiting copy is not hit");
        frameAt(doc, 250);
        assertEquals(20, b.box().width());

        m.shown.set(true);
        frameAt(doc, 250);
        assertSame(b, child(doc.root(), 0), "turning true while exiting takes the same copy back");
        assertTrue(b.hasState(Element.ENTERING) && !b.hasState(Element.EXITING) && !b.isInert());
        frameAt(doc, 275);
        assertEquals(30, b.box().width(), "the transition runs back");

        m.shown.set(false);
        frameAt(doc, 400);
        frameAt(doc, 499);
        assertEquals(1, doc.root().children().size());
        frameAt(doc, 500);
        assertEquals(0, doc.root().children().size(), "removed once the transition ended");
    }

    @Test
    void withoutRulesForTheStatesCopiesGoAtOnceAndInfiniteAnimationsAreNotWaitedFor() {
        Shown m = new Shown();
        m.shown.set(true);
        UiDocument plain = load("""
                <if test="shown"><div class="b"></div></if>
                """, """
                .b { height: 10px; transition: width 100ms linear }
                """, m);
        frameAt(plain, 0);
        assertFalse(child(plain.root(), 0).hasState(Element.ABSENT), "no rule uses the states: nothing is absent");
        m.shown.set(false);
        frameAt(plain, 0);
        assertEquals(0, plain.root().children().size());

        Shown n = new Shown();
        n.shown.set(true);
        UiDocument spinning = load("""
                <if test="shown"><div class="spin"></div></if>
                """, """
                @keyframes k { to { width: 10px } }
                .spin:-ix-exiting { animation: k 1s infinite }
                """, n);
        frameAt(spinning, 0);
        n.shown.set(false);
        frameAt(spinning, 0);
        assertEquals(1, spinning.root().children().size());
        frameAt(spinning, 10);
        assertEquals(0, spinning.root().children().size());
    }

    record Row(String id) {
    }

    static final class Rows extends ViewModel {
        final Value<List<Row>> rows = state(List.of(new Row("a"), new Row("b"), new Row("c")));
    }

    @Test
    void anExitingForRowStaysWhereItIsWhileTheShownRowsReorderAndItsKeyTakesItBack() {
        Rows m = new Rows();
        UiDocument doc = load("""
                <div><for each="rows" as="r" key="r.id"><span bind:text="r.id"></span></for><div></div></div>
                """, """
                span { transition: color 100ms linear }
                span:-ix-exiting { color: #123456 }
                """, m);
        frameAt(doc, 0);
        Element list = child(doc.root(), 0);
        Element b = child(list, 1);
        m.rows.set(List.of(new Row("c"), new Row("a")));
        frameAt(doc, 0);
        assertEquals(List.of("c", "a", "b", "div"), texts(list), "b exits in place, c moves to the front");
        assertTrue(b.hasState(Element.EXITING));

        m.rows.set(List.of(new Row("c"), new Row("b"), new Row("a")));
        frameAt(doc, 50);
        assertSame(b, child(list, 1), "the exiting row's key takes its copy back");
        assertEquals(List.of("c", "b", "a", "div"), texts(list));

        m.rows.set(List.of(new Row("c")));
        frameAt(doc, 100);
        frameAt(doc, 199);
        assertEquals(List.of("c", "b", "a", "div"), texts(list));
        frameAt(doc, 200);
        assertEquals(List.of("c", "div"), texts(list), "the rows went once their transitions ended");
    }

    /** The text of each span child, and the tag of every other element. */
    private static List<String> texts(Element parent) {
        return parent.children().stream().map(n -> n instanceof Element e && e.tag().equals("span")
                ? ((TextNode) e.children().get(0)).text() : ((Element) n).tag()).toList();
    }

    static final class Asking extends ViewModel {
        final Value<Boolean> open = state(false);
    }

    @Test
    void aClosedDialogExitsPaintedButNoLongerOpenModalOrHitAndReopeningTakesItBack() {
        Asking m = new Asking();
        UiDocument doc = load("""
                <div class="page"><dialog bind:open="open"><span>sure?</span></dialog></div>
                """, """
                .page { width: 100px; height: 100px }
                dialog { transition: opacity 100ms linear }
                dialog:-ix-absent { opacity: 0 }
                """, m);
        frameAt(doc, 0);
        Element dialog = child(child(doc.root(), 0), 0);
        m.open.set(true);
        frameAt(doc, 0);
        frameAt(doc, 10);
        frameAt(doc, 200);
        assertSame(dialog, doc.topLayer().modal());

        m.open.set(false);
        frameAt(doc, 200);
        assertFalse(doc.topLayer().isOpen(dialog));
        assertNull(doc.topLayer().modal(), "an exiting dialog is not modal");
        assertTrue(doc.topLayer().entries().contains(dialog) && dialog.hasState(Element.OPEN), "it is still laid out and painted");
        assertSame(child(doc.root(), 0), doc.events().hitTest(50, 50));

        m.open.set(true);
        frameAt(doc, 250);
        assertTrue(doc.topLayer().isOpen(dialog) && dialog.hasState(Element.ENTERING));

        m.open.set(false);
        frameAt(doc, 400);
        frameAt(doc, 500);
        assertFalse(doc.topLayer().entries().contains(dialog) || dialog.hasState(Element.OPEN));
    }

    @Test
    void theRemovedSyntaxIsReported() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <if test="true" leave="out"><div></div></if>
                """, """
                @starting-style { div { width: 0 } }
                """, null));
        assertEquals(List.of("template/unknown-block-attribute@t.html:2:17", "css/unsupported-at-rule@t.css:1:1"),
                DiagCodes.of(e));
    }

    @Test
    void theDocumentExitsWithoutBindingsThenEntersBackFromWhereItGotToOrFromAbsent() {
        Shown m = new Shown();
        UiDocument doc = load("""
                <div class="a"><if test="shown"><div class="in"></div></if></div>
                """, """
                .a { width: 20px; height: 10px; transition: width 100ms linear }
                .a:-ix-absent { width: 0 }
                """, m);
        Element a = child(doc.root(), 0);
        frameAt(doc, 0);
        frameAt(doc, 100);
        frameAt(doc, 110);
        assertEquals(20, a.box().width());

        int[] done = {0};
        doc.exit(() -> done[0]++);
        assertTrue(doc.exiting());
        assertTrue(a.isInert());
        frameAt(doc, 110);
        frameAt(doc, 160);
        assertEquals(10, a.box().width());
        m.shown.set(true);
        frameAt(doc, 160);
        assertTrue(a.children().isEmpty(), "bindings stop while it exits");

        doc.enter();
        assertFalse(doc.exiting());
        frameAt(doc, 160);
        assertEquals(1, a.children().size(), "bindings resume when it comes back");
        frameAt(doc, 185);
        assertEquals(15, a.box().width(), "back from where the exit got to");
        frameAt(doc, 210);
        frameAt(doc, 220);
        assertEquals(20, a.box().width());

        doc.exit(() -> done[0]++);
        frameAt(doc, 220);
        frameAt(doc, 320);
        frameAt(doc, 330);
        assertEquals(1, done[0], "only the exit that ended ran its callback");
        assertFalse(doc.exiting());
        assertEquals(0, a.box().width(), "the ended exit stays as it ended");

        // Hidden for a while: the next entry still starts from absent, not from a transition back.
        doc.enter();
        frameAt(doc, 1000);
        assertEquals(0, a.box().width());
        frameAt(doc, 1010);
        frameAt(doc, 1060);
        assertEquals(10, a.box().width());
    }

    @Test
    void withoutRulesForTheStatesTheDocumentExitsAtOnce() {
        UiDocument doc = load("""
                <div class="a"></div>
                """, ".a { width: 20px; height: 10px }", null);
        boolean[] done = {false};
        doc.exit(() -> done[0] = true);
        assertTrue(done[0]);
        assertFalse(doc.exiting());
    }
}
