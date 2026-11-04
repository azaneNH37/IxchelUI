package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.ModelShape;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.Translations;
import com.azane.ixchel.core.text.TextMeasurer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDocumentTest {
    private static UiDocument load(String html, String css) {
        return load(html, css, GoldenTest.MONO);
    }

    private static UiDocument load(String html, String css, TextMeasurer measurer) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, measurer,
                GoldenTest.NO_IMAGES);
    }

    private static String frame(UiDocument doc, float w, float h) {
        return GoldenTest.print(doc.frame(w, h));
    }

    @Test
    void textAndClassChangesReflowSiblingsOnTheNextFrame() {
        UiDocument doc = load("""
                <div class="row"><span class="n">30</span><div class="mark"></div></div>
                """, """
                .row { flex-direction: row; align-items: flex-start }
                .mark { width: 2px; height: 2px; background-color: #fff }
                .gone { display: none }
                """);
        assertEquals("text 0 0 9 #FFFFFFFF \"30\"\nrect 12 0 2 2 #FFFFFFFF", frame(doc, 100, 100));

        Element row = (Element) doc.root().children().get(0);
        Element span = (Element) row.children().get(0);
        ((TextNode) span.children().get(0)).setText("300");
        assertEquals("text 0 0 9 #FFFFFFFF \"300\"\nrect 18 0 2 2 #FFFFFFFF", frame(doc, 100, 100));

        span.addClass("gone");
        assertEquals("rect 0 0 2 2 #FFFFFFFF", frame(doc, 100, 100));
    }

    @Test
    void percentagesResolveAgainstTheViewportAndTextWrapsToItsBox() {
        UiDocument doc = load("""
                <div class="half"></div>
                <div class="narrow">aa bb cc</div>
                """, """
                .half { width: 50%; height: 10px; background-color: #f00 }
                .narrow { width: 30px; align-items: flex-start }
                """);
        assertEquals("""
                rect 0 0 240 10 #FFFF0000
                text 0 10 9 #FFFFFFFF "aa bb"
                text 0 19 9 #FFFFFFFF "cc\"""", frame(doc, 480, 270));
    }

    @Test
    void loadReportsTemplateAndStyleErrorsTogether() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load(
                "<div class=\"a\" style=\"colr: red\"><blink></blink></div>",
                ".a { width: 10 }"));
        assertEquals(List.of("template/unknown-tag@t.html:2:34", "css/missing-unit@t.css:1:13",
                "css/unknown-property@t.html:2:23"), DiagCodes.of(e));
    }

    @Test
    void linkedAndInlineSheetsApplyInDocumentOrder() {
        Map<String, String> sheets = Map.of(
                "a.css", ".n { width: 10px; height: 8px; background-color: #f00 }",
                "b.css", ".n { background-color: #00f }");
        UiDocument doc = UiDocument.load("""
                <link rel="stylesheet" href="a.css"/>
                <style>.n { height: 4px; background-color: #0f0 }</style>
                <link rel="stylesheet" href="b.css"/>
                <div class="n"></div>
                """, "t.html", sheets::get, com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("rect 0 0 10 4 #FF0000FF", frame(doc, 100, 100));
    }

    @Test
    void loadReportsBadLinksAtTheirPositions() {
        UiLoadException thrown = assertThrows(UiLoadException.class, () -> UiDocument.load("""
                <div></div>
                <link rel="stylesheet" href="nope.css"/>
                <div><link rel="stylesheet" href="t.css"/></div>
                <link rel="icon" href="t.css"/>
                <link rel="stylesheet"/>
                <link rel="stylesheet" href="t.css" class="x" bind:href="t"></link>
                """, "t.html", GoldenTest.sheet(""), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertEquals(List.of("template/misplaced-element@t.html:3:6", "template/invalid-link@t.html:4:1",
                "template/invalid-link@t.html:5:1", "template/unknown-attribute@t.html:6:37",
                "template/unknown-attribute@t.html:6:47", "template/invalid-link@t.html:6:1",
                "template/mismatched-closing-tag@t.html:6:61", "load/file-not-found@t.html:2:1"), DiagCodes.of(thrown));
    }

    @Test
    void aStylesheetTheHostCannotReadFailsTheLoadAtItsLink() {
        UiLoadException failed = assertThrows(UiLoadException.class, () -> UiDocument.load("<link rel=\"stylesheet\" href=\"nope.css\"/>", "t.html", GoldenTest.NO_SHEETS, com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertEquals(List.of("load/unreadable-file@t.html:1:1"), DiagCodes.of(failed));
    }

    @Test
    void unchangedFrameKeepsTheDisplayListVersionAndAChangeBumpsIt() {
        UiDocument doc = load("<span class=\"n\">30</span>", ".n { color: #f00 }");
        long first = doc.frame(100, 100).version();
        assertEquals(first, doc.frame(100, 100).version());

        ((TextNode) ((Element) doc.root().children().get(0)).children().get(0)).setText("29");
        assertNotEquals(first, doc.frame(100, 100).version());
    }

    @Test
    void changingIdRestylesTheElement() {
        UiDocument doc = load("<span id=\"a\">x</span>", "#b { color: #f00 }");
        assertEquals("text 0 0 9 #FFFFFFFF \"x\"", frame(doc, 100, 100));
        ((Element) doc.root().children().get(0)).setAttribute("id", "b");
        assertEquals("text 0 0 9 #FFFF0000 \"x\"", frame(doc, 100, 100));
    }

    @Test
    void movedElementTakesInheritedValuesFromItsNewParent() {
        UiDocument doc = load("""
                <div class="red"><span>x</span></div>
                <div class="blue"></div>
                """, ".red { color: #f00 } .blue { color: #00f }");
        frame(doc, 100, 100);
        Element red = (Element) doc.root().children().get(0);
        Element blue = (Element) doc.root().children().get(1);
        blue.appendChild(red.children().get(0));
        assertEquals("text 0 0 9 #FF0000FF \"x\"", frame(doc, 100, 100));
    }

    @Test
    void invalidateTextRemeasuresWithTheCurrentFont() {
        float[] advance = {6};
        TextMeasurer font = new TextMeasurer() {
            @Override
            public float width(String text, float size, boolean bold) {
                return text.length() * advance[0];
            }

            @Override
            public float lineHeight(float size) {
                return size;
            }

            @Override
            public float baseline(float size) {
                return size * 7 / 9;
            }
        };
        UiDocument doc = load("""
                <div class="row"><span>ab</span><div class="mark"></div></div>
                """, """
                .row { flex-direction: row; align-items: flex-start }
                .mark { width: 1px; height: 1px; background-color: #fff }
                """, font);
        assertTrue(frame(doc, 100, 100).endsWith("rect 12 0 1 1 #FFFFFFFF"));
        advance[0] = 8;
        doc.invalidateText();
        assertTrue(frame(doc, 100, 100).endsWith("rect 16 0 1 1 #FFFFFFFF"));
    }

    static final class Counter extends ViewModel {
        final Value<Integer> count = state(0);
    }

    private static List<String> lint(String html, String css, ModelShape shape) {
        return DiagCodes.of(UiDocument.lint(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, Translations.NONE, shape, Set.of()));
    }

    private static List<String> loadFailure(String html, ViewModel model) {
        return DiagCodes.of(assertThrows(UiLoadException.class, () -> UiDocument.load(GoldenTest.linked(html), "t.html",
                GoldenTest.sheet(""), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, model, Bindings.PLAIN_TEXT)));
    }

    @Test
    void lintReportsAnUnknownNameAsLoadDoes() {
        String html = "<span bind:text=\"missing\"></span>";
        List<String> linted = lint(html, "", ModelShape.of(Counter.class));
        assertEquals(List.of("binding/unknown-name@t.html:2:18"), linted);
        assertEquals(loadFailure(html, new Counter()), linted);
    }

    @Test
    void lintReportsATypeErrorAsLoadDoes() {
        String html = "<div bind:class:x=\"count\"></div>";
        List<String> linted = lint(html, "", ModelShape.of(Counter.class));
        assertEquals(List.of("binding/value-type@t.html:2:20"), linted);
        assertEquals(loadFailure(html, new Counter()), linted);
    }

    @Test
    void lintWithoutAShapeSeesOnlyLocalStates() {
        assertEquals(List.of("binding/unknown-name@t.html:2:62"),
                lint("<div state:n=\"1\"><span bind:text=\"n\"></span><span bind:text=\"count\"></span></div>", "", null));
    }

    @Test
    void lintReportsInlineContentErrorsWithoutLayingOut() {
        assertEquals(List.of("layout/inline-box-property@t.html:2:8", "layout/block-in-inline@t.html:2:25"),
                lint("<div>a <span class=\"p\">b<span class=\"x\">c</span></span></div>",
                        "div, .x { display: block } .p { padding: 2px }", null));
    }
}
