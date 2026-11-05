package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.view.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentFormTest {
    private static final int ESCAPE = Keys.code("escape");
    private static final int TAB = Keys.code("tab");
    private static final int ENTER = Keys.code("enter");

    private static UiDocument load(String html, String css, ViewModel model) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
    }

    private static void click(EventDispatcher ev, float x, float y) {
        ev.mousePressed(x, y, 0, 0);
        ev.mouseReleased(x, y, 0, 0);
    }

    private static Element one(UiDocument doc, String selector) {
        List<Element> found = doc.querySelectorAll(selector);
        assertEquals(1, found.size(), selector);
        return found.get(0);
    }

    static final class Picker extends ViewModel {
        final List<String> log = new ArrayList<>();
        final Value<String> mode = state("a");
        final Action<Void> input = action(() -> log.add("input " + mode.get()));
        final Action<Void> change = action(() -> log.add("change " + mode.get()));
        final Action<Void> below = action(() -> log.add("below"));
    }

    @Test
    void anOpenSelectListEscapesItsScrollClipAndIsHitAndPaintedAboveLaterContent() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <div class="list"><div class="pad"></div>
                <select class="s" bind:value="mode" on:input="input" on:change="change"><option value="a">Alpha</option><option value="b">Beta</option></select></div>
                <div class="after" on:mousedown="below"></div>
                """, ".list { height: 30px; overflow: auto } .pad { height: 18px; flex-shrink: 0 }"
                + " .s { padding: 0; border-style: none; width: 40px; height: 9px } .after { height: 40px }", m);
        doc.frame(100, 100);
        EventDispatcher ev = doc.events();
        click(ev, 5, 20);
        doc.frame(100, 100);
        List<Element> options = doc.querySelectorAll("select:open > option");
        assertEquals(2, options.size(), "the click opened the list");
        assertEquals(40, options.get(1).box().y(), "the list hangs under the select, outside the 30px clip");
        assertEquals(40, options.get(1).box().width(), "the list is at least as wide as the select");
        assertSame(options.get(1), ev.hitTest(5, 45), "the list is hit before the later sibling under it");
        String painted = GoldenTest.print(doc.frame(100, 100));
        assertTrue(painted.endsWith("text 3 42 9 #FFE0E0E0 \"Beta\""), "the list paints last, unclipped:\n" + painted);

        click(ev, 5, 45);
        doc.frame(100, 100);
        assertEquals("b", m.mode.get(), "picking writes back");
        assertEquals(List.of("input b", "change b"), m.log);
        assertTrue(doc.topLayer().entries().isEmpty(), "picking closes the list");

        click(ev, 5, 20);
        doc.frame(100, 100);
        ev.mousePressed(50, 60, 0, 0);
        ev.mouseReleased(50, 60, 0, 0);
        doc.frame(100, 100);
        assertTrue(doc.topLayer().entries().isEmpty(), "a press outside closes the list");
        assertEquals(List.of("input b", "change b"), m.log, "and is not dispatched to what lies under it");
    }

    static final class Dialogs extends ViewModel {
        final List<String> log = new ArrayList<>();
        final Value<Boolean> open = state(false);
        final Value<String> text = state("");
        final Value<String> mode = state("a");
        final Action<Void> below = action(() -> log.add("below"));
        final Action<Void> closed = action(() -> log.add("close"));
    }

    @Test
    void aModalDialogBlocksWhatIsBelowAndEscapeClosesItsNestedSelectFirst() {
        Dialogs m = new Dialogs();
        UiDocument doc = load("""
                <button class="below" on:click="below">x</button>
                <dialog class="d" bind:open="open" on:close="closed"><input class="first" bind:value="text"/>
                <select class="pick" bind:value="mode"><option value="a">A</option><option value="b">B</option></select></dialog>
                """, "", m);
        doc.frame(100, 100);
        EventDispatcher ev = doc.events();
        click(ev, 2, 2);
        assertEquals(List.of("below"), m.log);
        Element button = one(doc, ".below");
        assertSame(button, ev.focused());

        m.open.set(true);
        doc.frame(100, 100);
        Element dialog = one(doc, ".d");
        assertEquals(50, dialog.box().x() + dialog.box().width() / 2, 1, "centered in the viewport");
        assertSame(one(doc, ".first"), ev.focused(), "opening moves focus into the dialog");
        assertTrue(ev.mousePressed(2, 2, 0, 0), "a press outside the dialog is taken");
        ev.mouseReleased(2, 2, 0, 0);
        assertEquals(List.of("below"), m.log, "but reaches nothing below");
        ev.keyPressed(TAB, 0);
        assertSame(one(doc, ".pick"), ev.focused());
        ev.keyPressed(TAB, 0);
        assertSame(one(doc, ".first"), ev.focused(), "Tab cycles inside the dialog");

        ev.keyPressed(TAB, 0);
        ev.keyPressed(ENTER, 0);
        doc.frame(100, 100);
        assertEquals(2, doc.topLayer().entries().size(), "the select opened above the dialog");
        assertTrue(ev.keyPressed(ESCAPE, 0));
        doc.frame(100, 100);
        assertEquals(List.of(dialog), doc.topLayer().entries(), "Escape closed only the list");
        assertTrue(m.open.get());
        assertTrue(ev.keyPressed(ESCAPE, 0));
        doc.frame(100, 100);
        assertFalse(m.open.get(), "Escape closed the dialog and wrote back");
        assertEquals(List.of("below", "close"), m.log);
        assertSame(button, ev.focused(), "focus went back to where it was");
        assertFalse(ev.keyPressed(ESCAPE, 0), "with nothing open, Escape is the host's");
    }

    static final class Controls extends ViewModel {
        final List<String> log = new ArrayList<>();
        final Value<Boolean> on = state(false);
        final Value<String> level = state("4");
        final Value<String> count = state("2");
        final Action<Void> toggled = action(() -> log.add("checkbox " + on.get()));
        final Action<Void> leveled = action(() -> log.add("range " + level.get()));
    }

    @Test
    void checkboxRangeAndNumberEditWithinTheirBoundsAndCommitOnChange() {
        Controls m = new Controls();
        UiDocument doc = load("""
                <input type="checkbox" class="c" bind:checked="on" on:change="toggled"/>
                <input type="range" class="r" min="0" max="10" step="2" bind:value="level" on:change="leveled"/>
                <input type="number" class="n" min="0" max="3" bind:value="count"/>
                """, ".c, .r, .n { padding: 0; border-style: none } .r { width: 55px } .n { width: 30px; height: 9px }", m);
        doc.frame(100, 100);
        EventDispatcher ev = doc.events();
        click(ev, 5, 5);
        doc.frame(100, 100);
        assertTrue(m.on.get());
        assertTrue(one(doc, ".c").hasState(Element.CHECKED), ":checked follows the click");

        ev.keyPressed(TAB, 0);
        ev.keyPressed(Keys.code("right"), 0);
        assertEquals("6", m.level.get(), "Right steps by step");
        ev.keyPressed(Keys.code("end"), 0);
        ev.keyPressed(Keys.code("right"), 0);
        assertEquals("10", m.level.get(), "End goes to max and Right stops there");
        assertEquals(List.of("checkbox true", "range 6", "range 10"), m.log, "a key that does not move commits nothing");

        m.log.clear();
        // Track: thumb centers from 2.5 to 52.5.
        ev.mousePressed(12.5f, 15, 0, 0);
        assertEquals("2", m.level.get(), "a press jumps to the pointer, snapped to the step");
        ev.mouseMoved(70, 15);
        assertEquals("10", m.level.get(), "dragging past the end clamps");
        ev.mouseMoved(40, 15);
        assertEquals("8", m.level.get());
        ev.mouseReleased(40, 15, 0, 0);
        assertEquals(List.of("range 8"), m.log, "one change, on release, against the value before the press");

        ev.keyPressed(TAB, 0);
        ev.keyPressed(Keys.code("up"), 0);
        ev.keyPressed(Keys.code("up"), 0);
        assertEquals("3", m.count.get(), "Up steps and stops at max");
        ev.charTyped('x', 0);
        assertEquals("3", m.count.get(), "a number field takes no letters but e");
        ev.keyPressed(Keys.code("down"), 0);
        assertEquals("2", m.count.get());
    }

    record Row(String id, Value<String> text, Derived<Boolean> big) {
    }

    static final class Rows extends ViewModel {
        final Value<List<Row>> rows;

        Rows() {
            List<Row> list = new ArrayList<>();
            for (String s : List.of("a", "b")) {
                Value<String> text = state(s);
                list.add(new Row(s, text, derive(text, t -> t.length() > 2)));
            }
            rows = state(List.copyOf(list));
        }
    }

    @Test
    void rowValuesInEachAreTwoWayAndRowCellsAreFollowedWithoutTheListChanging() {
        Rows m = new Rows();
        UiDocument doc = load("""
                <for each="rows" as="r" key="r.id"><div class="row" bind:class:big="r.big">
                <input class="t" bind:value="r.text"/><span bind:text="r.text"></span></div></for>
                """, ".t { padding: 0; border-style: none; width: 60px; height: 9px }", m);
        doc.frame(100, 100);
        EventDispatcher ev = doc.events();
        List<Row> before = m.rows.get();
        click(ev, 50, 22);
        "xy".codePoints().forEach(c -> ev.charTyped(c, 0));
        doc.frame(100, 100);
        assertEquals("bxy", before.get(1).text().get(), "typing wrote the second row's own Value");
        assertEquals("a", before.get(0).text().get());
        assertSame(before, m.rows.get());
        List<Element> rows = doc.querySelectorAll(".row");
        assertEquals("bxy", rows.get(1).textContent(), "the row's span follows its cell");
        assertTrue(rows.get(1).hasClass("big") && !rows.get(0).hasClass("big"), "a row Derived is followed too");

        before.get(0).text().set("q");
        doc.frame(100, 100);
        assertEquals("q", doc.querySelectorAll(".t").get(0).control().value(), "a model write reaches the row's input");
    }

    static final class Clicker extends ViewModel {
        final Action<Void> go = action(() -> {
        });
    }

    @Test
    void theClickSoundPlaysForButtonsCheckboxesAndOpeningSelectsUnlessDisabledOrSilenced() {
        UiDocument doc = load("""
                <button class="c" on:click="go"><span>go</span></button>
                <button class="c q">quiet</button>
                <button class="c" disabled="">off</button>
                <input class="c" type="checkbox"/>
                <select class="c"><option>A</option></select>
                """, ".c { padding: 0; border-style: none; width: 40px; height: 10px; flex-shrink: 0 }"
                + " .q { -ix-click-sound: none }", new Clicker());
        doc.frame(100, 100);
        EventDispatcher ev = doc.events();
        int[] played = {0};
        ev.setClickSound(() -> played[0]++);
        click(ev, 5, 5);
        assertEquals(1, played[0], "a click on the span inside a button, whose listener consumes it");
        click(ev, 5, 15);
        click(ev, 5, 25);
        assertEquals(1, played[0], "neither the silenced nor the disabled button");
        click(ev, 5, 35);
        assertEquals(2, played[0], "the checkbox toggled");
        click(ev, 5, 45);
        assertEquals(3, played[0], "the select opened");
        ev.keyPressed(ESCAPE, 0);
        assertEquals(3, played[0], "closing plays nothing");
        click(ev, 5, 5);
        ev.keyPressed(ENTER, 0);
        assertEquals(5, played[0], "Enter on the focused button");
    }

    static final class Disclosure extends ViewModel {
        final Value<Boolean> expanded = state(false);
    }

    @Test
    void aSummaryTogglesItsDetailsByClickOnItsLabelOrByEnterAndWritesBindOpen() {
        Disclosure m = new Disclosure();
        UiDocument doc = load("""
                <details class="d" bind:open="expanded"><summary class="s"><span class="l"></span></summary><div class="body"></div></details>
                """, ".s { height: 10px } .l { width: 10px; height: 10px } .body { height: 20px; flex-shrink: 0 }", m);
        doc.frame(100, 100);
        Element details = one(doc, ".d");
        EventDispatcher ev = doc.events();
        click(ev, 12, 5);
        doc.frame(100, 100);
        assertTrue(m.expanded.get(), "a click on the summary's label opens the details and writes back");
        assertTrue(details.hasState(Element.OPEN), ":open");
        assertEquals(30, details.box().height(), "the content is laid out while open");

        ev.keyPressed(ENTER, 0);
        doc.frame(100, 100);
        assertFalse(m.expanded.get(), "Enter on the focused summary closes it");
        assertFalse(details.hasState(Element.OPEN));

        m.expanded.set(true);
        doc.frame(100, 100);
        assertTrue(details.hasState(Element.OPEN), "the model opens it");
    }

    @Test
    void aClosedDetailsLaysOutOnlyItsSummaryWhateverTheAuthorDisplay() {
        UiDocument doc = load("""
                <details class="d"><summary class="s"></summary><div class="body"></div></details>
                """, ".s { height: 10px } .body { display: flex; height: 20px; flex-shrink: 0 }", new Clicker());
        doc.frame(100, 100);
        assertEquals(10, one(doc, ".d").box().height());
    }

    @Test
    void aDetailsThatDoesNotStartWithASummaryIsReported() {
        UiLoadException e = assertThrows(UiLoadException.class,
                () -> load("<details><div></div><summary></summary></details>\n", "", new Clicker()));
        assertEquals(List.of("view/details-without-summary@t.html:2:10"), DiagCodes.of(e));
    }

    record Mode(int id) {
    }

    static final class Modes extends ViewModel {
        final Value<List<Mode>> modes = state(List.of(new Mode(1), new Mode(2), new Mode(3)));
        final Value<Long> mode = state(2L);
    }

    @Test
    void radiosInAForSharingAGroupWriteTheirValueAndCheckedMovesWithIt() {
        Modes m = new Modes();
        UiDocument doc = load("""
                <for each="modes" as="m" key="m.id"><input type="radio" class="r" bind:group="mode" bind:value="m.id"/></for>
                """, "", m);
        doc.frame(100, 100);
        List<Element> radios = doc.querySelectorAll(".r");
        assertTrue(radios.get(1).hasState(Element.CHECKED), "the Long group equals the int value 2");
        EventDispatcher ev = doc.events();
        click(ev, 5, 5);
        doc.frame(100, 100);
        assertEquals(1L, m.mode.get(), "the click wrote the radio's value");
        assertEquals(List.of(radios.get(0)), doc.querySelectorAll(".r:checked"), ":checked moved to the clicked radio");

        ev.keyPressed(TAB, 0);
        ev.keyPressed(Keys.code("space"), 0);
        doc.frame(100, 100);
        assertEquals(2L, m.mode.get(), "Space checks the focused radio");
        assertEquals(List.of(radios.get(1)), doc.querySelectorAll(".r:checked"));
    }

    @Test
    void aRadioValueTheGroupCannotHoldIsReported() {
        UiLoadException e = assertThrows(UiLoadException.class,
                () -> load("<input type=\"radio\" bind:group=\"mode\" bind:value=\"'x'\"/>\n", "", new Modes()));
        assertEquals(List.of("binding/value-type@t.html:2:51"), DiagCodes.of(e));
    }
}
