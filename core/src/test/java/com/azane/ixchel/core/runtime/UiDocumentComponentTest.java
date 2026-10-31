package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentComponentTest {
    private static UiDocument load(String html, Map<String, String> files, ViewModel model) {
        return UiDocument.load(html, "t.html", files::get, TagRegistry.HTML, GoldenTest.MONO, GoldenTest.NO_IMAGES,
                model, Bindings.PLAIN_TEXT);
    }

    private static Element byId(UiDocument doc, String id) {
        return doc.querySelectorAll("#" + id).get(0);
    }

    /** The children of {@code e} as {@code tag:text}, in order. */
    private static String shape(Element e) {
        StringBuilder out = new StringBuilder();
        for (Node n : e.children()) {
            if (n instanceof Element c) {
                out.append(c.tag()).append(':').append(c.textContent()).append(' ');
            }
        }
        return out.toString().trim();
    }

    private static void click(EventDispatcher ev, float x, float y) {
        ev.mousePressed(x, y, 0, 0);
        ev.mouseReleased(x, y, 0, 0);
    }

    static final class Blocks extends ViewModel {
        final Value<List<String>> rows = state(List.of("a", "b"));
        final Value<Boolean> show = state(false);
        final Value<Boolean> deep = state(true);
    }

    @Test
    void blocksMountSeveralRootsInPlaceNestAndReorderKeyedRowsKeepingTheirNodes() {
        Blocks m = new Blocks();
        UiDocument doc = load("<div id=\"list\"><div>[</div><for each=\"rows\" as=\"r\" key=\"r\"><span bind:text=\"r\"></span>"
                + "<button bind:text=\"r\"></button></for><div>]</div></div>"
                + "<div id=\"opt\"><div>[</div><if test=\"show\"><span>x</span><if test=\"deep\"><button>y</button></if></if>"
                + "<div>]</div></div>",
                Map.of(), m);
        doc.frame(100, 100);
        Element list = byId(doc, "list");
        Element opt = byId(doc, "opt");
        assertEquals("div:[ span:a button:a span:b button:b div:]", shape(list));
        assertEquals("div:[ div:]", shape(opt));
        Node bRow = list.children().get(3);

        m.rows.set(List.of("b", "c", "a"));
        m.show.set(true);
        doc.frame(100, 100);
        assertEquals("div:[ span:b button:b span:c button:c span:a button:a div:]", shape(list));
        assertTrue(bRow == list.children().get(1), "a kept key keeps its nodes when moved");
        assertEquals("div:[ span:x button:y div:]", shape(opt));

        m.deep.set(false);
        m.rows.set(List.of());
        doc.frame(100, 100);
        assertEquals("div:[ div:]", shape(list));
        assertEquals("div:[ span:x div:]", shape(opt));
    }

    private static final String COUNTER = """
            <template name="ui-counter" params="count label='n' step=1" events="changed">
            <button class="inc" on:click="count = count + step; changed(count)"><span bind:text="label + count"></span></button>
            </template>
            """;
    private static final String COUNTER_CSS = ".inc { width: 20px; height: 10px; padding: 0; border-style: none }";

    static final class Counters extends ViewModel {
        final Value<Integer> total = state(1);
        final Value<Integer> last = state(0);
    }

    @Test
    void parametersWriteBackThroughTheUsersPathDefaultsAreLocalAndEventsCarryAPayload() {
        Counters m = new Counters();
        UiDocument doc = load("""
                <link rel="component" href="ui/counter.html"/><style>ui-counter { width: 20px; height: 10px }</style>
                <ui-counter id="a" bind:count="total" on:changed="last = $event"></ui-counter>
                <ui-counter id="b" bind:count="total" label="x" bind:step="10"></ui-counter>
                """, Map.of("ui/counter.html", "<link rel=\"stylesheet\" href=\"counter.css\"/>" + COUNTER,
                "ui/counter.css", COUNTER_CSS), m);
        doc.frame(100, 100);
        EventDispatcher ev = doc.events();
        assertEquals("n1", byId(doc, "a").textContent());
        assertEquals("x1", byId(doc, "b").textContent());

        click(ev, 5, 5);
        doc.frame(100, 100);
        assertEquals(2, m.total.get());
        assertEquals(2, m.last.get(), "the payload reaches the listener as $event");
        assertEquals("n2", byId(doc, "a").textContent());
        assertEquals("x2", byId(doc, "b").textContent(), "both instances read the same page state");

        click(ev, 5, 15);
        doc.frame(100, 100);
        assertEquals(12, m.total.get(), "the passed step replaced the default");
        assertEquals(2, m.last.get(), "an event nobody listens to does nothing");
    }

    static final class Card extends ViewModel {
        final Value<String> msg = state("hi");
        final Value<Boolean> show = state(false);
    }

    @Test
    void slotContentResolvesInTheUsersScopeAndAnyGivenContentSuppressesTheFallback() {
        Card m = new Card();
        UiDocument doc = load("""
                <link rel="component" href="card.html"/>
                <ui-card id="c1"><span slot="title">T</span><span bind:text="msg"></span></ui-card>
                <ui-card id="c2"></ui-card>
                <ui-card id="c3"><if test="show" slot="title"><span>late</span></if></ui-card>
                """, Map.of("card.html", """
                <template name="ui-card">
                <div class="head"><slot name="title"><span>untitled</span></slot></div><div class="body"><slot></slot></div>
                </template>
                """), m);
        doc.frame(100, 100);
        assertEquals("Thi", byId(doc, "c1").textContent());
        assertEquals("untitled", byId(doc, "c2").textContent());
        assertEquals("", byId(doc, "c3").textContent(), "an unmounted <if> still counts as given content");

        m.msg.set("yo");
        m.show.set(true);
        doc.frame(100, 100);
        assertEquals("Tyo", byId(doc, "c1").textContent());
        assertEquals("late", byId(doc, "c3").textContent());
    }

    @Test
    void loadReportsComponentMisuseAtTheUseAndBodyErrorsWithTheUseAppended() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link rel="component" href="counter.html"/><link rel="component" href="card.html"/>
                <link rel="component" href="loop-a.html"/><link rel="component" href="dup.html"/>
                <ui-counter bind:count="total + 1"></ui-counter>
                <ui-counter label="a" bind:label="'b'" bind:count="total"></ui-counter>
                <ui-counter></ui-counter>
                <ui-card><span slot="nope"></span></ui-card>
                <ui-counter bind:count="total" on:changed.shift="last = 1"></ui-counter>
                """, Map.of("counter.html", COUNTER,
                "card.html", "<template name=\"ui-card\" events=\"done\"><button on:click=\"done; done(1)\"></button></template>",
                "loop-a.html", "<link rel=\"component\" href=\"loop-b.html\"/><template name=\"ui-a\"></template>",
                "loop-b.html", "<link rel=\"component\" href=\"loop-a.html\"/><template name=\"ui-b\"></template>",
                "dup.html", "<template name=\"ui-card\"></template>"), new Counters()));
        assertEquals(List.of("load/component-cycle@loop-b.html:1:1", "load/duplicate-component@dup.html:1:1",
                "binding/not-assignable@counter.html:2:31", "binding/duplicate-parameter@t.html:4:1",
                "binding/missing-parameter@t.html:5:1", "binding/unknown-slot@t.html:6:10",
                "binding/event-payload@card.html:1:64", "binding/unexpected-filter@t.html:7:32"), DiagCodes.of(e));
    }

    @Test
    void aParameterWhoseBindingFailsIsNotAlsoReportedMissing() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link rel="component" href="counter.html"/>
                <ui-counter bind:count="nope"></ui-counter>
                """, Map.of("counter.html", COUNTER), new Counters()));
        assertEquals(List.of("binding/unknown-name@t.html:2:25"), DiagCodes.of(e));
    }

    static final class Styled extends ViewModel {
    }

    @Test
    void authorRulesBeatComponentRulesImportantReversesThatAndInlineBeatsBoth() {
        UiDocument doc = load("""
                <link rel="component" href="tag.html"/><link rel="stylesheet" href="page.css"/>
                <ui-tag></ui-tag>
                """, Map.of("tag.html", """
                <style>.a { color: #f00 } .b { color: #0f0 !important } .c { color: #f00 }</style>
                <template name="ui-tag"><span class="a">a</span><span class="b">b</span><span class="c" style="color: #00f">c</span></template>
                """, "page.css", ".a { color: #00f } .c { color: #0f0 } .b { color: #f00 !important }"), new Styled());
        assertEquals("""
                text 0 0 9 #FF0000FF "a"
                text 0 9 9 #FF00FF00 "b"
                text 0 18 9 #FF0000FF "c"
                """.strip(), GoldenTest.print(doc.frame(100, 100)).strip());
    }

    static final class Themed extends ViewModel {
        final Value<String> accent = state("#00f");
    }

    @Test
    void themeLayerSitsBetweenComponentsAndAuthorAndItsPropertiesReachEveryElement() {
        Themed m = new Themed();
        UiDocument doc = load("""
                <link rel="component" href="btn.html"/><link rel="stylesheet" href="@ixchel/dark.css"/>
                <style>.title { color: #0f0 } .muted { color: var(--ix-text-muted) } .accent { color: var(--accent) }</style>
                <div class="panel" bind:style:--accent="accent"><span class="title">t</span><span class="muted">m</span>
                <ui-btn class="accent"></ui-btn></div>
                """, Map.of("btn.html", """
                <style>ui-btn button.b { background-color: #f00; color: var(--ix-accent) }</style>
                <template name="ui-btn"><button class="b">b</button></template>
                """), m);
        doc.frame(100, 100);
        assertEquals(0xE0101418, doc.querySelectorAll(".panel").get(0).computed().get(CssProperties.BACKGROUND_COLOR));
        assertEquals(0xFF00FF00, doc.querySelectorAll(".title").get(0).computed().get(CssProperties.COLOR));
        assertEquals(0xFFAAAAAA, doc.querySelectorAll(".muted").get(0).computed().get(CssProperties.COLOR));
        Element b = doc.querySelectorAll("button").get(0);
        assertEquals(0x30FFFFFF, b.computed().get(CssProperties.BACKGROUND_COLOR), "the theme beats a more specific component rule");
        assertEquals(0xFFFFFFFF, b.computed().get(CssProperties.COLOR));
        assertEquals(0xFF0000FF, doc.querySelectorAll("ui-btn").get(0).computed().get(CssProperties.COLOR));

        m.accent.set("#ff0");
        doc.frame(100, 100);
        assertEquals(0xFFFFFF00, doc.querySelectorAll("ui-btn").get(0).computed().get(CssProperties.COLOR));
    }

    @Test
    void libraryStylesheetsMustExistAndOnlyThePageLinksThem() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link rel="component" href="c.html"/><link rel="stylesheet" href="@ixchel/nope.css"/><ui-c></ui-c>
                """, Map.of("c.html", "<link rel=\"stylesheet\" href=\"@ixchel/dark.css\"/><template name=\"ui-c\"></template>"),
                new Styled()));
        assertEquals(List.of("load/library-sheet-not-on-page@c.html:1:1", "load/unknown-library-sheet@t.html:1:38"), DiagCodes.of(e));
    }
}
