package com.azane.ixchel.core.event;

import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.core.text.TextMeasurer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventDispatcherTest {
    private static final TextMeasurer MONO = new TextMeasurer() {
        @Override
        public boolean hasFont(String font) {
            return true;
        }

        @Override
        public float width(String text, float size, boolean bold, String font) {
            return text.length() * 6 * size / 9;
        }

        @Override
        public float lineHeight(float size) {
            return size;
        }

        @Override
        public float baseline(float size) {
            return size * 7 / 9;
        }
    };

    /** a: 0..40 square; b overlaps its right half and paints later; c is clipped by a; d is hidden. */
    private static final String HTML = """
            <link href="t.css"/>
            <div id="a" tabindex="0"><div id="c"></div></div>
            <div id="b" tabindex="0"><div id="b1"></div><div id="b2"></div></div>
            <div id="d"></div>
            """;
    private static final String CSS = """
            @stylesheet;
            div { position: absolute }
            #a { left: 0; top: 0; width: 40px; height: 40px; overflow: hidden }
            #c { left: 30px; top: 0; width: 30px; height: 10px }
            #b { left: 20px; top: 0; width: 40px; height: 40px; flex-direction: row }
            #b1 { left: 0; top: 0; width: 10px; height: 10px }
            #b2 { left: 10px; top: 0; width: 10px; height: 10px }
            #d { left: 0; top: 50px; width: 10px; height: 10px; display: none }
            """;

    private static UiDocument load() {
        UiDocument doc = UiDocument.load(HTML.replace("/>\n", "/><page>\n") + "</page>", "t.html", Map.of("t.css", CSS)::get, com.azane.ixchel.core.html.HtmlElements.TAGS, MONO, src -> null);
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

    @Test
    void hitTestFollowsPaintOrderClippingAndDisplayNone() {
        UiDocument doc = load();
        EventDispatcher ev = doc.events();
        Element root = doc.root();
        assertEquals("a", ev.hitTest(5, 5).attribute("id"));
        assertEquals("b1", ev.hitTest(25, 5).attribute("id"), "later sibling paints on top");
        assertEquals("b", ev.hitTest(25, 30).attribute("id"));
        // c sticks out of a at x 40..60, but a clips it; b is underneath there.
        assertEquals("b", ev.hitTest(45, 20).attribute("id"));
        assertSame(root, ev.hitTest(5, 55), "display: none is not hit");
        assertSame(root, ev.hitTest(90, 90));
        assertFalse(ev.mousePressed(90, 90, 0, 0), "a press on nothing is left to the host");
    }

    @Test
    void listenersRunCaptureTargetBubbleAndStopPropagationEndsTheWalk() {
        UiDocument doc = load();
        Element b = byId(doc.root(), "b");
        Element b1 = byId(doc.root(), "b1");
        List<String> log = new ArrayList<>();
        b.addEventListener(UiEvent.Type.MOUSE_DOWN, true, e -> log.add("b capture " + e.phase()));
        b.addEventListener(UiEvent.Type.MOUSE_DOWN, false, e -> log.add("b bubble " + e.phase()));
        b1.addEventListener(UiEvent.Type.MOUSE_DOWN, false, e -> log.add("b1 " + e.phase()));
        b1.addEventListener(UiEvent.Type.MOUSE_UP, false, e -> {
            log.add("b1 up");
            e.stopPropagation();
        });
        b.addEventListener(UiEvent.Type.MOUSE_UP, false, e -> log.add("b up"));

        assertTrue(doc.events().mousePressed(25, 5, 0, 0));
        doc.events().mouseReleased(25, 5, 0, 0);
        assertEquals(List.of("b capture CAPTURE", "b1 TARGET", "b bubble BUBBLE", "b1 up"), log);
    }

    @Test
    void clickGoesToTheCommonAncestorOfPressAndRelease() {
        UiDocument doc = load();
        List<String> clicks = new ArrayList<>();
        for (String id : List.of("a", "b", "b1", "b2")) {
            byId(doc.root(), id).addEventListener(UiEvent.Type.CLICK, false,
                    e -> clicks.add(id + "<-" + e.target().attribute("id")));
        }
        EventDispatcher ev = doc.events();
        ev.mousePressed(25, 5, 0, 0);
        ev.mouseReleased(35, 5, 0, 0);
        assertEquals(List.of("b<-b"), clicks, "press on b1, release on b2 clicks their parent");

        clicks.clear();
        ev.mousePressed(25, 5, 1, 0);
        ev.mouseReleased(25, 5, 0, 0);
        assertEquals(List.of(), clicks, "release of a different button is no click");

        ev.mousePressed(5, 5, 0, 0);
        ev.mouseReleased(25, 5, 0, 0);
        assertEquals(List.of(), clicks, "a and b1 only share the root");
    }

    @Test
    void hoverAndActiveCoverAncestorsAndFollowLayoutUnderAStillPointer() {
        UiDocument doc = load();
        Element b = byId(doc.root(), "b");
        Element b1 = byId(doc.root(), "b1");
        EventDispatcher ev = doc.events();
        ev.mousePressed(25, 5, 0, 0);
        assertTrue(b1.hasState(Element.HOVER) && b.hasState(Element.HOVER));
        assertTrue(b1.hasState(Element.ACTIVE) && b.hasState(Element.ACTIVE));
        ev.mouseReleased(25, 5, 0, 0);
        assertFalse(b1.hasState(Element.ACTIVE) || b.hasState(Element.ACTIVE));

        b.setInlineStyle(CssParser.parseDeclarations("left: 60px", new SourcePos("t.html", 1, 1), new Diagnostics()));
        doc.frame(100, 100);
        assertFalse(b1.hasState(Element.HOVER), "b moved away from the pointer");
        assertTrue(byId(doc.root(), "a").hasState(Element.HOVER), "a is uncovered");
    }

    @Test
    void focusFollowsPressesTabAndLeavesDetachedElements() {
        UiDocument doc = load();
        Element a = byId(doc.root(), "a");
        Element b = byId(doc.root(), "b");
        EventDispatcher ev = doc.events();
        ev.mousePressed(25, 5, 0, 0);
        assertSame(b, ev.focused(), "pressing b1 focuses its focusable ancestor");
        assertTrue(b.hasState(Element.FOCUS));

        List<Integer> keys = new ArrayList<>();
        b.addEventListener(UiEvent.Type.KEY_DOWN, false, e -> {
            keys.add(e.key());
            e.consume();
        });
        assertTrue(ev.keyPressed(65, 0));
        assertEquals(List.of(65), keys);

        a.addEventListener(UiEvent.Type.KEY_DOWN, false, e -> { });
        ev.keyPressed(UiEvent.KEY_TAB, UiEvent.MOD_SHIFT);
        assertEquals(List.of(65, UiEvent.KEY_TAB), keys, "b consumed Tab, so focus stays");
        assertSame(b, ev.focused());

        ev.mousePressed(90, 90, 0, 0);
        assertNull(ev.focused(), "pressing nothing clears focus");
        assertTrue(ev.keyPressed(UiEvent.KEY_TAB, 0));
        assertSame(a, ev.focused());
        assertFalse(b.hasState(Element.FOCUS));

        doc.root().removeChild(a);
        doc.frame(100, 100);
        assertNull(ev.focused());
    }
}
