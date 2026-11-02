package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentBindingTest {
    private static UiDocument load(String html, String css, ViewModel model) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), TagRegistry.HTML,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
    }

    static final class Ammo extends ViewModel {
        int live = 30;
        int textComputations;
        final Cell<Integer> loaded = source(Source.polled(() -> live));
        final Derived<String> text = derive(loaded, n -> {
            textComputations++;
            return String.format("%03d", n);
        });
        final Derived<Boolean> isEmpty = derive(loaded, n -> n == 0);
    }

    @Test
    void sourceChangeRewritesTextAndClassAndAnUnchangedFrameDoesNoWork() {
        Ammo m = new Ammo();
        UiDocument doc = load("<span class=\"n\" bind:class:empty=\"isEmpty\" bind:text=\"text\"></span>",
                ".empty { color: #f00 }", m);
        assertEquals("text 0 0 9 #FFFFFFFF \"030\"", GoldenTest.print(doc.frame(100, 100)));
        long version = doc.frame(100, 100).version();
        assertEquals(1, m.textComputations);

        m.live = 0;
        assertEquals("text 0 0 9 #FFFF0000 \"000\"", GoldenTest.print(doc.frame(100, 100)));
        long changed = doc.frame(100, 100).version();
        assertTrue(changed > version);
        assertEquals(changed, doc.frame(100, 100).version());
        assertEquals(2, m.textComputations);
    }

    static final class Mutable extends ViewModel {
        final int[] live = {2};
        int downstream;
        final Cell<int[]> counts = source(Source.polled(() -> live, int[]::clone, Arrays::equals));
        final Derived<Boolean> even = derive(counts, c -> c[0] % 2 == 0);
        final Derived<String> label = derive(even, e -> {
            downstream++;
            return e ? "even" : "odd";
        });
        final Value<String> suffix = state("!");
        final Derived<String> shown = derive(label, suffix, (l, s) -> l + s);
    }

    @Test
    void inPlaceMutationIsSeenThroughTheSnapshotAndAnEqualResultStopsDownstream() {
        Mutable m = new Mutable();
        UiDocument doc = load("<span bind:text=\"shown\"></span>", "", m);
        assertEquals("text 0 0 9 #FFFFFFFF \"even!\"", GoldenTest.print(doc.frame(100, 100)));

        m.live[0] = 4;
        doc.frame(100, 100);
        assertEquals(1, m.downstream, "even stayed true, so label must not recompute");

        m.live[0] = 5;
        assertEquals("text 0 0 9 #FFFFFFFF \"odd!\"", GoldenTest.print(doc.frame(100, 100)));

        m.suffix.set("?");
        assertEquals("text 0 0 9 #FFFFFFFF \"odd?\"", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(2, m.downstream);
    }

    static final class Failing extends ViewModel {
        int live = 1;
        final Cell<Integer> n = source(Source.polled(() -> live));
        final Derived<Integer> inverse = derive(n, x -> {
            if (x <= 0) {
                throw new IllegalStateException("no ammo");
            }
            return 10 / x;
        });
    }

    @Test
    void aThrowingDerivedKeepsTheLastTextAndIsReportedOnce() {
        Failing m = new Failing();
        UiDocument doc = load("<span bind:text=\"inverse\"></span>", "", m);
        m.live = 0;
        assertEquals("text 0 0 9 #FFFFFFFF \"10\"", GoldenTest.print(doc.frame(100, 100)));
        m.live = -1;
        doc.frame(100, 100);
        assertEquals(List.of("binding/runtime-exception@t.html:2:18"), DiagCodes.of(doc.diagnostics()));

        m.live = 2;
        assertEquals("text 0 0 9 #FFFFFFFF \"5\"", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void loadReportsEveryBadBindingWithItsPosition() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <div bind:text="missing"></div>
                <div bind:class:x="loaded"></div>
                <div bind:text="text.length"></div>
                <span bind:text="text">x</span>
                <div bind:style="text"></div>
                <div bind:href="text"></div>
                """, "", new Ammo()));
        assertEquals(List.of("binding/unknown-name@t.html:2:17", "binding/value-type@t.html:3:20",
                "binding/unknown-member@t.html:4:22", "binding/text-not-empty@t.html:5:7",
                "binding/incomplete-target@t.html:6:6", "binding/unknown-target@t.html:7:6"), DiagCodes.of(e));
    }

    @Test
    void aNameInADocumentWithoutAViewModelIsUnknown() {
        UiLoadException none = assertThrows(UiLoadException.class,
                () -> UiDocument.load("<span bind:text=\"x\"></span>", "t.html", GoldenTest.NO_SHEETS, TagRegistry.HTML,
                        GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertEquals(List.of("binding/unknown-name@t.html:1:18"), DiagCodes.of(none));
    }

    static final class Held extends ViewModel {
        Object live = "sword";
        final Cell<Object> stack = source(Source.polled(() -> live, v -> v, (a, b) -> a == b));
    }

    @Test
    void aNativeElementPaintsItsBoundPayloadAndOnlyABindingCanSetIt() {
        TagRegistry tags = TagRegistry.HTML.extend().nativeTag("item", "item").build();
        Held m = new Held();
        UiDocument doc = UiDocument.load(GoldenTest.linked("<item class=\"i\" bind:item=\"stack\"></item>"), "t.html",
                GoldenTest.sheet(".i { width: 16px; height: 16px }"), tags,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        assertEquals("item 0 0 16 16 sword", GoldenTest.print(doc.frame(100, 100)));

        m.live = "bow";
        assertEquals("item 0 0 16 16 bow", GoldenTest.print(doc.frame(100, 100)));
        m.live = null;
        assertEquals("", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void aNativeElementsPayloadIsNotAnAttribute() {
        TagRegistry tags = TagRegistry.HTML.extend().nativeTag("item", "item").build();
        UiLoadException e = assertThrows(UiLoadException.class, () -> UiDocument.load(
                "<item item=\"sword\"></item>", "t.html", GoldenTest.NO_SHEETS, tags, GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertEquals(List.of("template/unknown-attribute@t.html:1:7"), DiagCodes.of(e));
    }

    record Row(String name, int count) {
    }

    static final class Picker extends ViewModel {
        final List<String> sent = new ArrayList<>();
        int detailReads;
        CompletableFuture<Void> reply;
        final Value<Row> row = state(new Row("ammo", 1));
        final Value<String> selected = state("none");
        final Value<List<Row>> rows = state(List.of(new Row("a", 1), new Row("b", 2)));
        final Value<Boolean> show = state(false);
        final Derived<String> detail = derive(selected, s -> {
            detailReads++;
            return "sel " + s;
        });
        final Action<Row> select = action(r -> selected.set(r.name()));
        final Action<Void> reload = task(() -> {
            sent.add("reload");
            reply = new CompletableFuture<>();
            return reply;
        });
    }

    @Test
    void aPathReadsARecordComponentAndAnEqualComponentRepaintsNothing() {
        Picker m = new Picker();
        UiDocument doc = load("<span bind:text=\"row.name\"></span>", "", m);
        assertEquals("text 0 0 9 #FFFFFFFF \"ammo\"", GoldenTest.print(doc.frame(100, 100)));
        long version = doc.frame(100, 100).version();
        m.row.set(new Row("ammo", 2));
        assertEquals(version, doc.frame(100, 100).version(), "count is not bound");
        m.row.set(new Row("fuel", 2));
        assertEquals("text 0 0 9 #FFFFFFFF \"fuel\"", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void aClickRunsTheActionWithItsArgumentAndTheChangeShowsNextFrame() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <div class="b" on:click="select(row)"></div><span bind:text="selected"></span>
                """, ".b { width: 10px; height: 10px }", m);
        doc.frame(100, 100);
        assertTrue(doc.events().mousePressed(5, 5, 0, 0));
        assertTrue(doc.events().mouseReleased(5, 5, 0, 0));
        assertEquals("text 0 10 9 #FFFFFFFF \"ammo\"", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void aTaskIsPendingUntilItsReplyArrivesAndAfterCloseLateRepliesAreDropped() throws Exception {
        Picker m = new Picker();
        UiDocument doc = load("<div class=\"b\" on:click=\"reload\" bind:class:busy=\"reload.pending\"></div>",
                ".b { width: 10px; height: 10px } .busy { background-color: #fff }", m);
        List<String> notified = new ArrayList<>();
        m.subscribe(m.reload.pending(), () -> notified.add("pending=" + m.reload.pending().get()));
        doc.frame(100, 100);

        click(doc);
        click(doc);
        assertEquals(List.of("reload"), m.sent, "a second click while pending is dropped");
        assertEquals("rect 0 0 10 10 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));

        Thread t = new Thread(() -> m.reply.completeExceptionally(new IllegalStateException("no ammo")));
        t.start();
        t.join();
        assertEquals("", GoldenTest.print(doc.frame(100, 100)), "the reply is applied on the next frame");
        assertEquals("no ammo", m.reload.error().get().getMessage());
        assertEquals(List.of("pending=true", "pending=false"), notified);

        click(doc);
        assertEquals(null, m.reload.error().get(), "running again clears the error");
        doc.frame(100, 100);
        m.close();
        m.reply.complete(null);
        doc.frame(100, 100);
        assertEquals(true, m.reload.pending().get(), "a reply after close is dropped");
        assertEquals(List.of("pending=true", "pending=false", "pending=true"), notified);
        click(doc);
        assertEquals(List.of("reload", "reload"), m.sent, "a closed model runs no actions");
    }

    private static void click(UiDocument doc) {
        doc.events().mousePressed(5, 5, 0, 0);
        doc.events().mouseReleased(5, 5, 0, 0);
    }

    @Test
    void loadReportsEveryBadHandler() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <div on:hover="reload"></div>
                <div on:click="row"></div>
                <div on:click="select"></div>
                <div on:click="reload(row)"></div>
                <div on:click="select(selected)"></div>
                <div bind:text="reload"></div>
                """, "", new Picker()));
        assertEquals(List.of("binding/unknown-event@t.html:2:6", "binding/not-callable@t.html:3:16",
                "binding/argument-arity@t.html:4:16", "binding/argument-arity@t.html:5:16",
                "binding/argument-type@t.html:6:23", "binding/not-a-value@t.html:7:17"), DiagCodes.of(e));
    }

    static final class Keyed extends ViewModel {
        final List<String> log = new ArrayList<>();
        final Action<Void> up = action(() -> log.add("up"));
        final Action<Void> shiftUp = action(() -> log.add("shift-up"));
        final Action<Void> released = action(() -> log.add("released"));
    }

    @Test
    void aKeyFilterRunsTheHandlerForTheNamedKeyAndExactModifiersOnlyAndConsumesJustThose() {
        Keyed m = new Keyed();
        UiDocument doc = load("""
                <div class="k"><div class="k" tabindex="0" on:keydown.up="up" on:keydown.up.shift="shiftUp" on:keyup="released"></div></div>
                """, ".k { width: 10px; height: 10px }", m);
        doc.frame(100, 100);
        assertFalse(doc.events().keyPressed(Keys.code("up"), 0), "a nested element only sees keys while focused");
        click(doc);

        assertTrue(doc.events().keyPressed(Keys.code("up"), 0));
        assertFalse(doc.events().keyPressed(Keys.code("down"), 0), "another key is not consumed");
        assertTrue(doc.events().keyPressed(Keys.code("up"), Keys.MOD_SHIFT), "Shift+Up runs only the Shift handler");
        assertFalse(doc.events().keyPressed(Keys.code("up"), Keys.MOD_SHIFT | Keys.MOD_CTRL),
                "an extra modifier matches neither handler");
        assertTrue(doc.events().keyPressed(Keys.code("up"), Keys.MOD_SUPER), "Super is not a filter modifier");
        assertTrue(doc.events().keyReleased(Keys.code("a"), Keys.MOD_ALT), "an unfiltered handler fires for every key");
        assertEquals(List.of("up", "shift-up", "up", "released"), m.log);
    }

    @Test
    void keyHandlersOnATopLevelElementFireWithoutFocusAndWhenAChildIsFocused() {
        Keyed m = new Keyed();
        UiDocument doc = load("""
                <div on:keydown.up="up"><div class="k" tabindex="0"></div></div><div class="k" tabindex="0"></div>
                """, ".k { width: 10px; height: 10px }", m);
        doc.frame(100, 100);
        assertTrue(doc.events().keyPressed(265, 0), "nothing focused: the handler acts on the document");
        click(doc);
        assertTrue(doc.events().keyPressed(265, 0), "a focused child bubbles to it");
        doc.events().mousePressed(5, 15, 0, 0);
        doc.events().mouseReleased(5, 15, 0, 0);
        assertTrue(doc.events().keyPressed(265, 0), "focus in a sibling still reaches a document-level handler");
        assertEquals(List.of("up", "up", "up"), m.log);
    }

    @Test
    void loadReportsBadEventFilters() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <div on:keydown.meta="up"></div>
                <div on:keydown.up.super="up"></div>
                <div on:click.up="up"></div>
                <div on:keyup.f13="up"></div>
                <div on:click.right.left="up"></div>
                <div on:wheel.shift="up"></div>
                """, "", new Keyed()));
        assertEquals(List.of("binding/unknown-filter@t.html:2:6", "binding/unknown-filter@t.html:3:6",
                "binding/unknown-filter@t.html:4:6", "binding/unknown-filter@t.html:5:6",
                "binding/unknown-filter@t.html:6:6", "binding/unexpected-filter@t.html:7:6"), DiagCodes.of(e));
    }

    static final class Clicks extends ViewModel {
        final List<String> log = new ArrayList<>();
        final Action<Void> any = action(() -> log.add("any"));
        final Action<Void> right = action(() -> log.add("right"));
        final Action<Void> shift = action(() -> log.add("shift"));
        final Action<Void> down = action(() -> log.add("down"));
    }

    @Test
    void mouseFiltersPickTheButtonAndExactModifiersAndPlainClickIsAnyLeftClick() {
        Clicks m = new Clicks();
        UiDocument doc = load("""
                <div class="k" on:click="any" on:click.right="right" on:click.shift="shift" on:mousedown="down"></div>
                """, ".k { width: 10px; height: 10px }", m);
        doc.frame(100, 100);
        int[][] presses = {{0, 0}, {1, 0}, {0, Keys.MOD_SHIFT}, {1, Keys.MOD_SHIFT}, {2, 0}};
        for (int[] p : presses) {
            doc.events().mousePressed(5, 5, p[0], p[1]);
            doc.events().mouseReleased(5, 5, p[0], p[1]);
        }
        assertEquals(List.of("down", "any", "down", "right", "down", "any", "shift", "down", "down"), m.log,
                "Shift+right matches no filter; a middle click only the unfiltered mousedown");
    }

    @Test
    void eachKeepsRowNodesByKeyAndPlacesRowsInListOrderBetweenStaticSiblings() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <span>[</span><for each="rows" as="r" key="r.name"><span class="r" on:click="select(r)" bind:text="r.count"></span></for><span>]</span>
                """, ".r { width: 10px; height: 10px }", m);
        assertEquals("""
                text 0 0 9 #FFFFFFFF "["
                text 0 9 9 #FFFFFFFF "1"
                text 0 19 9 #FFFFFFFF "2"
                text 0 29 9 #FFFFFFFF "]\"""", GoldenTest.print(doc.frame(100, 100)));
        Element a = (Element) doc.root().children().get(1);

        m.rows.set(List.of(new Row("c", 3), new Row("b", 2), new Row("a", 5)));
        assertEquals("""
                text 0 0 9 #FFFFFFFF "["
                text 0 9 9 #FFFFFFFF "3"
                text 0 19 9 #FFFFFFFF "2"
                text 0 29 9 #FFFFFFFF "5"
                text 0 39 9 #FFFFFFFF "]\"""", GoldenTest.print(doc.frame(100, 100)));
        assertSame(a, doc.root().children().get(3), "row 'a' kept its node and only took the new value");

        doc.events().mousePressed(5, 32, 0, 0);
        doc.events().mouseReleased(5, 32, 0, 0);
        m.rows.set(List.of());
        assertEquals("text 0 0 9 #FFFFFFFF \"[\"\ntext 0 9 9 #FFFFFFFF \"]\"", GoldenTest.print(doc.frame(100, 100)));
        assertEquals("a", m.selected.get(), "the handler passed its own row");
    }

    @Test
    void eachIndexIsTheRowsPositionAndFollowsAKeptRowWhenTheListReorders() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <for each="rows" as="r" key="r.name" index="i"><span bind:text="i + r.name"></span></for>
                """, "", m);
        assertEquals("""
                text 0 0 9 #FFFFFFFF "0a"
                text 0 9 9 #FFFFFFFF "1b\"""", GoldenTest.print(doc.frame(100, 100)));
        Element b = (Element) doc.root().children().get(1);

        m.rows.set(List.of(new Row("c", 3), new Row("b", 2)));
        assertEquals("""
                text 0 0 9 #FFFFFFFF "0c"
                text 0 9 9 #FFFFFFFF "1b\"""", GoldenTest.print(doc.frame(100, 100)));
        assertSame(b, doc.root().children().get(1), "row 'b' kept its node at the same index");

        m.rows.set(List.of(new Row("b", 2), new Row("c", 3)));
        assertEquals("""
                text 0 0 9 #FFFFFFFF "0b"
                text 0 9 9 #FFFFFFFF "1c\"""", GoldenTest.print(doc.frame(100, 100)));
        assertSame(b, doc.root().children().get(0), "the kept row moved and took its new index");
    }

    @Test
    void loadReportsAnIndexNamedLikeTheRowOrHidingAName() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <for each="rows" as="r" key="r.name" index="r"></for>
                <for each="rows" as="r" key="r.name" index="row"></for>
                """, "", new Picker()));
        assertEquals(List.of("binding/invalid-name@t.html:2:45", "binding/hidden-name@t.html:3:45"), DiagCodes.of(e));
    }

    @Test
    void ifMountsWhileTrueAndUnmountedContentIsNotRead() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <if test="show"><div><span bind:text="detail"></span></div></if><if test="!show"><div><span>none</span></div></if>
                """, "", m);
        assertEquals("text 0 0 9 #FFFFFFFF \"none\"", GoldenTest.print(doc.frame(100, 100)));
        m.selected.set("x");
        doc.frame(100, 100);
        assertEquals(0, m.detailReads);

        m.show.set(true);
        assertEquals("text 0 0 9 #FFFFFFFF \"sel x\"", GoldenTest.print(doc.frame(100, 100)), "mounted before the else branch");
        assertEquals(1, m.detailReads);
        m.show.set(false);
        m.selected.set("y");
        assertEquals("text 0 0 9 #FFFFFFFF \"none\"", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(1, m.detailReads);
    }

    @Test
    void loadReportsBadBlocksAndBadBindingsInsideUnmountedContent() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <for each="rows"></for>
                <for each="row" as="r" key="r"></for>
                <if test="selected"></if>
                <for each="rows" as="r" key="row.name"></for>
                <if test="show" class="x"></if>
                <if test="show"><span bind:text="nope"></span></if>
                <for each="rows" as="r" key="r.name"><span bind:text="r.missing"></span></for>
                <for each="rows" as="rows" key="rows"></for>
                """, "", new Picker()));
        assertEquals(List.of("template/missing-block-attribute@t.html:2:1", "template/missing-block-attribute@t.html:2:1",
                "template/unknown-block-attribute@t.html:6:17", "binding/value-type@t.html:3:12", "binding/value-type@t.html:4:11",
                "binding/invalid-key@t.html:5:30", "binding/unknown-name@t.html:7:34",
                "binding/unknown-member@t.html:8:57", "binding/hidden-name@t.html:9:22"), DiagCodes.of(e));
    }

    @Test
    void expressionsFollowTheirPathsAndStatementsRunInOrderOnLocalState() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <div state:n="rows.size * 10">
                  <div class="b" on:click="n = n + 1; selected = 'n' + n"></div>
                  <span bind:text="row.name + ': ' + (row.count == 1 ? 'one' : row.count + ' items') + ' ' + n / 4"></span>
                  <if test="n > 20 && !show"><span>big</span></if>
                </div>
                """, ".b { width: 10px; height: 10px }", m);
        assertEquals("text 0 10 9 #FFFFFFFF \"ammo: one 5\"", GoldenTest.print(doc.frame(100, 100)),
                "the state starts from the polled list; int / int divides as in Java");

        click(doc);
        assertEquals("n21", m.selected.get(), "the second statement reads the first one's write");
        m.row.set(new Row("ammo", 3));
        assertEquals("text 0 10 9 #FFFFFFFF \"ammo: 3 items 5\"\ntext 0 19 9 #FFFFFFFF \"big\"",
                GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void eachRowHasItsOwnLocalState() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <for each="rows" as="r" key="r.name"><span state:open="false" class="r"
                      on:click="open = !open" bind:text="r.name + (open ? '+' : '-')"></span></for>
                """, ".r { width: 10px; height: 10px }", m);
        doc.frame(100, 100);
        click(doc);
        assertEquals("text 0 0 9 #FFFFFFFF \"a+\"\ntext 0 10 9 #FFFFFFFF \"b-\"", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void loadReportsBadExpressionsAndStatementsAtTheirColumn() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <div bind:text="row.count + "></div>
                <div bind:class:x="row.count && show"></div>
                <div on:click="detail = 'x'"></div>
                <div on:click="show = 1"></div>
                <div state:show="1"></div>
                <div bind:text="row.name < 3"></div>
                <input bind:value="selected + ''"/>
                """, "", new Picker()));
        assertEquals(List.of("binding/expression-syntax@t.html:2:29", "binding/operand-type@t.html:3:30",
                "binding/not-assignable@t.html:4:16", "binding/assignment-type@t.html:5:23",
                "binding/hidden-name@t.html:6:6", "binding/operand-type@t.html:7:26",
                "binding/two-way-target@t.html:8:20"), DiagCodes.of(e));
    }

    static final class Bars extends ViewModel {
        final Value<Object> width = state("40%");
        final Value<Integer> left = state(5);
        final Value<String> tint = state("#f00");
    }

    @Test
    void boundStyleTakesCssTextAndNumbersOverridesTheCascadeAndNullFallsBack() {
        Bars m = new Bars();
        UiDocument doc = load("<div class=\"fill\" style=\"width: 50px !important; height: 4px\" bind:style:width=\"width\""
                + " bind:style:left=\"left\" bind:style:background-color=\"tint\"></div>",
                ".fill { width: 10px; left: 1px; background-color: #00f }", m);
        assertEquals("rect 5 0 40 4 #FFFF0000", GoldenTest.print(doc.frame(100, 100)));

        m.width.set(20);
        assertEquals("rect 5 0 20 4 #FFFF0000", GoldenTest.print(doc.frame(100, 100)));
        m.width.set(25.0);
        m.left.set(12);
        assertEquals("rect 12 0 25 4 #FFFF0000", GoldenTest.print(doc.frame(100, 100)));

        m.width.set(null);
        m.left.set(null);
        m.tint.set(null);
        assertEquals("rect 1 0 50 4 #FF0000FF", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(0, doc.diagnostics().errorCount());
    }

    static final class Widths extends ViewModel {
        final Value<String> width = state("20px");
        final Value<Object> tint = state("#fff");
    }

    @Test
    void anInvalidBoundValueKeepsTheLastValidOneAndIsReportedOnceUntilItRecovers() {
        Widths m = new Widths();
        UiDocument doc = load("<div style=\"height: 4px\" bind:style:width=\"width\" bind:style:background-color=\"tint\"></div>",
                "", m);
        assertEquals("rect 0 0 20 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));

        m.width.set("wide");
        assertEquals("rect 0 0 20 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        m.width.set("30 px");
        doc.frame(100, 100);
        assertEquals(List.of("binding/invalid-style-value@t.html:2:44"), DiagCodes.of(doc.diagnostics()));

        m.width.set("30px");
        assertEquals("rect 0 0 30 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        m.width.set("-5px");
        m.tint.set(7);
        assertEquals("rect 0 0 30 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        m.tint.set(true);
        doc.frame(100, 100);
        assertEquals(List.of("binding/invalid-style-value@t.html:2:44", "binding/invalid-style-value@t.html:2:44",
                "binding/invalid-style-value@t.html:2:80"), DiagCodes.of(doc.diagnostics()));
    }

    static final class Styled extends ViewModel {
        final Value<String> w = state("1px");
        final Value<Boolean> flag = state(true);
        final Value<Integer> n = state(1);
        final Value<String> bad = state("x y");
    }

    @Test
    void loadReportsBadStyleBindingsWithTheirPositions() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <div bind:style:wdth="w"></div>
                <div bind:style:margin="w"></div>
                <div bind:style:width="flag"></div>
                <div bind:style:color="n"></div>
                """, "", new Styled()));
        assertEquals(List.of("binding/unknown-style-property@t.html:2:6", "binding/shorthand-style@t.html:3:6",
                "binding/value-type@t.html:4:24", "binding/value-type@t.html:5:24"), DiagCodes.of(e));
    }

    @Test
    void anInvalidInitialBoundStyleValueFailsTheLoad() {
        UiLoadException initial = assertThrows(UiLoadException.class,
                () -> load("<div bind:style:width=\"bad\"></div>", "", new Styled()));
        assertEquals(List.of("binding/invalid-style-value@t.html:2:24"), DiagCodes.of(initial));
    }

    static final class ReadOnlyDeclared extends ViewModel {
        final Cell<String> f = state("x");
    }

    @Test
    void writabilityFollowsTheDeclaredTypeNotTheHeldCell() {
        UiLoadException e = assertThrows(UiLoadException.class,
                () -> load("<input bind:value=\"f\"/>", "", new ReadOnlyDeclared()));
        assertEquals(List.of("model/undeclared-writable@t.html:1:1", "binding/two-way-target@t.html:2:20"), DiagCodes.of(e));
    }

    private static final TagRegistry STRING_ITEM = TagRegistry.HTML.extend()
            .declare("item payload=item accepts=java.lang.String", "t").build();

    private static UiDocument loadItem(TagRegistry tags, ViewModel model) {
        return UiDocument.load(GoldenTest.linked("<item bind:item=\"p\"></item>"), "t.html", GoldenTest.sheet(""), tags,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT);
    }

    @Test
    void aPayloadOfAnUnacceptedTypeIsReportedAtTheValue() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> loadItem(STRING_ITEM, new ViewModel() {
            final Value<Integer> p = state(1);
        }));
        assertEquals(List.of("binding/payload-type@t.html:2:18"), DiagCodes.of(e));
    }

    @Test
    void aPayloadOfTheAcceptedTypeLoads() {
        UiDocument doc = loadItem(STRING_ITEM, new ViewModel() {
            final Value<String> p = state("s");
        });
        assertEquals(List.of(), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void anObjectPayloadIsCheckedOnlyAtRuntimeAndLoads() {
        UiDocument doc = loadItem(STRING_ITEM, new ViewModel() {
            final Value<Object> p = state("s");
        });
        assertEquals(List.of(), DiagCodes.of(doc.diagnostics()));
    }

    private static final TagRegistry SUPPLIER_ITEM = TagRegistry.HTML.extend()
            .declare("item payload=item accepts=java.util.function.Supplier<java.lang.String>", "t").build();

    @Test
    void anAcceptedTypeArgumentIsChecked() {
        UiDocument doc = loadItem(SUPPLIER_ITEM, new ViewModel() {
            final Value<Supplier<String>> p = state(() -> "s");
        });
        assertEquals(List.of(), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void aMismatchedTypeArgumentIsAPayloadTypeError() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> loadItem(SUPPLIER_ITEM, new ViewModel() {
            final Value<Supplier<Integer>> p = state(() -> 1);
        }));
        assertEquals(List.of("binding/payload-type@t.html:2:18"), DiagCodes.of(e));
    }

    @Test
    void anUnloadableAcceptedClassSkipsTheCheck() {
        TagRegistry tags = TagRegistry.HTML.extend().declare("item payload=item accepts=com.example.Missing", "t").build();
        UiDocument doc = loadItem(tags, new ViewModel() {
            final Value<Integer> p = state(1);
        });
        assertEquals(List.of(), DiagCodes.of(doc.diagnostics()));
    }
}
