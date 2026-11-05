package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.view.TagRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentTextTest {
    private static UiDocument load(String html, String css) {
        return UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
    }

    private static String frame(String html, String css) {
        return GoldenTest.print(load(html, css).frame(100, 100));
    }

    @Test
    void textAlignPlacesEachLineInTheTextBoxAndIsInherited() {
        assertEquals("""
                text 5 0 9 #FFFFFFFF "ab cd"
                text 14 9 9 #FFFFFFFF "ef"
                text 22 18 9 #FFFFFFFF "abc\"""", frame("""
                <div class="c">ab cd ef</div>
                <div class="r"><div>abc</div></div>
                """, """
                div { width: 40px }
                .c { text-align: center }
                .r { text-align: end }
                """));
    }

    @Test
    void lineHeightSplitsItsLeadingAroundTheGlyphsAFactorInheritsAsOneAPercentageAsPixels() {
        // p: 18px strut lines; the 18px span inherits the factor, 36px. q: 27px, inherited as 27px by the 27px span.
        assertEquals("""
                text 0 16 9 #FFFFFFFF "ab"
                text 12 9 18 #FFFFFFFF "c"
                text 0 50 9 #FFFFFFFF "x"
                text 6 36 27 #FFFFFFFF "y\"""", frame("""
                <div class="p">ab<span class="s">c</span></div>
                <div class="q">x<span class="t">y</span></div>
                """, """
                .p, .q { display: block }
                .p { line-height: 2 }
                .s { font-size: 18px; display: inline }
                .q { line-height: 300% }
                .t { font-size: 3em; display: inline }
                """));
    }

    @Test
    void formControlsAreAsTallAsTheirLineHeight() {
        UiDocument doc = load("<input class=\"i\"/>", ".i { line-height: 15px; padding: 0; border: none }");
        doc.frame(100, 100);
        assertEquals(15, ((Element) doc.root().children().get(0)).box().height());
    }

    @Test
    void nowrapKeepsOneLineAndEllipsisCutsItAtThePaddingBoxOnlyWhenClipped() {
        // 40px boxes with a 1px border: 38px of room, six 6px glyphs; "…" is one glyph in MONO and a
        // fragment of its own, after the cut text.
        assertEquals("""
                text 1 0 9 #FFFFFFFF "ab cd ef gh"
                clip 1 9 38 9
                text 1 9 9 #FFFFFFFF "ab cd"
                text 31 9 9 #FFFFFFFF "…"
                unclip
                clip 1 18 38 9
                text 1 18 9 #FFFFFFFF "ab cd ef gh"
                unclip""", frame("""
                <div class="v">ab cd ef gh</div>
                <div class="e">ab cd ef gh</div>
                <div class="c">ab cd ef gh</div>
                """, """
                div { width: 40px; height: 9px; white-space: nowrap; border-left: 1px solid #0000; border-right: 1px solid #0000 }
                .e { overflow: hidden; text-overflow: ellipsis }
                .c { overflow: hidden }
                .v { text-overflow: ellipsis }
                """));
    }

    @Test
    void inlineElementsFlowInTheirParentsLinesAndStyleTheirText() {
        // "ab cd" is 18 + 14 (bold) = 32px; " ef" would end at 50, past 48.
        assertEquals("""
                text 0 0 9 #FFFFFFFF "ab "
                text 18 0 9 #FFFF0000 "cd" flags 5
                text 0 9 9 #FFFFFFFF "ef "
                text 18 9 9 #FFFFFFFF "gh" flags 4""", frame("""
                <div>ab <span class="b">cd</span> ef <a>gh</a></div>
                """, """
                div { display: block; width: 48px }
                .b { font-weight: bold; color: #f00; text-decoration: underline }
                """));
    }

    @Test
    void cjkBreaksBetweenCharactersButNotBeforeClosingPunctuation() {
        assertEquals("""
                text 0 0 9 #FFFFFFFF "今天天气很"
                text 0 9 9 #FFFFFFFF "好。\"""", frame("<div>今天天气很好。</div>", "div { display: block; width: 30px }"));
    }

    @Test
    void inlineBlockSitsOnTheBaselineAndRaisesItsLine() {
        // The box's bottom edge is its baseline: 20px above, so the text's line top moves to 20 - 7.
        assertEquals("""
                text 0 13 9 #FFFFFFFF "a"
                rect 6 0 4 20 #FFFFFFFF
                text 10 13 9 #FFFFFFFF "b\"""", frame("<div>a<span class=\"i\"></span>b</div>", """
                div { display: block }
                .i { display: inline-block; width: 4px; height: 20px; background-color: #fff }
                """));
    }

    @Test
    void styledTextSpansKeepTheirColorAndAddTheirFlags() {
        Bindings.TextConverter styled = new Bindings.TextConverter() {
            @Override
            public String plain(Object value) {
                return String.valueOf(value);
            }

            @Override
            public StyledText styled(Object value) {
                return new StyledText(List.of(new StyledText.Span("ab", 0xFFFF0000, StyledText.BOLD, null),
                        new StyledText.Span("cd", 0, StyledText.OBFUSCATED, null)));
            }
        };
        UiDocument doc = UiDocument.load(GoldenTest.linked("<div><span class=\"u\" bind:text=\"'x'\"></span></div>"),
                "t.html", GoldenTest.sheet("div { display: block; color: #0f0 } .u { text-decoration: underline }"), com.azane.ixchel.core.html.HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, null, styled);
        assertEquals("""
                text 0 0 9 #FFFF0000 "ab" flags 5
                text 14 0 9 #FF00FF00 "cd" flags 20""", GoldenTest.print(doc.frame(100, 100)));
    }

    @Test
    void textTransformMapsCaseBeforeMeasuringAndIsInherited() {
        // "STRASSE " is eight 6px glyphs, so the span starts at 48, not at the 42 of "straße ".
        assertEquals("""
                text 0 0 9 #FFFFFFFF "STRASSE "
                text 48 0 9 #FFFFFFFF "X"
                text 0 9 9 #FFFFFFFF "abc\"""", frame("""
                <div class="u">straße <span>x</span></div>
                <div class="l">ABC</div>
                """, """
                div { display: block }
                .u { text-transform: uppercase }
                .l { text-transform: lowercase }
                """));
    }

    @Test
    void fontFamilyTakesTheFirstFontTheHostHasAndAComponentFontWins() {
        // The host has minecraft:uniform (4px glyphs) and mod:runes; other fonts are 6px.
        com.azane.ixchel.core.text.TextMeasurer fonts = new com.azane.ixchel.core.text.TextMeasurer() {
            @Override
            public boolean hasFont(String font) {
                return font.equals("minecraft:uniform") || font.equals("mod:runes");
            }

            @Override
            public float width(String text, float size, boolean bold, String font) {
                return text.length() * ("minecraft:uniform".equals(font) ? 4 : 6);
            }

            @Override
            public float lineHeight(float size) {
                return 9;
            }

            @Override
            public float baseline(float size) {
                return 7;
            }
        };
        Bindings.TextConverter runes = new Bindings.TextConverter() {
            @Override
            public String plain(Object value) {
                return String.valueOf(value);
            }

            @Override
            public StyledText styled(Object value) {
                return new StyledText(List.of(new StyledText.Span("ef", 0, 0, "mod:runes")));
            }
        };
        UiDocument doc = UiDocument.load(GoldenTest.linked("<div>ab<span>cd</span><span bind:text=\"'x'\"></span></div>"),
                "t.html", GoldenTest.sheet("div { display: block; font-family: missing, minecraft:uniform }"),
                com.azane.ixchel.core.html.HtmlElements.TAGS, fonts, GoldenTest.NO_IMAGES, null, runes);
        assertEquals("""
                text 0 0 9 #FFFFFFFF "ab" font minecraft:uniform
                text 8 0 9 #FFFFFFFF "cd" font minecraft:uniform
                text 16 0 9 #FFFFFFFF "ef" font mod:runes""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(List.of("layout/unknown-font@t.html:2:1"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void inlineElementWithABoxPropertyIsAnError() {
        UiDocument doc = load("<div>a <span class=\"p\">b</span></div>", "div { display: block } .p { padding: 2px }");
        doc.frame(100, 100);
        assertEquals(List.of("layout/inline-box-property@t.html:2:8"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void inlineElementIsHitOnItsLinesOnly() {
        // "ab " is 18px, the link "cd ef" wraps: "cd" at 18..30 on line one, "ef" at 0..12 on line two.
        UiDocument doc = load("<div>ab <a>cd ef</a></div>", "div { display: block; width: 36px }");
        doc.frame(100, 100);
        Element div = (Element) doc.root().children().get(0);
        Element a = (Element) div.children().get(1);
        assertSame(a, doc.events().hitTest(20, 4));
        assertSame(a, doc.events().hitTest(4, 13));
        assertSame(div, doc.events().hitTest(4, 4), "inside the link's bounding box, before it starts");
        assertSame(div, doc.events().hitTest(20, 13), "inside the bounding box, after the link ends");
    }

    @Test
    void textShadowDrawsAnInheritedOffsetCopyUnderTheTextInItsOwnColor() {
        assertEquals("""
                text 1 1 9 #FF3F3F3F "ab "
                text 0 0 9 #FFFFFFFF "ab "
                text 19 1 9 #FF3F3F3F "cd"
                text 18 0 9 #FFFF0000 "cd\"""", frame("""
                <div>ab <span class="r">cd</span></div>
                """, """
                div { display: block; text-shadow: #3F3F3F 1px 1px }
                .r { color: #f00 }
                """));
    }

    @Test
    void blurSeveralShadowsAndAMissingColorAreReported() {
        UiLoadException e = assertThrows(UiLoadException.class, () -> load("<div></div>", """
                .a { text-shadow: 1px 1px 2px #000 }
                .b { text-shadow: 1px 1px #000, 2px 2px #fff }
                .c { text-shadow: 1px 1px }
                .d { text-shadow: 1px 1px 0 #000 }
                """));
        assertEquals(List.of("css/unsupported-value@t.css:1:40", "css/unsupported-value@t.css:2:31",
                "css/unsupported-value@t.css:3:19"), DiagCodes.of(e));
    }
}
