package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.event.EventDispatcher;
import com.azane.ixchel.core.view.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentComponentTest {
    private static UiDocument load(String html, Map<String, String> files, ViewModel model) {
        return UiDocument.load(GoldenTest.page(html), "t.html", files::get, com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES,
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
            <component name="ui-counter" params="count label='n' step=1" events="changed">
            <button class="inc" on:click="count = count + step; changed(count)"><span bind:text="label + count"></span></button>
            </component>
            """;
    private static final String COUNTER_CSS = "@stylesheet; .inc { width: 20px; height: 10px; padding: 0; border-style: none }";

    static final class Counters extends ViewModel {
        final Value<Integer> total = state(1);
        final Value<Integer> last = state(0);
    }

    @Test
    void parametersWriteBackThroughTheUsersPathDefaultsAreLocalAndEventsCarryAPayload() {
        Counters m = new Counters();
        UiDocument doc = load("""
                <link href="page.css"/><link href="ui/counter.html"/>
                <ui-counter id="a" bind:count="total" on:changed="last = $event"></ui-counter>
                <ui-counter id="b" bind:count="total" label="x" bind:step="10"></ui-counter>
                """, Map.of("page.css", "@stylesheet; ui-counter { width: 20px; height: 10px }",
                "ui/counter.html", "<link href=\"counter.css\"/>" + COUNTER,
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
                <link href="card.html"/>
                <ui-card id="c1"><span slot="title">T</span><span bind:text="msg"></span></ui-card>
                <ui-card id="c2"></ui-card>
                <ui-card id="c3"><if test="show" slot="title"><span>late</span></if></ui-card>
                """, Map.of("card.html", """
                <component name="ui-card">
                <div class="head"><slot name="title"><span>untitled</span></slot></div><div class="body"><slot></slot></div>
                </component>
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
                <link href="counter.html"/><link href="card.html"/>
                <link href="loop-a.html"/><link href="dup.html"/>
                <ui-counter bind:count="total + 1"></ui-counter>
                <ui-counter label="a" bind:label="'b'" bind:count="total"></ui-counter>
                <ui-counter></ui-counter>
                <ui-card><span slot="nope"></span></ui-card>
                <ui-counter bind:count="total" on:changed.shift="last = 1"></ui-counter>
                """, Map.of("counter.html", COUNTER,
                "card.html", "<component name=\"ui-card\" events=\"done\"><button on:click=\"done; done(1)\"></button></component>",
                "loop-a.html", "<link href=\"loop-b.html\"/><component name=\"ui-a\"></component>",
                "loop-b.html", "<link href=\"loop-a.html\"/><component name=\"ui-b\"></component>",
                "dup.html", "<component name=\"ui-card\"></component>"), new Counters()));
        assertEquals(List.of("link/cycle@loop-b.html:1:1", "link/duplicate-component@dup.html:1:1",
                "binding/not-assignable@counter.html:2:31", "binding/duplicate-parameter@t.html:4:1",
                "binding/missing-parameter@t.html:5:1", "binding/unknown-slot@t.html:6:10",
                "binding/event-payload@card.html:1:65", "binding/unexpected-filter@t.html:7:32"), DiagCodes.of(e));
    }

    @Test
    void aParameterWhoseBindingFailsIsNotAlsoReportedMissing() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="counter.html"/>
                <ui-counter bind:count="nope"></ui-counter>
                """, Map.of("counter.html", COUNTER), new Counters()));
        assertEquals(List.of("binding/unknown-name@t.html:2:25"), DiagCodes.of(e));
    }

    static final class Styled extends ViewModel {
    }

    @Test
    void eachViewsRulesReachOnlyItsOwnElementsAndThePageWinsOnTheHostUnlessTheComponentIsImportant() {
        UiDocument doc = load("""
                <link href="tag.html"/><link href="page.css"/>
                <ui-tag><span class="a" id="given">g</span></ui-tag><span class="a" id="own">p</span>
                """, Map.of("tag.html", """
                <link href="tag.css"/><component name="ui-tag"><span class="a" id="inner">a</span><slot></slot></component>
                """, "tag.css", "@stylesheet; .a { color: #f00 } ui-tag { background-color: #f00; border-color: #f00 !important }",
                "page.css", "@stylesheet; .a { color: #00f } ui-tag { background-color: #00f; border-color: #00f }"), new Styled());
        doc.frame(100, 100);
        assertEquals(0xFFFF0000, byId(doc, "inner").computed().get(CssProperties.COLOR), "the page's .a does not reach in");
        assertEquals(0xFF0000FF, byId(doc, "own").computed().get(CssProperties.COLOR), "the component's .a does not leak out");
        assertEquals(0xFF0000FF, byId(doc, "given").computed().get(CssProperties.COLOR), "slot content is the page's");
        Element host = doc.querySelectorAll("ui-tag").get(0);
        assertEquals(0xFF0000FF, host.computed().get(CssProperties.BACKGROUND_COLOR), "the page wins on the host");
        assertEquals(0xFFFF0000, host.computed().get(CssProperties.BORDER_TOP_COLOR), "!important reverses that");
    }

    @Test
    void thePageStylesAComponentsInsideOnlyThroughItsParts() {
        UiDocument doc = load("""
                <link href="bar.html"/><link href="page.css"/>
                <ui-bar id="x"></ui-bar><ui-bar id="y"></ui-bar>
                """, Map.of("bar.html", """
                <link href="bar.css"/><component name="ui-bar"><div class="fill" part="fill track"></div><div class="tip"></div></component>
                """, "bar.css", "@stylesheet; .fill { background-color: #f00; width: 5px } .tip { background-color: #f00 }",
                "page.css", """
                @stylesheet;
                #x::part(fill) { background-color: #00f }
                #y::part(track):hover { background-color: #0f0 }
                .tip, ui-bar::part(tip) { background-color: #00f }
                """), new Styled());
        doc.frame(100, 100);
        Element xFill = doc.querySelectorAll("#x .fill").get(0);
        Element yFill = doc.querySelectorAll("#y .fill").get(0);
        assertEquals(0xFF0000FF, xFill.computed().get(CssProperties.BACKGROUND_COLOR), "a named part of the matched host");
        assertEquals(0xFFFF0000, yFill.computed().get(CssProperties.BACKGROUND_COLOR), "not while the part is not hovered");
        assertEquals(0xFFFF0000, doc.querySelectorAll("#x .tip").get(0).computed().get(CssProperties.BACKGROUND_COLOR),
                "an element no part names stays the component's");

        yFill.setState(Element.HOVER, true);
        doc.frame(100, 100);
        assertEquals(0xFF00FF00, yFill.computed().get(CssProperties.BACKGROUND_COLOR), "a state after ::part() tests the part");
    }

    static final class Themed extends ViewModel {
        final Value<String> accent = state("#00f");
    }

    @Test
    void aLinkedThemeReachesEveryElementBelowTheComponentsOwnRules() {
        Themed m = new Themed();
        UiDocument doc = load("""
                <link href="btn.html"/><link href="page.css"/>
                <div class="ix-panel" bind:style:--accent="accent"><span class="ix-title">t</span><span class="muted">m</span>
                <ui-btn class="accent"></ui-btn></div>
                """, Map.of("page.css", """
                @stylesheet;
                @link "@ixchel/themes/dark.css";
                .ix-title { color: #0f0 } .muted { color: var(--ix-text-muted) } .accent { color: var(--accent) }
                """, "btn.html", """
                <link href="btn.css"/><component name="ui-btn"><button class="b">b</button></component>
                """, "btn.css", "@stylesheet; ui-btn button.b { background-color: #f00; color: var(--ix-accent) }"), m);
        doc.frame(100, 100);
        assertEquals(0xE0101418, doc.querySelectorAll(".ix-panel").get(0).computed().get(CssProperties.BACKGROUND_COLOR));
        assertEquals(0xFF00FF00, doc.querySelectorAll(".ix-title").get(0).computed().get(CssProperties.COLOR));
        assertEquals(0xFFAAAAAA, doc.querySelectorAll(".muted").get(0).computed().get(CssProperties.COLOR));
        Element b = doc.querySelectorAll("button").get(0);
        assertEquals(0xFFFF0000, b.computed().get(CssProperties.BACKGROUND_COLOR), "a component's rule beats the theme");
        assertEquals(0xFFD8BC52, b.computed().get(CssProperties.COLOR), "the theme's tokens reach inside");
        assertEquals(0xFF0000FF, doc.querySelectorAll("ui-btn").get(0).computed().get(CssProperties.COLOR));

        m.accent.set("#ff0");
        doc.frame(100, 100);
        assertEquals(0xFFFFFF00, doc.querySelectorAll("ui-btn").get(0).computed().get(CssProperties.COLOR));
    }

    @Test
    void aProjectThemeBuildsOnABaseAndBringsItsEffects() {
        UiDocument doc = load("""
                <link href="page.css"/>
                <div class="ix-panel"></div>
                """, Map.of("page.css", "@stylesheet; @link \"themes/main.css\";", "themes/main.css", """
                /* the host's theme */
                @partial;
                @link "@ixchel/themes/dark.css";
                @link "../fx/shine.ixeffect";
                :root { --ix-panel-bg: #123456 }
                .ix-panel { background-image: paint(Shine); --Shine-speed: 2 }
                """, "fx/shine.ixeffect", """
                kind box;
                param speed { syntax: <number>; inherits: false; initial-value: 1; }
                vec4 effect(IxBox b) { return vec4(speed); }
                """), new Styled());
        doc.frame(100, 100);
        Element panel = doc.querySelectorAll(".ix-panel").get(0);
        assertEquals(0xFF123456, panel.computed().get(CssProperties.BACKGROUND_COLOR), "the theme overrides its base");
        assertEquals(0xFFFFFFFF, panel.computed().get(CssProperties.COLOR), "the base's rules apply");
        assertEquals(List.of("t.html", "page.css", "themes/main.css", "fx/shine.ixeffect"), doc.files());
    }

    @Test
    void eachLinkMustNameTheKindItsPlaceExpects() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="part.css"/><link href="c.html"/><ui-c></ui-c>
                """, Map.of("part.css", "@partial;", "c.html", "<link href=\"p.html\"/><component name=\"ui-c\"></component>",
                "p.html", "<page></page>"), new Styled()));
        assertEquals(List.of("link/wrong-kind@c.html:1:1", "link/wrong-kind@t.html:1:1"), DiagCodes.of(e));

        UiLoadException component = assertThrows(UiLoadException.class, () -> UiDocument.load(
                "<component name=\"ui-c\"></component>", "t.html", Map.<String, String>of()::get,
                com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertEquals(List.of("link/wrong-kind@t.html:1:1"), DiagCodes.of(component));
    }
}
