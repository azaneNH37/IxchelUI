package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Bindings;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentBindingTest {
    private static UiDocument load(String html, String css, ViewModel model) {
        return UiDocument.load(html, "t.html", List.of(new UiDocument.CssSource(css, "t.css")), TagRegistry.HTML,
                GoldenTest.MONO, model, Bindings.PLAIN_TEXT);
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
                <div bind:text="gun.ammo"></div>
                <span bind:text="text">x</span>
                <div bind:style="text"></div>
                <div bind:title="text"></div>
                """, "", new Ammo()));
        String m = e.getMessage();
        assertTrue(m.startsWith("6 errors"), m);
        assertTrue(m.contains("t.html:1:17:") && m.contains("'missing' is not a Value or Derived field"), m);
        assertTrue(m.contains("t.html:2:20:") && m.contains("needs a Boolean"), m);
        assertTrue(m.contains("t.html:3:17:") && m.contains("not supported yet"), m);
        assertTrue(m.contains("t.html:4:7:") && m.contains("leave it empty"), m);
        assertTrue(m.contains("t.html:5:6:") && m.contains("toggle a class"), m);
        assertTrue(m.contains("t.html:6:6:") && m.contains("unknown binding target 'bind:title'"), m);

        UiLoadException none = assertThrows(UiLoadException.class,
                () -> UiDocument.load("<span bind:text=\"x\"></span>", "t.html", List.of(), TagRegistry.HTML, GoldenTest.MONO));
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
        UiDocument doc = UiDocument.load("<item class=\"i\" bind:item=\"stack\"></item>", "t.html",
                List.of(new UiDocument.CssSource(".i { width: 16px; height: 16px }", "t.css")), tags,
                GoldenTest.MONO, m, Bindings.PLAIN_TEXT);
        assertEquals("item 0 0 16 16 sword", GoldenTest.print(doc.frame(100, 100)));

        m.live = "bow";
        assertEquals("item 0 0 16 16 bow", GoldenTest.print(doc.frame(100, 100)));
        m.live = null;
        assertEquals("", GoldenTest.print(doc.frame(100, 100)));

        UiLoadException e = assertThrows(UiLoadException.class, () -> UiDocument.load(
                "<item item=\"sword\"></item>", "t.html", List.of(), tags, GoldenTest.MONO));
        assertTrue(e.getMessage().contains("unknown attribute 'item'"), e.getMessage());
    }
}
