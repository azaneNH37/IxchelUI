package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.view.TagRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentDisabledTest {
    static final class Form extends ViewModel {
        int clicks;
        final Value<Boolean> locked = state(true);
        final Action<Void> save = action(v -> clicks++);
    }

    @Test
    void aDisabledElementMatchesDisabledAndItsSubtreeTakesNoClickOrFocusUntilEnabled() {
        Form m = new Form();
        UiDocument doc = UiDocument.load(GoldenTest.page("""
                <link href="t.css"/>
                <div class="b" tabindex="0" bind:disabled="locked" on:click="save"><div class="in"></div></div>
                <div class="other" tabindex="0"></div>
                """), "t.html", GoldenTest.sheet("""
                .b { width: 10px; height: 10px; background-color: #fff }
                .b:disabled { background-color: #888 }
                .in { width: 4px; height: 4px }
                .other { width: 10px; height: 10px }
                """), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        assertEquals("rect 0 0 10 10 #FF888888", GoldenTest.print(doc.frame(100, 100)));
        Element other = (Element) doc.root().children().get(1);

        // A press inside is taken, so the host does not act on it, but runs nothing and focuses nothing.
        assertTrue(doc.events().mousePressed(2, 2, 0, 0));
        assertTrue(doc.events().mouseReleased(2, 2, 0, 0));
        assertEquals(0, m.clicks);
        assertNull(doc.events().focused());
        doc.events().keyPressed(UiEvent.KEY_TAB, 0);
        assertSame(other, doc.events().focused(), "Tab skips the disabled element");

        m.locked.set(false);
        assertEquals("rect 0 0 10 10 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        doc.events().mousePressed(2, 2, 0, 0);
        doc.events().mouseReleased(2, 2, 0, 0);
        assertEquals(1, m.clicks);
        Element b = (Element) doc.root().children().get(0);
        assertSame(b, doc.events().focused());

        m.locked.set(true);
        doc.frame(100, 100);
        assertNull(doc.events().focused(), "focus leaves an element once it is disabled");
    }

    @Test
    void aButtonInsideADisabledDivIsDisabledItselfUntilTheDivIsEnabled() {
        Form m = new Form();
        UiDocument doc = UiDocument.load(GoldenTest.page("""
                <link href="t.css"/>
                <div bind:disabled="locked"><button class="b" on:click="save"></button></div>
                <div class="other" tabindex="0"></div>
                """), "t.html", GoldenTest.sheet("""
                .b { width: 10px; height: 10px; padding: 0; border-style: none; background-color: #fff }
                .b:disabled { background-color: #888 }
                .other { width: 10px; height: 10px }
                """), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        assertEquals("rect 0 0 10 10 #FF888888", GoldenTest.print(doc.frame(100, 100)), "the button matches :disabled");
        Element b = doc.querySelectorAll(".b").get(0);
        assertTrue(b.hasState(Element.DISABLED));

        doc.events().mousePressed(2, 2, 0, 0);
        doc.events().mouseReleased(2, 2, 0, 0);
        assertEquals(0, m.clicks, "a click on it runs nothing");
        doc.events().keyPressed(UiEvent.KEY_TAB, 0);
        assertSame(doc.querySelectorAll(".other").get(0), doc.events().focused(), "Tab skips it");

        m.locked.set(false);
        assertEquals("rect 0 0 10 10 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)), "enabling the div restyles the button");
        doc.events().mousePressed(2, 2, 0, 0);
        doc.events().mouseReleased(2, 2, 0, 0);
        assertEquals(1, m.clicks);
        assertSame(b, doc.events().focused());
    }
}
