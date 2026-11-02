package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.Translations;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiDocumentTranslationTest {
    private static String frame(String language, Map<String, String> entries, String html, String css) {
        Translations t = new Translations() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public boolean base() {
                return true;
            }

            @Override
            public String get(String key) {
                return entries.get(key);
            }
        };
        UiDocument doc = UiDocument.load(GoldenTest.linked(html), "t.html", GoldenTest.sheet(css), com.azane.ixchel.core.html.HtmlElements.TAGS, t,
                GoldenTest.MONO, GoldenTest.NO_IMAGES, null, Bindings.PLAIN_TEXT);
        return GoldenTest.print(doc.frame(200, 100));
    }

    @Test
    void aNewlineInATranslationBreaksTheLineAndMarksKeepTheirStyle() {
        assertEquals("""
                text 0 0 9 #FFFFFFFF "Hi "
                text 18 0 9 #FFFF0000 "Bob"
                text 36 0 9 #FFFFFFFF ","
                text 0 9 9 #FFFFFFFF "bye\"""", frame("en-us", Map.of("m", "Hi <b>Bob</b>,\nbye"),
                "<div t=\"m\"><span mark=\"b\" class=\"b\"></span></div>",
                "div { display: block } .b { color: #f00 }"));
    }

    @Test
    void langSelectsByTheLanguageOfTheDocumentRootUnlessAnElementSetsItsOwn() {
        String html = "<div><span class=\"a\">a</span><span class=\"a\" lang=\"en\">b</span></div>";
        String css = ".a:lang(zh) { color: #f00 }";
        assertEquals("""
                text 0 0 9 #FFFF0000 "a"
                text 0 9 9 #FFFFFFFF "b\"""", frame("zh-cn", Map.of(), html, css));
        assertEquals("""
                text 0 0 9 #FFFFFFFF "a"
                text 0 9 9 #FFFFFFFF "b\"""", frame("en-us", Map.of(), html, css));
    }

    @Test
    void aLangWithoutARangeOrASecondLangInOneCompoundIsReported() {
        Diagnostics diag = UiDocument.lint(GoldenTest.linked("<div></div>"), "t.html",
                GoldenTest.sheet("div:lang() {}\ndiv:lang(en):lang(zh) {}"), com.azane.ixchel.core.html.HtmlElements.TAGS, Translations.NONE, null, Set.of());
        assertEquals(List.of("css/invalid-lang@t.css:1:5", "css/invalid-lang@t.css:2:14"), DiagCodes.of(diag));
    }
}
