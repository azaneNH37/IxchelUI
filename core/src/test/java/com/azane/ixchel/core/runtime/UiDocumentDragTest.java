package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentDragTest {
    static final class Board extends ViewModel {
        final List<String> log = new ArrayList<>();
        final Value<Boolean> shown = state(true);
        final Action<String> note = action(log::add);
    }

    /** Two 20px rows: a draggable source with a preview at y 0..20, a drop target at y 20..40. */
    private static UiDocument load(Board m) {
        UiDocument doc = UiDocument.load(GoldenTest.linked("""
                <if test="shown"><div class="src" draggable="true" on:click="note('click')" on:dragstart="note('start')"
                     on:drag="note('drag ' + $event.movementX)" on:dragend="note('end')" on:mouseup="note('up')">
                  <span>s</span><ix-drag-preview class="ghost"><span>g</span></ix-drag-preview></div></if>
                <div class="dst" on:drop="note('drop')"></div>
                """), "t.html", GoldenTest.sheet(".src, .dst { height: 20px; flex-shrink: 0 } .ghost { width: 10px; height: 10px }"), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        doc.frame(100, 100);
        return doc;
    }

    @Test
    void aPressThatStaysWithinTheThresholdIsAClickAndOneThatLeavesItIsADrag() {
        Board m = new Board();
        UiDocument doc = load(m);
        EventDispatcher ev = doc.events();
        ev.mousePressed(5, 5, 0, 0);
        ev.mouseMoved(7, 6);
        ev.mouseReleased(7, 6, 0, 0);
        assertEquals(List.of("up", "click"), m.log, "3px of |dx| + |dy| is still a click");

        m.log.clear();
        ev.mousePressed(5, 5, 0, 0);
        ev.mouseMoved(9, 5);
        assertSame(one(doc, ".src"), ev.dragSource());
        ev.mouseMoved(9, 25);
        assertTrue(one(doc, ".dst").hasState(Element.HOVER), "hover follows the pointer, marking the drop target");
        doc.frame(100, 100);
        Element ghost = one(doc, ".ghost");
        assertSame(ghost, doc.topLayer().dragPreview());
        assertEquals(4, ghost.box().x(), "the preview is centered on the pointer");
        assertEquals(20, ghost.box().y());
        assertSame(one(doc, ".dst"), ev.hitTest(9, 25), "the preview is never hit");
        assertTrue(GoldenTest.print(doc.atPointerDisplayList()).contains("\"g\""));
        ev.mouseReleased(9, 25, 0, 0);
        assertEquals(List.of("start", "drag 4.0", "drag 0.0", "drop", "end"), m.log,
                "no click or mouseup once dragging; drop under the pointer, then dragend last");
        assertNull(ev.dragSource());
        doc.frame(100, 100);
        assertNull(doc.topLayer().atPointer(), "the preview is gone");
    }

    @Test
    void escapeCancelsWithoutADropAndARemovedSourceStillGetsItsDragend() {
        Board m = new Board();
        UiDocument doc = load(m);
        EventDispatcher ev = doc.events();
        ev.mousePressed(5, 5, 0, 0);
        ev.mouseMoved(5, 25);
        ev.keyPressed(Keys.code("escape"), 0);
        ev.mouseReleased(5, 25, 0, 0);
        assertEquals(List.of("start", "drag 0.0", "end"), m.log, "Escape: no drop, and the release does nothing");

        m.log.clear();
        ev.mousePressed(5, 5, 0, 0);
        ev.mouseMoved(5, 25);
        m.shown.set(false);
        doc.frame(100, 100);
        assertNull(ev.dragSource(), "the source left the tree");
        ev.mouseReleased(5, 25, 0, 0);
        assertEquals(List.of("start", "drag 0.0", "end"), m.log);
        assertNull(doc.topLayer().atPointer());
    }

    private static Element one(UiDocument doc, String selector) {
        List<Element> found = doc.querySelectorAll(selector);
        assertEquals(1, found.size(), selector);
        return found.get(0);
    }
}
