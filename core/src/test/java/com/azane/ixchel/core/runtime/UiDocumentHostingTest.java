package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Event;
import com.azane.ixchel.core.binding.Feed;
import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.Param;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.TwoWay;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.binding.JavaComponent;
import com.azane.ixchel.core.view.Translations;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Java components and library components. */
class UiDocumentHostingTest {
    private static final String DIAL_FILE = "ui/dial.html";
    private static final String DIAL = """
            <component name="ui-dial" params="label level=5" events="picked">
            <span class="c" bind:text="caption"></span><span class="v" bind:text="level"></span>
            </component>
            """;
    private static final String LINK = "<link href=\"ui/dial.html\"/>\n";

    private final List<Dial> dials = new ArrayList<>();
    private long nowMs;

    static final class Dial extends ViewModel {
        final Param<String> label = param();
        final TwoWay<Integer> level = twoWay();
        final Event<Integer> picked = event();
        final Derived<String> caption = derive(label, l -> "[" + l + "]");
        final Action<Void> bump = action(() -> level.set(level.get() + 1));
        final Action<Void> pick = action(() -> picked.emit(level.get() * 10));
    }

    static final class Page extends ViewModel {
        final Value<String> name = state("n");
        final Value<Integer> total = state(3);
        final Value<Long> wide = state(1L);
        final Value<Integer> last = state(0);
        final Value<Boolean> shown = state(true);
        final Value<List<String>> rows = state(List.of("a", "b"));
    }

    private static UiDocument load(String html, Map<String, String> files, ViewModel model, UiHosting hosting) {
        return UiDocument.load(GoldenTest.page(html), "t.html", files::get, HtmlElements.TAGS, Translations.NONE, GoldenTest.MONO,
                GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT, hosting);
    }

    private static UiHosting hosting(String file, Class<? extends ViewModel> type, Supplier<? extends ViewModel> factory) {
        return UiHosting.of(UiRegistry.CORE, UiRegistry.host("t").component(file, new JavaComponent(type, factory)));
    }

    private UiHosting dial() {
        return UiHosting.of(UiRegistry.CORE, dialRegistry());
    }

    private UiRegistry dialRegistry() {
        return UiRegistry.host("t").component(DIAL_FILE, new JavaComponent(Dial.class, () -> {
            Dial d = new Dial();
            dials.add(d);
            return d;
        }));
    }

    private static Element byId(UiDocument doc, String id) {
        return doc.querySelectorAll("#" + id).get(0);
    }

    private void frameAt(UiDocument doc, long ms) {
        nowMs = ms;
        doc.frame(100, 100);
    }

    @Test
    void portsFollowTheUsersPathsAndATwoWayWritesBackToTheUsersState() {
        Page m = new Page();
        UiDocument doc = load(LINK + "<ui-dial id=\"d\" bind:label=\"name\" bind:level=\"total\"></ui-dial>",
                Map.of(DIAL_FILE, DIAL), m, dial());
        assertEquals("[n]3", byId(doc, "d").textContent());

        dials.get(0).bump.run(null);
        assertEquals(4, m.total.get(), "set writes through to the page's state");
        m.name.set("z");
        doc.frame(100, 100);
        assertEquals("[z]4", byId(doc, "d").textContent());
    }

    @Test
    void aTwoWayGivenNothingIsTheInstancesLocalStateStartingAtTheDefault() {
        Page m = new Page();
        UiDocument doc = load(LINK + """
                <ui-dial id="a" label="x"></ui-dial><ui-dial id="b" label="y" bind:level="total"></ui-dial>
                """, Map.of(DIAL_FILE, DIAL), m, dial());
        assertEquals("[x]5", byId(doc, "a").textContent());

        dials.get(0).bump.run(null);
        doc.frame(100, 100);
        assertEquals("[x]6", byId(doc, "a").textContent(), "a local write alone refreshes the instance");
        assertEquals(3, m.total.get());
        assertEquals("[y]3", byId(doc, "b").textContent());
    }

