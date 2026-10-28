package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Bindings;
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
        final Value<Integer> loaded = source(Source.polled(() -> live));
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
        final Value<int[]> counts = source(Source.polled(() -> live, int[]::clone, Arrays::equals));
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
        final Value<Integer> n = source(Source.polled(() -> live));
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
        assertEquals(1, doc.diagnostics().errorCount());
        assertTrue(doc.diagnostics().entries().get(0).message().contains("no ammo"));

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
        String m = e.getMessage();
        assertTrue(m.startsWith("6 errors"), m);
        assertTrue(m.contains("t.html:2:17:") && m.contains("'missing' is not a field of Ammo"), m);
        assertTrue(m.contains("t.html:3:20:") && m.contains("needs a Boolean"), m);
        assertTrue(m.contains("t.html:4:17:") && m.contains("java.lang.String is not a record"), m);
        assertTrue(m.contains("t.html:5:7:") && m.contains("leave it empty"), m);
        assertTrue(m.contains("t.html:6:6:") && m.contains("bind:style takes a property name: bind:style:<property>"), m);
        assertTrue(m.contains("t.html:7:6:") && m.contains("unknown binding target 'bind:href'"), m);

        UiLoadException none = assertThrows(UiLoadException.class,
                () -> UiDocument.load("<span bind:text=\"x\"></span>", "t.html", GoldenTest.NO_SHEETS, TagRegistry.HTML,
                        GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertTrue(none.getMessage().contains("needs a view model"), none.getMessage());
    }

    static final class Held extends ViewModel {
        Object live = "sword";
        final Value<Object> stack = source(Source.polled(() -> live, v -> v, (a, b) -> a == b));
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

        UiLoadException e = assertThrows(UiLoadException.class, () -> UiDocument.load(
                "<item item=\"sword\"></item>", "t.html", GoldenTest.NO_SHEETS, tags, GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertTrue(e.getMessage().contains("unknown attribute 'item'"), e.getMessage());
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
        assertTrue(doc.events().mousePressed(5, 5, 0));
        assertTrue(doc.events().mouseReleased(5, 5, 0));
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
        doc.events().mousePressed(5, 5, 0);
        doc.events().mouseReleased(5, 5, 0);
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
        String m = e.getMessage();
        assertTrue(m.startsWith("6 errors"), m);
        assertTrue(m.contains("t.html:2:6:") && m.contains("unknown event 'hover'"), m);
        assertTrue(m.contains("t.html:3:16:") && m.contains("'row' is not an Action field"), m);
        assertTrue(m.contains("t.html:4:16:") && m.contains("pass it as select(path)"), m);
        assertTrue(m.contains("t.html:5:16:") && m.contains("'reload' takes no argument"), m);
        assertTrue(m.contains("t.html:6:16:") && m.contains("but 'selected' is java.lang.String"), m);
        assertTrue(m.contains("t.html:7:17:") && m.contains("an action is not a value"), m);
    }

    static final class Keyed extends ViewModel {
        final List<String> log = new ArrayList<>();
        final Action<Void> up = action(() -> log.add("up"));
        final Action<Void> shiftUp = action(() -> log.add("shift-up"));
        final Action<Void> released = action(() -> log.add("released"));
    }

    @Test
    void aKeyFilterRunsTheHandlerForTheNamedKeyAndModifiersOnlyAndConsumesJustThose() {
        Keyed m = new Keyed();
        UiDocument doc = load("""
                <div class="k"><div class="k" tabindex="0" on:keydown.up="up" on:keydown.up.shift="shiftUp" on:keyup="released"></div></div>
                """, ".k { width: 10px; height: 10px }", m);
        doc.frame(100, 100);
        assertFalse(doc.events().keyPressed(Keys.code("up"), 0), "a nested element only sees keys while focused");
        click(doc);

        assertTrue(doc.events().keyPressed(Keys.code("up"), 0));
        assertFalse(doc.events().keyPressed(Keys.code("down"), 0), "another key is not consumed");
        assertTrue(doc.events().keyPressed(Keys.code("up"), Keys.MOD_SHIFT | Keys.MOD_CTRL), "extra modifiers do not matter");
        assertTrue(doc.events().keyReleased(Keys.code("a"), 0), "an unfiltered handler fires for every key");
        assertEquals(List.of("up", "up", "shift-up", "released"), m.log);
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
        doc.events().mousePressed(5, 15, 0);
        doc.events().mouseReleased(5, 15, 0);
        assertTrue(doc.events().keyPressed(265, 0), "focus in a sibling still reaches a document-level handler");
        assertEquals(List.of("up", "up", "up"), m.log);
    }

    @Test
    void loadReportsBadKeyFilters() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("""
                <div on:keydown.meta="up"></div>
                <div on:keydown.up.super="up"></div>
                <div on:click.up="up"></div>
                <div on:keyup.f13="up"></div>
                """, "", new Keyed()));
        String m = e.getMessage();
        assertTrue(m.startsWith("4 errors"), m);
        assertTrue(m.contains("t.html:2:6:") && m.contains("unknown key 'meta' in 'on:keydown.meta'"), m);
        assertTrue(m.contains("t.html:3:6:") && m.contains("unknown modifier 'super' in 'on:keydown.up.super'"), m);
        assertTrue(m.contains("t.html:4:6:") && m.contains("'click' takes no key filter"), m);
        assertTrue(m.contains("t.html:5:6:") && m.contains("unknown key 'f13' in 'on:keyup.f13'"), m);
    }

    @Test
    void eachKeepsRowNodesByKeyAndPlacesRowsInListOrderBetweenStaticSiblings() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <span>[</span><span each="rows" as="r" key="r.name" class="r" on:click="select(r)" bind:text="r.count"></span><span>]</span>
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

        doc.events().mousePressed(5, 32, 0);
        doc.events().mouseReleased(5, 32, 0);
        m.rows.set(List.of());
        assertEquals("text 0 0 9 #FFFFFFFF \"[\"\ntext 0 9 9 #FFFFFFFF \"]\"", GoldenTest.print(doc.frame(100, 100)));
        assertEquals("a", m.selected.get(), "the handler passed its own row");
    }

    @Test
    void ifMountsWhileTrueAndUnmountedContentIsNotRead() {
        Picker m = new Picker();
        UiDocument doc = load("""
                <div if="show"><span bind:text="detail"></span></div><div if="!show"><span>none</span></div>
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
                <div each="rows"></div>
                <div each="row" as="r" key="r"></div>
                <div if="selected"></div>
                <div each="rows" as="r" key="row.name"></div>
                <div as="r"></div>
                <div each="rows" as="r" key="r" if="show"></div>
                <div if="show"><span bind:text="nope"></span></div>
                <div each="rows" as="r" key="r.name"><span bind:text="r.missing"></span></div>
                """, "", new Picker()));
        String m = e.getMessage();
        assertTrue(m.startsWith("8 errors"), m);
        assertTrue(m.contains("t.html:2:6:") && m.contains("needs 'as'"), m);
        assertTrue(m.contains("t.html:3:12:") && m.contains("'each' needs a List"), m);
        assertTrue(m.contains("t.html:4:10:") && m.contains("'if' needs a Boolean"), m);
        assertTrue(m.contains("t.html:5:30:") && m.contains("'key' is a path from the row 'r'"), m);
        assertTrue(m.contains("t.html:6:6:") && m.contains("'as' only goes with 'each'"), m);
        assertTrue(m.contains("t.html:7:33:") && m.contains("cannot share an element"), m);
        assertTrue(m.contains("t.html:8:33:") && m.contains("'nope' is not a field"), m);
        assertTrue(m.contains("t.html:9:55:") && m.contains("Row has no component 'missing'"), m);
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
        assertEquals(1, doc.diagnostics().errorCount());
        assertEquals("t.html:2:44: error: 'width' gave 'wide' for 'width': expected a length in px, a percentage or auto",
                doc.diagnostics().entries().get(0).toString());

        m.width.set("30px");
        assertEquals("rect 0 0 30 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        m.width.set("-5px");
        m.tint.set(7);
        assertEquals("rect 0 0 30 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        m.tint.set(true);
        doc.frame(100, 100);
        assertEquals(3, doc.diagnostics().errorCount());
        assertTrue(doc.diagnostics().entries().get(1).message().contains("negative value not allowed"));
        assertTrue(doc.diagnostics().entries().get(2).message().contains("gave 7 for 'background-color': this property takes no plain numbers"));
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
        String m = e.getMessage();
        assertTrue(m.startsWith("4 errors"), m);
        assertTrue(m.contains("t.html:2:6:") && m.contains("unsupported property 'wdth'"), m);
        assertTrue(m.contains("t.html:3:6:") && m.contains("'margin' is a shorthand; bind its longhands instead: "
                + "margin-top, margin-right, margin-bottom, margin-left"), m);
        assertTrue(m.contains("t.html:4:24:") && m.contains("takes a String or a Number, but 'flag' is java.lang.Boolean"), m);
        assertTrue(m.contains("t.html:5:24:") && m.contains("'color' takes no plain numbers"), m);

        UiLoadException initial = assertThrows(UiLoadException.class,
                () -> load("<div bind:style:width=\"bad\"></div>", "", new Styled()));
        assertTrue(initial.getMessage().contains("t.html:2:24:")
                && initial.getMessage().contains("'bad' gave 'x y' for 'width': expected a single value"), initial.getMessage());
    }
}
