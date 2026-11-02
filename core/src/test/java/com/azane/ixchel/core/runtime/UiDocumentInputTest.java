package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.event.Clipboard;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentInputTest {
    static final class Search extends ViewModel {
        final Value<String> query = state("");
    }

    private static UiDocument load(String html, String css, ViewModel model) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
    }

    private static final class Board implements Clipboard {
        String text = "";

        @Override
        public String get() {
            return text;
        }

        @Override
        public void set(String text) {
            this.text = text;
        }
    }

    private static void type(EventDispatcher ev, String s) {
        s.codePoints().forEach(c -> ev.charTyped(c, 0));
    }

    private static final int CTRL = Keys.MOD_CTRL;

    @Test
    void typingEditsTheFieldAndWritesBackThroughBindValueWithinMaxlength() {
        Search m = new Search();
        UiDocument doc = load("<input class=\"i\" bind:value=\"query\" maxlength=\"5\"/>",
                ".i { width: 40px; height: 11px }", m);
        doc.frame(100, 100);
        EventDispatcher ev = doc.events();
        Board board = new Board();
        ev.setClipboard(board);
        assertFalse(ev.charTyped('x', 0), "an unfocused document takes no characters");

        ev.mousePressed(5, 5, 0, 0);
        ev.mouseReleased(5, 5, 0, 0);
        type(ev, "a\u0007b");
        assertEquals("ab", m.query.get(), "typing writes back at once; control characters are dropped");

        assertTrue(ev.keyPressed(Keys.code("a"), CTRL));
        assertTrue(ev.keyPressed(Keys.code("x"), CTRL));
        assertEquals("ab", board.text);
        assertEquals("", m.query.get(), "cut removes the selection");
        for (int i = 0; i < 3; i++) {
            ev.keyPressed(Keys.code("v"), CTRL);
        }
        assertEquals("ababa", m.query.get(), "a paste past maxlength is cut off");
        type(ev, "z");
        assertEquals("ababa", m.query.get(), "a full field takes no more characters");

        ev.keyPressed(Keys.code("left"), Keys.MOD_SHIFT);
        ev.keyPressed(Keys.code("left"), Keys.MOD_SHIFT);
        ev.keyPressed(Keys.code("backspace"), 0);
        assertEquals("aba", m.query.get(), "backspace deletes a Shift-extended selection");
        ev.keyPressed(Keys.code("backspace"), CTRL);
        assertEquals("", m.query.get(), "Ctrl+Backspace deletes the word before the caret");

        assertTrue(ev.keyPressed(Keys.code("e"), 0), "the input claims plain keys, so the inventory key does not close the screen");
        assertFalse(ev.keyPressed(Keys.code("escape"), 0), "Escape is left to the host");

        m.query.set("hello");
        doc.frame(100, 100);
        ev.keyPressed(Keys.code("backspace"), 0);
        assertEquals("hell", m.query.get(), "a view model write puts the caret at the end");
    }

    @Test
    void paintShowsPlaceholderSelectionAndABlinkingCaretKeptInView() {
        Search m = new Search();
        UiDocument doc = load("<input class=\"i\" bind:value=\"query\" placeholder=\"find\"/>",
                ".i { width: 20px; height: 11px; padding: 1px 2px; color: #ff0; border-style: none; background-color: transparent }", m);
        long[] now = {0};
        doc.clock(() -> now[0]);
        assertEquals("""
                clip 2 1 16 9
                text 2 1 9 #80FFFF00 "find"
                unclip""", GoldenTest.print(doc.frame(100, 100)), "empty: the placeholder at half alpha");

        doc.events().mousePressed(5, 5, 0, 0);
        type(doc.events(), "abcdef");
        assertEquals("""
                clip 2 1 16 9
                text -19 1 9 #FFFFFF00 "abcdef"
                rect 17 1 1 9 #FFFFFF00
                unclip""", GoldenTest.print(doc.frame(100, 100)), "36px of text scrolls so the caret ends the box");

        now[0] = UiDocument.BLINK_MS * 1_000_000;
        long version = doc.frame(100, 100).version();
        assertEquals("""
                clip 2 1 16 9
                text -19 1 9 #FFFFFF00 "abcdef"
                unclip""", GoldenTest.print(doc.frame(100, 100)), "the caret blinks off without an edit");
        assertEquals(version, doc.frame(100, 100).version(), "a frame within the same blink phase repaints nothing");

        doc.events().keyPressed(Keys.code("home"), Keys.MOD_SHIFT);
        assertEquals("""
                clip 2 1 16 9
                rect 2 1 36 9 #803399FF
                text 2 1 9 #FFFFFF00 "abcdef"
                rect 2 1 1 9 #FFFFFF00
                unclip""", GoldenTest.print(doc.frame(100, 100)), "moving the caret home scrolls back and restarts the blink");
    }

    @Test
    void aPressPlacesTheCaretAtTheNearestCharacterBoundary() {
        Search m = new Search();
        UiDocument doc = load("<input class=\"i\" bind:value=\"query\"/>", ".i { width: 60px; height: 9px; padding: 0; border-style: none }", m);
        m.query.set("abcd");
        doc.frame(100, 100);
        doc.events().mousePressed(14, 4, 0, 0);
        type(doc.events(), "-");
        assertEquals("ab-cd", m.query.get(), "14px is nearer the boundary at 12 than at 18");
        doc.events().mousePressed(4, 4, 0, Keys.MOD_SHIFT);
        type(doc.events(), "+");
        assertEquals("a+cd", m.query.get(), "Shift+press extends the selection from the caret");
    }

    @Test
    void loadRejectsInputChildrenAndBindValueOnAnythingButStringState() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <input>x</input>
                <input bind:value="frozen"/>
                <input bind:value="count"/>
                <div bind:scrolltop="query"></div>
                """, "", new ViewModel() {
            final Value<String> query = state("");
            final Cell<String> frozen = source(com.azane.ixchel.core.binding.Source.polled(() -> "f"));
            final Value<Integer> count = state(0);
        }));
        assertEquals(List.of("template/invalid-children@t.html:2:8", "binding/two-way-target@t.html:3:20",
                "binding/two-way-target@t.html:4:20", "binding/two-way-target@t.html:5:22"), DiagCodes.of(e));
    }
}