    @Test
    void somethingNotWritableGivenToATwoWayIsRejectedAtTheUse() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load(LINK + """
                <ui-dial label="x" bind:level="total + 1"></ui-dial>
                <ui-dial label="x" level="1"></ui-dial>
                """, Map.of(DIAL_FILE, DIAL), new Page(), dial()));
        assertEquals(List.of("component/read-only-two-way@t.html:2:32", "component/read-only-two-way@t.html:3:1"),
                DiagCodes.of(e));
    }

    static final class Mismatched extends ViewModel {
        final Param<String> given = param();
        final Param<String> undeclared = param();
        final Cell<String> plain = source(Source.polled(() -> "x"));
        final Event<Void> silent = event();
    }

    @Test
    void portsAndTheTemplatesSignatureMustMatchBothWaysBeforeAnythingIsBuilt() {
        int[] built = new int[1];
        String odd = """
                <component name="ui-odd" params="given plain missing" events="lost"></component>
                """;
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="odd.html"/>
                <ui-odd given="a" plain="b" missing="c"></ui-odd>
                """, Map.of("odd.html", odd), new Page(), hosting("odd.html", Mismatched.class, () -> {
            built[0]++;
            return new Mismatched();
        })));
        assertEquals(List.of("component/signature-mismatch@odd.html:1:40", "component/signature-mismatch@odd.html:1:46",
                "component/signature-mismatch@odd.html:1:1", "component/signature-mismatch@odd.html:1:1",
                "component/signature-mismatch@odd.html:1:1"), DiagCodes.of(e));
        assertEquals(0, built[0]);
    }

    static final class Stray extends ViewModel {
        final Param<String> given = param();
        private final Param<String> hidden = param();
        @SuppressWarnings("rawtypes")
        final Event raw = event();
    }

    static final class NoPort extends ViewModel {
        final Param<String> given = null;
    }

    @Test
    void aPortNoFieldHoldsIsReportedAndItsModelClosed() {
        List<Stray> made = new ArrayList<>();
        String file = "<component name=\"ui-stray\" params=\"given\"></component>";
        String use = "<link href=\"f.html\"/><ui-stray given=\"a\"></ui-stray>";
        UiLoadException e = assertThrows(UiLoadException.class, () -> load(use, Map.of("f.html", file), new Page(),
                hosting("f.html", Stray.class, () -> {
                    Stray s = new Stray();
                    made.add(s);
                    return s;
                })));
        assertEquals(List.of("component/unbound-port@f.html:1:1"), DiagCodes.of(e));
        assertTrue(made.get(0).scope().isClosed());

        UiLoadException none = assertThrows(UiLoadException.class, () -> load(use, Map.of("f.html", file), new Page(),
                hosting("f.html", NoPort.class, NoPort::new)));
        assertEquals(List.of("component/unbound-port@f.html:1:1"), DiagCodes.of(none));
    }

    static final class Eager extends ViewModel {
        final Param<String> given = param();
        final String read = given.get();
    }

    @Test
    void readingAParamInAConstructorIsReportedAtTheUse() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="f.html"/>
                <ui-eager given="a"></ui-eager>
                """, Map.of("f.html", "<component name=\"ui-eager\" params=\"given\"></component>"), new Page(),
                hosting("f.html", Eager.class, Eager::new)));
        assertEquals(List.of("binding/runtime-exception@t.html:2:1"), DiagCodes.of(e));
    }

    static final class Gauge extends ViewModel {
        final Param<Float> ratio = param();
        final Param<Float> whole = param();
        final Param<Float> scale = param();
        final TwoWay<Float> trim = twoWay();
        final Param<Long> count = param();
    }

    static final class Numbers extends ViewModel {
        final Value<Double> half = state(0.5);
        final Value<Integer> one = state(1);
    }

    private static final String GAUGE = """
            <component name="ui-gauge" params="ratio whole scale=2 trim=1 count=3">
            <span bind:text="ratio + whole + scale + trim"></span></component>
            """;

    @Test
    void numbersWidenToAParamsDeclaredTypeAndFollowTheirCell() {
        Numbers m = new Numbers();
        List<Gauge> made = new ArrayList<>();
        UiDocument doc = load("""
                <link href="g.html"/>
                <ui-gauge id="g" bind:ratio="half" bind:whole="one"></ui-gauge>
                """, Map.of("g.html", GAUGE), m, hosting("g.html", Gauge.class, () -> {
            Gauge g = new Gauge();
            made.add(g);
            return g;
        }));
        Gauge g = made.get(0);
        assertEquals(0.5f, g.ratio.get(), "a Double is taken as a Float");
        assertEquals(1f, g.whole.get(), "an Integer widens to a Float");
        assertEquals(2f, g.scale.get(), "a default widens too");
        assertEquals(1f, g.trim.get(), "so does a TwoWay's, which is the instance's own");
        assertEquals(3L, g.count.get());
        assertEquals("4.5", byId(doc, "g").textContent());

        m.half.set(0.25);
        doc.frame(100, 100);
        assertEquals(0.25f, g.ratio.get());
    }

    @Test
    void valuesThatDoNotFitAPortsDeclaredTypeAreRejected() {
        Map<String, String> files = Map.of("g.html", GAUGE, "bad.html",
                "<component name=\"ui-bad\" params=\"ratio whole='x' scale trim count\"></component>");
        UiRegistry java = UiRegistry.host("t").component("g.html", new JavaComponent(Gauge.class, Gauge::new))
                .component("bad.html", new JavaComponent(Gauge.class, Gauge::new));
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="g.html"/><link href="bad.html"/>
                <ui-gauge ratio="1" bind:whole="1"></ui-gauge>
                <ui-gauge bind:ratio="'x'" bind:whole="1"></ui-gauge>
                <ui-gauge bind:ratio="1" bind:whole="1" bind:trim="total"></ui-gauge>
                <ui-bad bind:ratio="1" bind:scale="1" bind:trim="wide" bind:count="1"></ui-bad>
                """, files, new Page(), UiHosting.of(UiRegistry.CORE, java)));
        assertEquals(List.of("component/param-type@t.html:2:1", "component/param-type@t.html:3:23",
                "component/param-type@t.html:4:52", "component/param-type@bad.html:1:46",
                "component/param-type@t.html:5:50"), DiagCodes.of(e));
    }

    static final class Picker extends ViewModel {
        final Event<Integer> picked = event();
        final Event<Void> done = event();
        final Event<Float> scaled = event();
        final Action<Void> pick = action(() -> {
            picked.emit(7);
            done.emit();
        });
    }

    @Test
    void eventsFromJavaAndFromTheTemplateReachTheListenerWithTheirPayload() {
        Page m = new Page();
        List<Picker> made = new ArrayList<>();
        UiDocument doc = load("""
                <link href="p.html"/><link href="s.css"/>
                <ui-picker on:picked="last = $event" on:done="total = total + 1" on:scaled="name = '' + $event"></ui-picker>
                """, Map.of("p.html", """
                <component name="ui-picker" events="picked done scaled"><button on:click="picked(9); scaled(1)"></button></component>
                """, "s.css", "@stylesheet; ui-picker, button { width: 20px; height: 10px; padding: 0; border-style: none }"), m, hosting("p.html", Picker.class, () -> {
            Picker p = new Picker();
            made.add(p);
            return p;
        }));
        doc.frame(100, 100);
        made.get(0).pick.run(null);
        assertEquals(7, m.last.get(), "emit reaches on: with the payload as $event");
        assertEquals(4, m.total.get());

        doc.events().mousePressed(5, 5, 0, 0);
        doc.events().mouseReleased(5, 5, 0, 0);
        assertEquals(9, m.last.get(), "a view call fires the same listener");
        assertEquals("1.0", m.name.get(), "a view call widens to the declared payload");
    }

    @Test
    void aTemplateCallMustFitTheDeclaredPayload() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="p.html"/>
                <ui-picker></ui-picker>
                """, Map.of("p.html", """
                <component name="ui-picker" events="picked done scaled"><button on:click="picked('x'); picked; done(1)"></button></component>
                """), new Page(), hosting("p.html", Picker.class, Picker::new)));
        assertEquals(List.of("binding/event-payload@p.html:1:75", "binding/event-payload@p.html:1:88",
                "binding/event-payload@p.html:1:96"), DiagCodes.of(e));
    }

    @Test
    void aChildModelSurvivesItsCopysExitAndClosesWhenTheCopyIsRemoved() {
        Page m = new Page();
        m.shown.set(false);
        UiDocument doc = load(LINK + """
                <link href="s.css"/>
                <if test="shown"><ui-dial id="d" label="x"></ui-dial></if>
                """, Map.of(DIAL_FILE, DIAL, "s.css", """
                @stylesheet;
                ui-dial { width: 40px; height: 10px; transition: width 100ms linear }
                ui-dial:-ix-absent { width: 0 }
                """), m, dial());
        doc.clock(() -> nowMs * 1_000_000);
        frameAt(doc, 0);
        assertTrue(dials.isEmpty(), "an unmounted copy is only checked, nothing is built");
        m.shown.set(true);
        frameAt(doc, 0);
        frameAt(doc, 10);
        frameAt(doc, 110);
        frameAt(doc, 120);
        Dial d = dials.get(0);

        m.shown.set(false);
        frameAt(doc, 200);
        frameAt(doc, 250);
        assertFalse(d.scope().isClosed(), "it stays through the exit");
        m.shown.set(true);
        frameAt(doc, 250);
        assertFalse(d.scope().isClosed(), "taken back, it is the same copy");
        assertEquals(1, dials.size());

        m.shown.set(false);
        frameAt(doc, 400);
        frameAt(doc, 500);
        frameAt(doc, 510);
        assertTrue(doc.querySelectorAll("#d").isEmpty());
        assertTrue(d.scope().isClosed());
    }

    static final class Panel extends ViewModel {
    }

    @Test
    void aRemovedCopyClosesTheInstancesNestedInsideIt() {
        Page m = new Page();
        List<Panel> panels = new ArrayList<>();
        UiRegistry java = dialRegistry().component("ui/panel.html", new JavaComponent(Panel.class, () -> {
            Panel p = new Panel();
            panels.add(p);
            return p;
        }));
        UiDocument doc = load("""
                <link href="ui/panel.html"/>
                <for each="rows" as="r" key="r"><div><if test="shown"><ui-panel></ui-panel></if></div></for>
                """, Map.of(DIAL_FILE, DIAL, "ui/panel.html", """
                <component name="ui-panel"><link href="dial.html"/><ui-dial label="x"></ui-dial></component>
                """), m, UiHosting.of(UiRegistry.CORE, java));
        assertEquals(2, panels.size());
        assertEquals(2, dials.size());

        m.rows.set(List.of("b"));
        doc.frame(100, 100);
        assertTrue(panels.get(0).scope().isClosed());
        assertTrue(dials.get(0).scope().isClosed(), "an instance inside an instance inside a block of the row");
        assertFalse(panels.get(1).scope().isClosed());
        assertFalse(dials.get(1).scope().isClosed());
    }

    @Test
    void closingTheDocumentClosesItsChildrenNotThePageAndEndsItsBindings() {
        Page m = new Page();
        m.shown.set(false);
        UiDocument doc = load(LINK + """
                <ui-dial label="x" on:picked="last = $event"></ui-dial>
                <if test="shown"><ui-dial label="y"></ui-dial></if>
                """, Map.of(DIAL_FILE, DIAL), m, dial());
        Dial d = dials.get(0);
        doc.close();
        doc.close();
        assertTrue(d.scope().isClosed());
        assertFalse(m.scope().isClosed());

        d.picked.emit(1);
        assertEquals(0, m.last.get(), "a closed model's events do nothing");
        m.shown.set(true);
        doc.frame(100, 100);
        assertEquals(1, dials.size(), "a closed document mounts nothing");
    }

    @Test
    void aLoadThatFailsClosesTheChildrenItBuilt() {
        assertThrows(UiLoadException.class, () -> load(LINK + "<ui-dial label=\"x\"></ui-dial><span bind:text=\"nope\"></span>",
                Map.of(DIAL_FILE, DIAL), new Page(), dial()));
        assertTrue(dials.get(0).scope().isClosed());
    }

    static final class Reader extends ViewModel {
        final Param<String> at = param();
        final Cell<String> value;

        Reader(Feed<String, String> feed) {
            value = source(feed, at);
        }
    }

    static final class FeedPage extends ViewModel {
        final Value<String> which = state("a");
        final Value<Boolean> shown = state(true);
        final Cell<String> own;

        FeedPage(Feed<String, String> feed) {
            own = source(feed, "a");
        }
    }

    private static final String READER = "<component name=\"ui-reader\" params=\"at\"><span bind:text=\"value\"></span></component>";

    @Test
    void aFeedIsReadOncePerPassHoweverManyModelsAndInstancesUseIt() {
        List<String> reads = new ArrayList<>();
        Feed<String, String> feed = Feed.polled(k -> {
            reads.add(k);
            return k.toUpperCase();
        });
        FeedPage m = new FeedPage(feed);
        UiDocument doc = load("""
                <link href="r.html"/>
                <ui-reader id="a" bind:at="which"></ui-reader><ui-reader id="b" at="a"></ui-reader>
                """, Map.of("r.html", READER), m, hosting("r.html", Reader.class, () -> new Reader(feed)));
        assertEquals(List.of("a"), reads);
        assertEquals("A", byId(doc, "a").textContent());

        doc.frame(100, 100);
        assertEquals(List.of("a", "a"), reads, "a new pass reads once more");

        m.which.set("b");
        doc.frame(100, 100);
        assertEquals(List.of("a", "a", "a", "b"), reads);
        assertEquals("B", byId(doc, "a").textContent(), "a Param key moves the reference to the new instance");
        assertEquals("A", byId(doc, "b").textContent());
    }

    @Test
    void anOpenedFeedClosesWithTheLastInstanceUsingIt() {
        List<String> log = new ArrayList<>();
        Feed<String, String> feed = Feed.opened(k -> {
            log.add("open " + k);
            return () -> log.add("close " + k);
        }, r -> "v");
        FeedPage m = new FeedPage(Feed.polled(k -> k));
        UiDocument doc = load("""
                <link href="r.html"/>
                <if test="shown"><ui-reader bind:at="which"></ui-reader><ui-reader bind:at="which"></ui-reader></if>
                """, Map.of("r.html", READER), m, hosting("r.html", Reader.class, () -> new Reader(feed)));
        assertEquals(List.of("open a"), log);

        m.which.set("b");
        doc.frame(100, 100);
        assertEquals(List.of("open a", "open b", "close a"), log, "a closes when the second model moves off it");

        m.shown.set(false);
        doc.frame(100, 100);
        assertEquals(List.of("open a", "open b", "close a", "close b"), log);
    }

    @Test
    void aComponentInAHiddenTooltipIsNotPolledUntilItShowsAndThenShowsCurrentValuesThatFrame() {
        Map<String, String> live = new HashMap<>(Map.of("a", "A"));
        List<String> reads = new ArrayList<>();
        Feed<String, String> feed = Feed.polled(k -> {
            reads.add(k);
            return live.get(k);
        });
        UiDocument doc = load("""
                <link href="r.html"/>
                <div>x<ix-tooltip id="tip"><ui-reader id="r" at="a"></ui-reader></ix-tooltip></div>
                """, Map.of("r.html", READER), new FeedPage(Feed.polled(k -> k)), hosting("r.html", Reader.class, () -> new Reader(feed)));
        doc.frame(100, 100);
        assertEquals(List.of(), reads, "not even the first poll while hidden");

        live.put("a", "A2");
        doc.setTooltip(byId(doc, "tip"), 0, 0);
        doc.frame(100, 100);
        assertEquals(List.of("a"), reads);
        assertEquals("A2", byId(doc, "r").textContent(), "the frame it shows in");

        doc.setTooltip(null, 0, 0);
        doc.frame(100, 100);
        doc.frame(100, 100);
        int whileShown = reads.size();
        live.put("a", "A3");
        doc.frame(100, 100);
        assertEquals(whileShown, reads.size(), "hidden again: no more reads");
        doc.setTooltip(byId(doc, "tip"), 0, 0);
        doc.frame(100, 100);
        assertEquals("A3", byId(doc, "r").textContent());
    }

    @Test
    void aComponentInAClosedDialogIsPolledInThePassABindingOpensIt() {
        List<String> reads = new ArrayList<>();
        Feed<String, String> feed = Feed.polled(k -> {
            reads.add(k);
            return k.toUpperCase();
        });
        FeedPage m = new FeedPage(Feed.polled(k -> k));
        m.shown.set(false);
        UiDocument doc = load("""
                <link href="r.html"/>
                <dialog bind:open="shown"><ui-reader id="r" at="d"></ui-reader></dialog>
                """, Map.of("r.html", READER), m, hosting("r.html", Reader.class, () -> new Reader(feed)));
        doc.frame(100, 100);
        assertEquals(List.of(), reads);

        m.shown.set(true);
        doc.frame(100, 100);
        assertEquals(List.of("d"), reads);
        assertEquals("D", byId(doc, "r").textContent());
    }

    @UiView("@ixchel/test/lib/mc-dial.html")
    static final class LibDial extends ViewModel {
        final Param<String> label = param();
        final TwoWay<Integer> level = twoWay();
        final Event<Integer> picked = event();
        final Derived<String> caption = derive(label, l -> "[" + l + "]");
    }

    /** Test files under {@code @ixchel/test/}, read from {@code ui/} next to this class. */
    private static final UiHosting LIBRARY = UiHosting.of(UiRegistry.CORE, UiRegistry.library()
            .root("test/", UiDocumentHostingTest.class, "ui/")
            .component(LibDial.class, LibDial::new));

    @Test
    void libraryComponentsLinkTheirOwnSheetsAndLoadOnce() {
        String badge = "<link href=\"@ixchel/test/lib/badge.html\"/>";
        UiDocument doc = load(badge + """
                <link href="@ixchel/test/lib/mc-dial.html"/><link href="wrap.html"/>
                <ix-badge id="a"></ix-badge><ix-badge id="b" note="b"></ix-badge><ui-wrap id="w"></ui-wrap>
                <mc-dial id="d" label="x"></mc-dial>
                """, Map.of("wrap.html", "<component name=\"ui-wrap\">" + badge + "<ix-badge note=\"w\"></ix-badge></component>"),
                new Page(), LIBRARY);
        doc.frame(100, 100);
        assertEquals("?", byId(doc, "a").textContent());
        assertEquals("w", byId(doc, "w").textContent());
        assertEquals(7, byId(doc, "a").box().width(), "its sibling badge.css joins the component layer");
        assertEquals("[x]5", byId(doc, "d").textContent(), "a library component can have a view model");
        assertEquals(List.of("t.html", "wrap.html"), doc.files(), "library files are not the host's to watch");
    }

    @Test
    void aLibraryComponentIsUnknownUntilLinked() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<ix-badge></ix-badge>", Map.of(),
                new Page(), LIBRARY));
        assertEquals(List.of("view/unknown-tag@t.html:1:7"), DiagCodes.of(e));
    }

    @Test
    void reservedPrefixesStayRejectedForProjectFiles() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <link href="ix.html"/><link href="mc.html"/>
                <ix-other></ix-other>
                """, Map.of("ix.html", "<component name=\"ix-mine\"></component>", "mc.html", "<component name=\"mc-mine\"></component>"),
                new Page(), LIBRARY));
        assertEquals(List.of("view/invalid-component-name@ix.html:1:1", "view/invalid-component-name@mc.html:1:1",
                "view/unknown-tag@t.html:2:1"), DiagCodes.of(e));
    }

    @com.azane.ixchel.core.view.UiElement(tag = "mc-mine")
    static final class McMine {
    }

    /** Fails if a host could define a library name or file, which every other host would then see under its name. */
    @Test
    void aHostRegistryDefinesNoLibraryNamesOrFiles() {
        assertThrows(IllegalArgumentException.class, () -> UiRegistry.host("t").element(McMine.class, null));
        assertThrows(IllegalArgumentException.class, () -> UiRegistry.host("t").component(LibDial.class, LibDial::new));
        assertThrows(IllegalArgumentException.class, () -> UiRegistry.host("t").root("t/", UiDocumentHostingTest.class, "ui/"));
        assertThrows(IllegalArgumentException.class, () -> UiHosting.of(UiRegistry.CORE, UiRegistry.CORE));
    }

    /** Fails if a host file makes up an ix- name unnoticed, or a component's stylesheet setting tokens on :root goes unwarned. */
    @Test
    void hostFilesUseOnlyTheLibrarysIxNames() {
        Map<String, String> files = Map.of(
                "t.css", "@stylesheet;\n.ix-panel, .ix-pannel { --ix-panel-bg: red; --ix-panel-bgg: red; color: var(--ix-nope, red) }",
                "c.html", "<component name=\"ui-c\"><link href=\"c.css\"/></component>",
                "c.css", "@stylesheet;\n:root { --c-gap: 1px }");
        String page = GoldenTest.page("<link href=\"t.css\"/><link href=\"c.html\"/>\n<ui-c></ui-c><div style=\"--ix-typo: 1\"></div>");
        com.azane.ixchel.core.style.LibraryNames vocabulary = com.azane.ixchel.core.style.LibraryNames.of(
                UiHosting.NONE.library(), List.of("@ixchel/themes/base.css", "@ixchel/themes/dark.css"));
        assertEquals(List.of("style/component-root-token@c.css:2:9",
                "style/unknown-library-class@t.css:2:12", "style/unknown-library-token@t.css:2:45",
                "style/unknown-library-token@t.css:2:77", "style/unknown-library-token@t.html:2:26"),
                DiagCodes.of(UiDocument.lint(page, "t.html", files::get, HtmlElements.TAGS, Translations.NONE, null, Set.of(),
                        UiHosting.NONE, vocabulary)));
        assertEquals(List.of("style/component-root-token@c.css:2:9"), DiagCodes.of(UiDocument.lint(page, "t.html", files::get,
                HtmlElements.TAGS, Translations.NONE, null, Set.of(), UiHosting.NONE, null)), "without the vocabulary");
    }

    @Test
    void lintChecksAJavaComponentFromItsClassWithoutAFactory() {
        Map<String, String> files = Map.of("odd.html", "<component name=\"ui-odd\" params=\"given\"></component>", DIAL_FILE,
                DIAL.replace("bind:text=\"caption\"", "bind:text=\"nope\""));
        UiHosting hosting = UiHosting.of(UiRegistry.CORE, UiRegistry.host("t")
                .component("odd.html", new JavaComponent(Mismatched.class, null))
                .component(DIAL_FILE, new JavaComponent(Dial.class, null)));
        var diag = UiDocument.lint(GoldenTest.page("""
                <link href="odd.html"/><link href="ui/dial.html"/>
                <ui-odd given="a"></ui-odd>
                <ui-dial bind:label="total" bind:level="total + 1"></ui-dial>
                <if test="shown"><ui-dial label="x"></ui-dial></if>
                """), "t.html", files::get, HtmlElements.TAGS, Translations.NONE, ModelShape.of(Page.class), Set.of(), hosting, null);
        assertEquals(List.of("component/signature-mismatch@odd.html:1:1", "component/signature-mismatch@odd.html:1:1",
                "component/param-type@t.html:3:22", "component/read-only-two-way@t.html:3:41",
                "binding/unknown-name@ui/dial.html:2:28"), DiagCodes.of(diag));
    }
}
