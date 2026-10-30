package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.Diagnostic;
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
        Template t = TemplateParser.parse(source, "hud.html", TagRegistry.HTML, diag);
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
    void mismatchedCloseTagReportsBothTagsAndPositions() {
        Diagnostics diag = new Diagnostics();
        TemplateParser.parse("<div>\n  <span>x</div>\n</div>", "t.html", TagRegistry.HTML, diag);
        Diagnostic d = diag.entries().get(0);
        assertEquals(new SourcePos("t.html", 2, 10), d.pos());
        assertTrue(d.message().contains("</div>") && d.message().contains("<span>")
                && d.message().contains("t.html:2:3"), d.message());
    }

    @Test
    void unquotedAttributeValueIsAnError() {
        Diagnostics diag = new Diagnostics();
        TemplateParser.parse("<div class=hand></div>", "t.html", TagRegistry.HTML, diag);
        assertEquals(1, diag.entries().size(), diag.entries().toString());
        assertEquals(new SourcePos("t.html", 1, 6), diag.entries().get(0).pos());
    }

    @Test
    void unknownTagAndUnknownAttributeAreBothReported() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<div onclick=\"x\"><marquee>go</marquee></div>", "t.html", TagRegistry.HTML, diag);
        List<Diagnostic> errors = diag.entries();
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).message().contains("unknown attribute 'onclick' on <div>"));
        assertTrue(errors.get(1).message().contains("unknown tag <marquee>"));
        assertTrue(el(t.roots().get(0)).children().isEmpty());
    }

    @Test
    void entitiesAreDecodedInTextAndAttributes() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<span id=\"a&amp;b&#x41;\">&lt;3 &#65;&quot;</span>", "t.html", TagRegistry.HTML, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        Element span = el(t.roots().get(0));
        assertEquals("a&bA", span.attribute("id"));
        assertEquals("<3 A\"", text(span.children().get(0)));

        Diagnostics bad = new Diagnostics();
        TemplateParser.parse("<span>a &nbsp; b</span>", "t.html", TagRegistry.HTML, bad);
        assertEquals(new SourcePos("t.html", 1, 9), bad.entries().get(0).pos());
    }

    @Test
    void styleContentIsCapturedRawWithStartPosition() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<style>\n  .a { color: red; }\n</style>\n<div/>", "t.html", TagRegistry.HTML, diag);
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
                """, "t.html", TagRegistry.HTML, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        assertEquals(1, t.roots().size());
        assertEquals(List.of(new Template.Link("a.css", new SourcePos("t.html", 1, 1)),
                new Template.Inline(".a {}", new SourcePos("t.html", 2, 8)),
                new Template.Link("../b.css", new SourcePos("t.html", 4, 1))), t.styles());

        Diagnostics nested = new Diagnostics();
        Template n = TemplateParser.parse("<div><link rel=\"stylesheet\" href=\"a.css\"/></div>", "t.html", TagRegistry.HTML, nested);
        assertEquals(1, nested.entries().size(), nested.entries().toString());
        assertEquals(new SourcePos("t.html", 1, 6), nested.entries().get(0).pos());
        assertTrue(nested.entries().get(0).message().contains("<link> is only allowed at the top level"));
        assertTrue(n.styles().isEmpty());
        assertTrue(el(n.roots().get(0)).children().isEmpty());
    }

    @Test
    void whitespaceCollapsesAndIsKeptOnlyBetweenSiblings() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<div>\n  <span>  a \n\t b  </span>\n  <span>c</span>\n</div>\n<div></div>",
                "t.html", TagRegistry.HTML, diag);
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
        Template t = TemplateParser.parse("<div class=\"ammo\"\n     bind:class:empty=\"isEmpty\"></div>", "t.html", TagRegistry.HTML, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        Element div = el(t.roots().get(0));
        assertEquals(List.of(new Directive("bind:class:empty", "isEmpty",
                new SourcePos("t.html", 2, 6), new SourcePos("t.html", 2, 24))), div.directives());
        assertTrue(div.attributes().isEmpty());
    }

    @Test
    void hostRegisteredTagParsesWhileUnregisteredOnesStillFail() {
        TagRegistry tags = TagRegistry.HTML.extend().tag("item", "count").build();
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<item count=\"3\"/><item stack=\"x\"/>", "t.html", tags, diag);
        assertEquals("3", el(t.roots().get(0)).attribute("count"));
        assertEquals(1, diag.entries().size(), diag.entries().toString());
        assertTrue(diag.entries().get(0).message().contains("unknown attribute 'stack' on <item>"));

        Diagnostics plain = new Diagnostics();
        TemplateParser.parse("<item/>", "t.html", TagRegistry.HTML, plain);
        assertTrue(plain.entries().get(0).message().contains("unknown tag <item>"));
    }

    @Test
    void plannedAttributesAreRejectedAsNotYetSupported() {
        Diagnostics diag = new Diagnostics();
        TemplateParser.parse("<div t=\"go\"></div>", "t.html", TagRegistry.HTML, diag);
        assertEquals(1, diag.entries().size(), diag.entries().toString());
        assertTrue(diag.entries().stream().allMatch(d -> d.message().contains("not supported yet")));
    }
}
