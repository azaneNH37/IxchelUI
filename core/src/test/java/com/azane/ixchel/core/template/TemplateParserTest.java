package com.azane.ixchel.core.template;

import com.azane.ixchel.core.runtime.DiagCodes;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Directive;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateParserTest {
    private static Element el(Node n) {
        return (Element) n;
    }

    private static String text(Node n) {
        return ((TextNode) n).text();
    }

    /** {@code e}'s element children, without the whitespace between them. */
    private static List<Element> elements(Node e) {
        return el(e).children().stream().filter(n -> n instanceof Element).map(n -> (Element) n).toList();
    }

    @Test
    void goldenHudParsesIntoExpectedTree() throws IOException {
        String source;
        try (InputStream in = TemplateParserTest.class.getResourceAsStream("/golden/ammo-hud/hud.html")) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse(source, "hud.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertTrue(diag.entries().isEmpty(), diag.entries().toString());
        assertEquals(1, t.roots().size());
        Element hand = el(t.roots().get(0));
        assertEquals("div", hand.tag());
        assertTrue(hand.hasClass("hand"));
        List<Element> parts = elements(hand);
        assertEquals(3, parts.size());
        Element gun = parts.get(0);
        assertEquals("img", gun.tag());
        assertEquals("tacz:textures/hud/gun.png", gun.attribute("src"));
        assertTrue(parts.get(1).children().isEmpty());
        Element info = parts.get(2);
        Element counts = elements(info).get(0);
        assertEquals("030", text(elements(counts).get(0).children().get(0)));
        Element side = elements(counts).get(1);
        assertEquals("0120", text(elements(side).get(0).children().get(0)));
        assertEquals("tacz:textures/hud/fire_mode_auto.png", elements(side).get(1).attribute("src"));
        Element version = elements(info).get(1);
        assertTrue(version.hasClass("version"));
        assertEquals("v1.1.4", text(version.children().get(0)));
    }

    @Test
    void mismatchedCloseTagIsReportedAtTheClosingTag() {
        Diagnostics diag = new Diagnostics();
        TemplateParser.parse("<div>\n  <span>x</div>\n</div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals("template/mismatched-closing-tag@t.html:2:10", DiagCodes.of(diag.entries().get(0)));
    }

    @Test
    void unquotedAttributeValueIsAnError() {
        Diagnostics diag = new Diagnostics();
        TemplateParser.parse("<div class=hand></div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals(List.of("template/unquoted-attribute@t.html:1:6"), DiagCodes.of(diag));
    }

    @Test
    void unknownTagAndUnknownAttributeAreBothReported() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<div onclick=\"x\"><marquee>go</marquee></div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals(List.of("template/unknown-attribute@t.html:1:6", "template/unknown-tag@t.html:1:18"), DiagCodes.of(diag));
        assertTrue(el(t.roots().get(0)).children().isEmpty());
    }

    @Test
    void entitiesAreDecodedInTextAndAttributes() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<span id=\"a&amp;b&#x41;\">&lt;3 &#65;&quot;</span>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        Element span = el(t.roots().get(0));
        assertEquals("a&bA", span.attribute("id"));
        assertEquals("<3 A\"", text(span.children().get(0)));

        Diagnostics bad = new Diagnostics();
        TemplateParser.parse("<span>a &nbsp; b</span>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, bad);
        assertEquals(List.of("template/invalid-character-reference@t.html:1:9"), DiagCodes.of(bad));
    }

    @Test
    void styleContentIsCapturedRawWithStartPosition() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<style>\n  .a { color: red; }\n</style>\n<div/>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        assertEquals(1, t.roots().size());
        Template.Inline s = (Template.Inline) t.styles().get(0);
        assertEquals("\n  .a { color: red; }\n", s.css());
        assertEquals(new SourcePos("t.html", 1, 8), s.start());
    }

    @Test
    void linksAndStylesAreCollectedInDocumentOrderAndLeaveTheTree() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("""
                <link rel="stylesheet" href="a.css"/>
                <style>.a {}</style>
                <div/>
                <link rel="stylesheet" href="../b.css"/>
                """, "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        assertEquals(1, t.roots().size());
        assertEquals(List.of(new Template.Link("a.css", new SourcePos("t.html", 1, 1)),
                new Template.Inline(".a {}", new SourcePos("t.html", 2, 8)),
                new Template.Link("../b.css", new SourcePos("t.html", 4, 1))), t.styles());

        Diagnostics nested = new Diagnostics();
        Template n = TemplateParser.parse("<div><link rel=\"stylesheet\" href=\"a.css\"/></div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, nested);
        assertEquals(List.of("template/misplaced-element@t.html:1:6"), DiagCodes.of(nested));
        assertTrue(n.styles().isEmpty());
        assertTrue(el(n.roots().get(0)).children().isEmpty());
    }

    @Test
    void aTooltipBelongsToAnElementAndTheFormControlsTakeItDirectly() {
        Diagnostics ok = new Diagnostics();
        TemplateParser.parse("""
                <div><input> <ix-tooltip>a</ix-tooltip> </input>
                <select><ix-tooltip>b</ix-tooltip><option value="x">X</option></select>
                <img src="a.png"><ix-tooltip>c</ix-tooltip></img></div>
                """, "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, ok);
        assertFalse(ok.hasErrors(), ok.entries().toString());

        Diagnostics bad = new Diagnostics();
        TemplateParser.parse("""
                <ix-tooltip>top</ix-tooltip>
                <div><ix-tooltip><span><ix-tooltip>nested</ix-tooltip></span></ix-tooltip></div>
                <img src="a.png"><span>x</span></img>
                <select><for each="xs" as="x" key="x"><ix-tooltip>c</ix-tooltip></for></select>
                """, "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, bad);
        assertEquals(List.of("template/misplaced-element@t.html:1:1", "template/misplaced-element@t.html:2:24",
                "template/invalid-children@t.html:3:18", "template/invalid-children@t.html:4:1"), DiagCodes.of(bad));
    }

    @Test
    void whitespaceCollapsesAndIsKeptOnlyBetweenSiblings() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<div>\n  <span>  a \n\t b  </span>\n  <span>c</span>\n</div>\n<div></div>",
                "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals(2, t.roots().size(), "whitespace between top-level nodes is dropped");
        Element div = el(t.roots().get(0));
        assertEquals(3, div.children().size(), "leading and trailing whitespace is dropped, the gap stays");
        assertEquals(" ", text(div.children().get(1)));
        Node text = el(div.children().get(0)).children().get(0);
        assertEquals(" a b ", text(text), "the ends stay for layout to drop at line edges");
        assertEquals(new SourcePos("t.html", 2, 9), text.pos());
    }

    @Test
    void bindAttributesWithSeveralColonsKeepNameAndValuePositions() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<div class=\"ammo\"\n     bind:class:empty=\"isEmpty\"></div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        Element div = el(t.roots().get(0));
        assertEquals(List.of(new Directive("bind:class:empty", "isEmpty",
                new SourcePos("t.html", 2, 6), new SourcePos("t.html", 2, 24))), div.directives());
        assertTrue(div.attributes().isEmpty());
    }

    @Test
    void hostRegisteredTagParsesWhileUnregisteredOnesStillFail() {
        TagRegistry tags = com.azane.ixchel.core.html.HtmlElements.TAGS.extend().add(com.azane.ixchel.core.dom.ElementType.plain("item", "count")).build();
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<item count=\"3\"/><item stack=\"x\"/>", "t.html", tags, diag);
        assertEquals("3", el(t.roots().get(0)).attribute("count"));
        assertEquals(List.of("template/unknown-attribute@t.html:1:24"), DiagCodes.of(diag));

        Diagnostics plain = new Diagnostics();
        TemplateParser.parse("<item/>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, plain);
        assertEquals(List.of("template/unknown-tag@t.html:1:1"), DiagCodes.of(plain));
    }
}
