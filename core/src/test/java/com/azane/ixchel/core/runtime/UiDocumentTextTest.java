package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.template.TagRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiDocumentTextTest {
    private static String frame(String html, String css) {
        UiDocument doc = UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), TagRegistry.HTML,
                GoldenTest.MONO, GoldenTest.NO_IMAGES);
        return GoldenTest.print(doc.frame(100, 100));
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
    void nowrapKeepsOneLineAndEllipsisCutsItAtThePaddingBoxOnlyWhenClipped() {
        // 40px boxes with a 1px border: 38px of room, six 6px glyphs; "…" is one glyph in MONO.
        assertEquals("""
                text 1 0 9 #FFFFFFFF "ab cd ef gh"
                clip 1 9 38 9
                text 1 9 9 #FFFFFFFF "ab cd…"
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
}
