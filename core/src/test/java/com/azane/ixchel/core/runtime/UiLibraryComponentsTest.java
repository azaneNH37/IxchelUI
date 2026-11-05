package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.dom.LayoutBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The library's {@code ix-*} components, as a page uses them. */
class UiLibraryComponentsTest {
    private static final String LINKS = """
            <link href="page.css"/>
            <link href="@ixchel/components/tabs.html"/><link href="@ixchel/components/tab.html"/>
            <link href="@ixchel/components/tab-panel.html"/><link href="@ixchel/components/tree-row.html"/>
            <link href="@ixchel/components/bar.html"/><link href="@ixchel/components/stepper.html"/>
            <link href="@ixchel/components/cycle.html"/><link href="@ixchel/components/pager.html"/>
            """;
    private static final Map<String, String> FILES = Map.of("page.css", """
            @stylesheet;
            ix-tab, ix-cycle { width: 30px; height: 10px }
            ix-stepper, ix-pager { width: 60px; height: 10px }
            .wide { width: 100px; height: 10px }
            .tall { width: 10px; height: 100px }
            """);

    static final class Model extends ViewModel {
        final Value<String> tab = state("a");
        final Value<Integer> picks = state(0);
        final Value<Integer> hp = state(30);
        final Value<Integer> max = state(120);
        final Value<Integer> count = state(5);
        final Value<Integer> zero = state(0);
        final Value<Integer> mode = state(0);
        final Value<List<String>> modes = state(List.of("off", "on", "auto"));
    }

