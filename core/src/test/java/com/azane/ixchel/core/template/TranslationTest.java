package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.runtime.DiagCodes;
import com.azane.ixchel.core.text.TextShaper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class TranslationTest {
    private static Translations language(String language, boolean base, Map<String, String> entries) {
        return new Translations() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public boolean base() {
                return base;
            }

            @Override
            public String get(String key) {
                return entries.get(key);
            }
        };
    }

    private static Translations base(Map<String, String> entries) {
        return language("en-us", true, entries);
    }

    private static Element parse(String html, Translations translations, Diagnostics diag) {
        Template t = TemplateParser.parse(html, "t.html", TagRegistry.HTML, null, translations, false, diag);
        return (Element) t.roots().get(0);
    }

    private static List<String> codes(String html, Translations translations) {
        Diagnostics diag = new Diagnostics();
        parse(html, translations, diag);
        return DiagCodes.of(diag);
    }

    /** Text as itself, elements as {@code <tag.class>children</tag>}. */
    private static String shape(Node n) {
        if (n instanceof TextNode t) {
            return t.text();
        }
        Element e = (Element) n;
        String cls = e.classes().stream().sorted().map(c -> "." + c).collect(Collectors.joining());
        return "<" + e.tag() + cls + ">" + e.children().stream().map(TranslationTest::shape).collect(Collectors.joining())
                + "</" + e.tag() + ">";
    }

    @Test
    void argumentsAndMarksAreLaidOutWhereTheTranslationPutsThem() {
        Diagnostics diag = new Diagnostics();
        Element div = parse("""
                <div t="m">
                  <span class="n" bind:text="n"></span>
                  <span mark="hl" class="hl"></span>
                  <ix-tooltip>tip</ix-tooltip>
                </div>""", base(Map.of("m", "Got %s <hl>coins</hl>, <hl>gems</hl> 100%%\nmore")), diag);
        assertEquals(List.of(), DiagCodes.of(diag));
        assertEquals("<div>Got <span.n></span> <span.hl>coins</span>, <span.hl>gems</span> 100%" + TextShaper.LINE_BREAK
                + "more<ix-tooltip>tip</ix-tooltip></div>", shape(div));
    }

    @Test
    void anArgumentUsedTwiceIsTheElementFirstAndACopyAfter() {
        Diagnostics diag = new Diagnostics();
        Element div = parse("<div t=\"m\"><span class=\"a\"></span><span class=\"b\"></span></div>",
                base(Map.of("m", "%2$s %1$s %2$s")), diag);
        Element b = (Element) div.children().get(0);
        assertEquals("<div><span.b></span> <span.a></span> <span.b></span></div>", shape(div));
        Element copy = (Element) div.children().get(4);
        assertNotSame(b, copy);
        assertSame(div, b.parent());
    }

    @Test
    void anIncompleteTranslationIsAnErrorInTheBaseLanguageAndAWarningElsewhere() {
        String html = "<div t=\"m\"><span></span><span mark=\"hl\"></span></div>";
        Map<String, String> entries = Map.of("m", "%2$s %d <hl>x");
        assertEquals(List.of("i18n/invalid-translation@t.html:1:9", "i18n/invalid-translation@t.html:1:9",
                        "i18n/invalid-translation@t.html:1:9", "i18n/invalid-translation@t.html:1:9"),
                codes(html, base(entries)));
        assertEquals(List.of("i18n/locale-translation@t.html:1:9", "i18n/locale-translation@t.html:1:9",
                "i18n/locale-translation@t.html:1:9"), codes(html, language("zh-cn", false, entries)));
    }

    @Test
    void aMissingKeyIsAnErrorAndTheElementKeepsItsChildren() {
        Diagnostics diag = new Diagnostics();
        Element div = parse("<div t=\"gone\"><span></span></div>", base(Map.of()), diag);
        assertEquals(List.of("i18n/missing-translation@t.html:1:9"), DiagCodes.of(diag));
        assertEquals("<div><span></span></div>", shape(div));
    }

    @Test
    void aTagNamingNoMarkIsShownAsTextWithAWarning() {
        Diagnostics diag = new Diagnostics();
        Element div = parse("<div t=\"m\"></div>", base(Map.of("m", "a <b>b</b> < c")), diag);
        assertEquals(List.of("i18n/undeclared-mark@t.html:1:9", "i18n/undeclared-mark@t.html:1:9"), DiagCodes.of(diag));
        assertEquals("<div>a <b>b</b> < c</div>", shape(div));
    }

    @Test
    void attributeTranslationsSetTitleAndPlaceholderOnly() {
        Diagnostics diag = new Diagnostics();
        Element input = parse("<input type=\"text\" t:placeholder=\"p\" t:title=\"h\"/>",
                base(Map.of("p", "Search…", "h", "Find items")), diag);
        assertEquals(List.of(), DiagCodes.of(diag));
        assertEquals("Search…", input.attribute("placeholder"));
        assertEquals("Find items", input.attribute("title"));
        assertEquals(List.of("i18n/invalid-translated-attribute@t.html:1:6", "i18n/invalid-translated-attribute@t.html:1:28"),
                codes("<div t:class=\"p\" title=\"x\" t:title=\"h\"></div>", base(Map.of("p", "a", "h", "b"))));
    }

    @Test
    void contentThatIsNotTheElementsOwnCannotBeTranslated() {
        Translations t = base(Map.of("m", "x"));
        assertEquals(List.of("i18n/invalid-message-element@t.html:1:20"), codes("<input type=\"text\" t=\"m\"/>", t));
        assertEquals(List.of("i18n/invalid-message-element@t.html:1:21"), codes("<span bind:text=\"a\" t=\"m\"></span>", t));
        assertEquals(List.of("i18n/invalid-message-child@t.html:1:12", "i18n/invalid-message-child@t.html:1:16"),
                codes("<div t=\"m\">text<if test=\"a\"><span></span></if></div>", t));
        assertEquals(List.of("i18n/invalid-message-child@t.html:1:34", "i18n/invalid-message-child@t.html:1:56"),
                codes("<div t=\"m\"><span mark=\"a\"></span><span mark=\"a\"></span><span mark=\"b\">x</span></div>", t));
        assertEquals(List.of("i18n/misplaced-mark@t.html:1:6"), codes("<div><span mark=\"a\"></span></div>", t));
    }

    @Test
    void literalTextIsReportedOnlyWhenEnabledAndNotUnderTranslateNo() {
        String html = """
                <div title="Hover">
                  <span>Hello</span>
                  <span translate="no">Ixchel<span>UI</span></span>
                  <span>&gt; 42</span>
                </div>""";
        assertEquals(List.of(), codes(html, Translations.NONE));
        Diagnostics diag = new Diagnostics(Set.of(TemplateCode.LITERAL_TEXT));
        TemplateParser.parse(html, "t.html", TagRegistry.HTML, diag);
        assertEquals(List.of("i18n/literal-text@t.html:1:1", "i18n/literal-text@t.html:2:9"), DiagCodes.of(diag));
    }
}
