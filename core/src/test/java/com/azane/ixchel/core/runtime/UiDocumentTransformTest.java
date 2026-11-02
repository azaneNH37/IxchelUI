package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentTransformTest {
    private static UiDocument load(String html, String css, ViewModel model) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
    }

    @Test
    void nestedTransformsWrapTheirSubtreesAndThePointerHitsWhereTheyPaint() {
        UiDocument doc = load("<div class=\"a\"><div class=\"b\"></div></div>", """
                .a { width: 20px; height: 10px; background-color: #fff; transform: translate(50%, 2px) scale(2); transform-origin: 0 0 }
                .b { width: 4px; height: 4px; background-color: #000; transform: translateX(3px) }
                """, null);
        assertEquals("""
                transform 2 0 0 2 10 2
                rect 0 0 20 10 #FFFFFFFF
                transform 1 0 0 1 3 0
                rect 0 0 4 4 #FF000000
                untransform
                untransform""", GoldenTest.print(doc.frame(100, 100)));
        Element a = (Element) doc.root().children().get(0);
        Element b = (Element) a.children().get(0);
        assertSame(b, doc.events().hitTest(18, 4), "b's local (1, 1) after both inverses");
        assertSame(a, doc.events().hitTest(12, 4), "left of b once b's translation is undone");
        assertSame(a, doc.events().hitTest(45, 15), "outside a's layout box, inside where it paints");
        assertSame(doc.root(), doc.events().hitTest(5, 5), "inside a's layout box, left of where it paints");
    }

    @Test
    void aRotationTurnsTheHitAreaAndAZeroScaleIsNeverHit() {
        UiDocument doc = load("<div class=\"r\"></div><div class=\"z\"></div>", """
                .r { width: 20px; height: 10px; transform: rotate(0.25turn) }
                .z { width: 20px; height: 10px; transform: scale(0) }
                """, null);
        doc.frame(100, 100);
        Element r = (Element) doc.root().children().get(0);
        assertSame(r, doc.events().hitTest(10, 13), "below the layout box, inside the turned one");
        assertSame(doc.root(), doc.events().hitTest(2, 5), "inside the layout box, outside the turned one");
        assertSame(doc.root(), doc.events().hitTest(10, 16), "in the zero-scaled element's layout box");
    }

    @Test
    void anUnsupportedTransformFunctionIsReportedAtItsPosition() {
        UiLoadException e = assertThrows(UiLoadException.class, () ->
                load("<div class=\"r\"></div>", ".r { transform: rotate(10deg) skew(5deg) }", null));
        assertEquals(List.of("css/unsupported-value@t.css:1:31"), DiagCodes.of(e));
    }

    static final class Pan extends ViewModel {
        final Value<Float> x = state(0f);
    }

    @Test
    void aHeldButtonKeepsMovesAtItsPressTargetAndStatementsReadTheMovement() {
        Pan m = new Pan();
        UiDocument doc = load("<div class=\"pad\" on:mousemove.left=\"x = x + $event.movementX\"></div>",
                ".pad { width: 20px; height: 20px }", m);
        doc.frame(100, 100);
        doc.events().mouseMoved(5, 5);
        doc.events().mouseMoved(8, 5);
        assertEquals(0f, m.x.get(), "no button held");
        doc.events().mousePressed(8, 5, 0, 0);
        doc.events().mouseMoved(15, 5);
        doc.events().mouseMoved(60, 40);
        assertEquals(52f, m.x.get(), "the second move leaves the pad but still goes to it");
        doc.events().mouseReleased(60, 40, 0, 0);
        doc.events().mouseMoved(10, 10);
        assertEquals(52f, m.x.get(), "released");
    }
}
