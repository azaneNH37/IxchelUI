package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentTooltipTest {
    static final class Model extends ViewModel {
        final Value<Boolean> shown = state(true);
        final Value<String> hint = state("hint");
    }

    private static UiDocument load(Model m) {
        return UiDocument.load(GoldenTest.linked("""
                <div class="list"><div class="pad"></div>
                <if test="shown"><button class="b">go<ix-tooltip class="tip"><span bind:text="hint"></span></ix-tooltip></button></if></div>
                <div class="after"></div>
                """), "t.html", GoldenTest.sheet(".list { height: 30px; overflow: auto } .pad { height: 18px; flex-shrink: 0 }"
                + " .b { padding: 0; border-style: none; height: 9px } .after { height: 40px }"), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
    }

    private static Element one(UiDocument doc, String selector) {
        List<Element> found = doc.querySelectorAll(selector);
        assertEquals(1, found.size(), selector);
        return found.get(0);
    }

    @Test
    void theTooltipPaintsOutsideItsScrollClipInItsOwnListAndIsNeverHit() {
        Model m = new Model();
        UiDocument doc = load(m);
        doc.frame(100, 100);
        Element tip = one(doc, ".tip");
        String document = GoldenTest.print(doc.frame(100, 100));

        doc.setTooltip(tip, 10, 60);
        doc.frame(100, 100);
        assertEquals(18, tip.box().x());
        assertEquals(44, tip.box().y(), "placed at the pointer, below the 30px clip of its scroll container");
        assertEquals(document, GoldenTest.print(doc.frame(100, 100)), "the document paints as before");
        String painted = GoldenTest.print(doc.atPointerDisplayList());
        assertTrue(painted.contains("\"hint\"") && !painted.contains("clip"), "the tooltip paints alone, unclipped:\n" + painted);
        assertSame(one(doc, ".after"), doc.events().hitTest(20, 48), "the pointer goes through the tooltip");

        int runs = doc.pipelineRuns;
        long version = doc.atPointerDisplayList().version();
        doc.setTooltip(tip, 12, 60);
        doc.frame(100, 100);
        assertEquals(20, tip.box().x());
        assertEquals(runs, doc.pipelineRuns, "moving the tooltip runs neither style nor layout");
        assertFalse(version == doc.atPointerDisplayList().version(), "but repaints it");

        m.hint.set("longer hint");
        doc.frame(100, 100);
        assertTrue(GoldenTest.print(doc.atPointerDisplayList()).contains("\"longer hint\""), "bound content updates while shown");

        m.shown.set(false);
        doc.frame(100, 100);
        assertNull(doc.topLayer().tooltip(), "removing the tooltip's element hides it");
        assertEquals("", GoldenTest.print(doc.atPointerDisplayList()));
    }
}