    private static UiDocument load(String body, Model m) {
        UiDocument doc = UiDocument.load(GoldenTest.page(LINKS + body), "t.html", FILES::get, HtmlElements.TAGS,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        doc.frame(300, 300);
        assertEquals(List.of(), doc.diagnostics().entries());
        return doc;
    }

    private static Element one(UiDocument doc, String selector) {
        List<Element> found = doc.querySelectorAll(selector);
        assertEquals(1, found.size(), selector);
        return found.get(0);
    }

    private static void click(UiDocument doc, String selector, int button, int mods) {
        LayoutBox b = one(doc, selector).box();
        float x = b.x() + b.width() / 2;
        float y = b.y() + b.height() / 2;
        doc.events().mousePressed(x, y, button, mods);
        doc.events().mouseReleased(x, y, button, mods);
        doc.frame(300, 300);
    }

    // Fails if a tab cannot write the value its ix-tabs provides, or raises pick without it.
    @Test
    void aTabSelectsItselfThroughItsTabsAndRaisesPick() {
        Model m = new Model();
        UiDocument doc = load("""
                <ix-tabs bind:value="tab">
                  <ix-tab id="a" value="a" label="A" on:pick="picks = picks + 1"></ix-tab>
                  <ix-tab id="b" value="b" label="B" on:pick="picks = picks + 1"></ix-tab>
                </ix-tabs>
                """, m);
        assertEquals(1, doc.querySelectorAll("#a .ix-tab-button.ix-selected").size());

        click(doc, "#b .ix-tab-button", 0, 0);
        assertEquals("b", m.tab.get());
        assertEquals(1, m.picks.get());
        assertEquals(0, doc.querySelectorAll("#a .ix-selected").size());
        assertEquals(1, doc.querySelectorAll("#b .ix-tab-button.ix-selected").size());
    }

    // Fails if the fill is not value / max of the space inside the bar's frame along its direction, from
    // the start side, held within that space, or covers the frame.
    @Test
    void aBarFillsTheShareOfItsValueFromItsStartSide() {
        Model m = new Model();
        UiDocument doc = load("""
                <ix-bar id="r" class="wide" bind:value="hp" bind:max="max"></ix-bar>
                <ix-bar id="u" class="tall" bind:value="hp" bind:max="max" dir="up"></ix-bar>
                """, m);
        LayoutBox right = one(doc, "#r").box();
        LayoutBox fill = one(doc, "#r .ix-bar-fill").box();
        assertEquals(25f, fill.width(), 1f);
        assertEquals(right.x() + 1, fill.x());
        assertEquals(right.y() + 1, fill.y());
        LayoutBox up = one(doc, "#u").box();
        LayoutBox upFill = one(doc, "#u .ix-bar-fill").box();
        assertEquals(25f, upFill.height(), 1f);
        assertEquals(up.y() + up.height() - 1, upFill.y() + upFill.height(), "an upward bar stands on its bottom");

        m.hp.set(500);
        doc.frame(300, 300);
        assertEquals(98f, one(doc, "#r .ix-bar-fill").box().width());
        m.max.set(0);
        doc.frame(300, 300);
        assertEquals(0f, one(doc, "#r .ix-bar-fill").box().width());
    }

    // Fails if Shift does not step ten times, or stepping passes max or min.
    @Test
    void aStepperStepsTenWithShiftAndHoldsWithinItsRange() {
        Model m = new Model();
        UiDocument doc = load("<ix-stepper bind:value=\"count\" bind:min=\"zero\" bind:max=\"max\"></ix-stepper>", m);
        click(doc, ".ix-stepper-inc", 0, Keys.MOD_SHIFT);
        assertEquals(15, m.count.get());
        click(doc, ".ix-stepper-dec", 0, 0);
        assertEquals(14, m.count.get());
        click(doc, ".ix-stepper-dec", 0, Keys.MOD_SHIFT);
        click(doc, ".ix-stepper-dec", 0, Keys.MOD_SHIFT);
        assertEquals(0, m.count.get());
        assertTrue(one(doc, ".ix-stepper-dec").hasState(Element.DISABLED), "no step below min");
    }

    // Fails if the stepper's field does not write a typed number back as the value's type, or writes
    // text that is not a number yet.
    @Test
    void aStepperTakesATypedNumber() {
        Model m = new Model();
        UiDocument doc = load("<ix-stepper bind:value=\"count\" bind:min=\"zero\" bind:max=\"max\"></ix-stepper>", m);
        click(doc, ".ix-stepper-input", 0, 0);
        doc.events().keyPressed(Keys.code("backspace"), 0);
        doc.events().charTyped('-', 0);
        doc.frame(300, 300);
        assertEquals(5, m.count.get(), "'-' is not a number yet");
        doc.events().keyPressed(Keys.code("backspace"), 0);
        "42".codePoints().forEach(c -> doc.events().charTyped(c, 0));
        doc.frame(300, 300);
        assertEquals(42, m.count.get());
    }

    // Fails if a panel shows while another tab is selected, or does not follow a tab click.
    @Test
    void aTabPanelShowsWhileItsTabIsSelected() {
        Model m = new Model();
        UiDocument doc = load("""
                <ix-tabs bind:value="tab">
                  <ix-tab id="b" value="b" label="B"></ix-tab>
                  <ix-tab-panel value="a"><span class="pa">A</span></ix-tab-panel>
                  <ix-tab-panel value="b"><span class="pb">B</span></ix-tab-panel>
                </ix-tabs>
                """, m);
        assertEquals(1, doc.querySelectorAll(".pa").size());
        assertEquals(0, doc.querySelectorAll(".pb").size());
        click(doc, "#b .ix-tab-button", 0, 0);
        assertEquals(0, doc.querySelectorAll(".pa").size());
        assertEquals(1, doc.querySelectorAll(".pb").size());
    }

    // Fails if a leaf row's arrow raises toggle, or a branch row's does not.
    @Test
    void onlyABranchRowsArrowRaisesToggle() {
        Model m = new Model();
        UiDocument doc = load("""
                <ix-tree-row id="leaf" bind:depth="1" bind:expanded="false" bind:leaf="true" on:toggle="picks = picks + 1">x</ix-tree-row>
                <ix-tree-row id="branch" bind:depth="0" bind:expanded="false" bind:leaf="false" on:toggle="picks = picks + 10">y</ix-tree-row>
                """, m);
        click(doc, "#leaf .ix-tree-row-arrow", 0, 0);
        assertEquals(0, m.picks.get());
        click(doc, "#branch .ix-tree-row-arrow", 0, 0);
        assertEquals(10, m.picks.get());
    }

    // Fails if the cycle does not wrap both ways or shows another option than its value.
    @Test
    void aCycleStepsForwardWithTheLeftButtonAndBackWithTheRight() {
        Model m = new Model();
        UiDocument doc = load("<ix-cycle bind:value=\"mode\" bind:options=\"modes\"></ix-cycle>", m);
        click(doc, ".ix-cycle-button", 1, 0);
        assertEquals(2, m.mode.get(), "back from the first wraps to the last");
        assertEquals("auto", one(doc, ".ix-cycle-button span").textContent());
        click(doc, ".ix-cycle-button", 0, 0);
        assertEquals(0, m.mode.get(), "forward from the last wraps to the first");
    }

    // Fails if the pager pages past its ends.
    @Test
    void aPagerStopsAtItsLastPage() {
        Model m = new Model();
        m.mode.set(3);
        UiDocument doc = load("<ix-pager bind:page=\"mode\" bind:count=\"count\"></ix-pager>", m);
        assertEquals("4 / 5", one(doc, ".ix-pager-label").textContent());
        click(doc, ".ix-pager-next", 0, 0);
        assertEquals(4, m.mode.get());
        click(doc, ".ix-pager-next", 0, 0);
        assertEquals(4, m.mode.get());
        assertTrue(one(doc, ".ix-pager-next").hasState(Element.DISABLED));
    }
}
