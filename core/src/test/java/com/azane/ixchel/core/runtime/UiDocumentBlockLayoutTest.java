package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.dom.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiDocumentBlockLayoutTest {
    private static UiDocument load(String html, String css) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
    }

    @Test
    void blockChildrenStackAndTheirVerticalMarginsCollapseWithEachOtherAndThroughTheParent() {
        // a's top margin collapses through b, but not through o: a flex item contains its children's
        // margins. Adjacent 10px margins collapse to 10px, b's bottom one with z's top one too.
        UiDocument doc = load("""
                <div class="o"><div class="b"><div class="m">a</div><div class="m">c</div></div><div class="m">z</div></div>
                """, """
                .o, .b, .m { display: block }
                .m { margin: 10px 0 }
                """);
        assertEquals("""
                text 0 10 9 #FFFFFFFF "a"
                text 0 29 9 #FFFFFFFF "c"
                text 0 48 9 #FFFFFFFF "z\"""", GoldenTest.print(doc.frame(100, 100)));
        Element o = (Element) doc.root().children().get(0);
        Element b = (Element) o.children().get(0);
        assertEquals(10, b.box().y());
        assertEquals(28, b.box().height());
        assertEquals(67, o.box().height());
        assertEquals(List.of(), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void inlineContentBetweenBlocksIsWrappedInAnonymousBlocksAndAnAbsoluteBoxDoesNotSplitIt() {
        UiDocument doc = load("""
                <div class="b">ab <div class="x">cd</div> ef<div class="a">X</div><span>gh</span></div>
                """, """
                .b, .x { display: block }
                .b { position: relative }
                .a { position: absolute; left: 50px; top: 0 }
                """);
        assertEquals("""
                text 0 0 9 #FFFFFFFF "ab"
                text 0 9 9 #FFFFFFFF "cd"
                text 0 18 9 #FFFFFFFF "ef"
                text 50 0 9 #FFFFFFFF "X"
                text 12 18 9 #FFFFFFFF "gh\"""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(27, ((Element) doc.root().children().get(0)).box().height());
        assertEquals(List.of(), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void aChildTurningBlockLevelSplitsTheParagraphAndTurningBackJoinsIt() {
        UiDocument doc = load("""
                <div class="b">ab<span>cd</span>ef</div>
                """, """
                .b { display: block }
                .x { display: block }
                """);
        assertEquals("""
                text 0 0 9 #FFFFFFFF "ab"
                text 12 0 9 #FFFFFFFF "cd"
                text 24 0 9 #FFFFFFFF "ef\"""", GoldenTest.print(doc.frame(100, 100)));
        Element span = (Element) ((Element) doc.root().children().get(0)).children().get(1);
        span.addClass("x");
        assertEquals("""
                text 0 0 9 #FFFFFFFF "ab"
                text 0 9 9 #FFFFFFFF "cd"
                text 0 18 9 #FFFFFFFF "ef\"""", GoldenTest.print(doc.frame(100, 100)));
        span.removeClass("x");
        assertEquals("""
                text 0 0 9 #FFFFFFFF "ab"
                text 12 0 9 #FFFFFFFF "cd"
                text 24 0 9 #FFFFFFFF "ef\"""", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void aBlockInsideAnInlineElementIsStillAnError() {
        UiDocument doc = load("""
                <div class="b"><span>a<div class="x">b</div></span></div>
                """, """
                .b, .x { display: block }
                """);
        doc.frame(100, 100);
        assertEquals(List.of("layout/block-in-inline@t.html:2:23"), DiagCodes.of(doc.diagnostics()));
    }
}
