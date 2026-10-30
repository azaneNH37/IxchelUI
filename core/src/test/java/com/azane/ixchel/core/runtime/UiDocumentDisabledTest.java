package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.UiEvent;
import com.azane.ixchel.core.template.TagRegistry;
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
        UiDocument doc = UiDocument.load("""
                <link rel="stylesheet" href="t.css"/>
                <div class="b" tabindex="0" bind:disabled="locked" on:click="save"><div class="in"></div></div>
                <div class="other" tabindex="0"></div>
                """, "t.html", GoldenTest.sheet("""
                .b { width: 10px; height: 10px; background-color: #fff }
                .b:disabled { background-color: #888 }
                .in { width: 4px; height: 4px }
                .other { width: 10px; height: 10px }
                """), TagRegistry.HTML, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
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
}
