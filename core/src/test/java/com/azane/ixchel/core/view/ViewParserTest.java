package com.azane.ixchel.core.view;

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

class ViewParserTest {
    /** Parses {@code html} as a page's content, on its first line after {@code <page>}. */
    private static ParsedView page(String html, String file, TagRegistry tags, Diagnostics diag) {
        return ViewParser.parse("<page>" + html + "</page>", file, tags, diag);
    }

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
        try (InputStream in = ViewParserTest.class.getResourceAsStream("/golden/ammo-hud/hud.html")) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Diagnostics diag = new Diagnostics();
        ParsedView t = ViewParser.parse(source, "hud.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
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
        page("<div>\n  <span>x</div>\n</div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals("view/mismatched-closing-tag@t.html:2:10", DiagCodes.of(diag.entries().get(0)));
    }

    @Test
    void unquotedAttributeValueIsAnError() {
        Diagnostics diag = new Diagnostics();
        page("<div class=hand></div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals(List.of("view/unquoted-attribute@t.html:1:12"), DiagCodes.of(diag));
    }

    @Test
    void unknownTagAndUnknownAttributeAreBothReported() {
        Diagnostics diag = new Diagnostics();
        ParsedView t = page("<div onclick=\"x\"><marquee>go</marquee></div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals(List.of("view/unknown-attribute@t.html:1:12", "view/unknown-tag@t.html:1:24"), DiagCodes.of(diag));
        assertTrue(el(t.roots().get(0)).children().isEmpty());
    }

    @Test
    void entitiesAreDecodedInTextAndAttributes() {
        Diagnostics diag = new Diagnostics();
        ParsedView t = page("<span id=\"a&amp;b&#x41;\">&lt;3 &#65;&quot;</span>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        Element span = el(t.roots().get(0));
        assertEquals("a&bA", span.attribute("id"));
        assertEquals("<3 A\"", text(span.children().get(0)));

        Diagnostics bad = new Diagnostics();
        page("<span>a &nbsp; b</span>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, bad);
        assertEquals(List.of("view/invalid-character-reference@t.html:1:15"), DiagCodes.of(bad));
    }

    @Test
    void theRootDeclaresTheKindAndOnlyCommentsAndWhitespaceSurroundIt() {
        Diagnostics diag = new Diagnostics();
        ParsedView page = ViewParser.parse("<!-- c -->\n<page><div/></page>\n", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        assertEquals(ParsedView.Kind.PAGE, page.kind());
        assertEquals(1, page.roots().size());
        assertTrue(page.roots().get(0).parent() == null, "the root is not part of the tree");

        ParsedView component = ViewParser.parse("<component name=\"x-a\"><link href=\"a.css\"/><slot></slot></component>",
                "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        assertEquals(ParsedView.Kind.COMPONENT, component.kind());
        assertEquals("x-a", component.component().name());
        assertEquals("a.css", component.stylesheet().href());

        Diagnostics bad = new Diagnostics();
        ParsedView extra = ViewParser.parse("<div/>\nx<page><slot></slot><page></page></page><page></page>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, bad);
        assertEquals(ParsedView.Kind.PAGE, extra.kind());
        assertEquals(List.of("view/misplaced-element@t.html:2:8", "view/misplaced-element@t.html:2:21",
                "view/missing-root@t.html:1:1", "view/missing-root@t.html:1:7",
                "view/missing-root@t.html:2:41"), DiagCodes.of(bad));
        Diagnostics empty = new Diagnostics();
        ViewParser.parse("<!-- nothing -->", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, empty);
        assertEquals(List.of("view/missing-root@t.html:1:1"), DiagCodes.of(empty));
    }

    @Test
    void linksComeFirstInTheRootWhichKeepsOneStylesheet() {
        Diagnostics diag = new Diagnostics();
        ParsedView t = ViewParser.parse("""
                <page>
                <link href="a.css"/>
                <link href="b.css"/>
                <div/>
                <link href="c.css"/>
                <div><link href="d.css"/></div>
                <link rel="stylesheet" href="e.css"></link>
                </page>""", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
        assertEquals(new ParsedView.Link("a.css", new SourcePos("t.html", 2, 1)), t.stylesheet());
        assertEquals(2, t.roots().size());
        assertTrue(el(t.roots().get(1)).children().isEmpty());
        assertEquals(List.of("view/invalid-link@t.html:3:1", "view/link-after-element@t.html:5:1",
                "view/misplaced-element@t.html:6:6", "view/unknown-attribute@t.html:7:7", "view/invalid-link@t.html:7:1",
                "view/link-after-element@t.html:7:1"), DiagCodes.of(diag));

        Diagnostics unlinked = new Diagnostics();
        ViewParser.parse("<page><link href=\"x.html\"/></page>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, unlinked);
        assertEquals(List.of("view/link-unavailable@t.html:1:7"), DiagCodes.of(unlinked));
    }

    @Test
    void aTooltipBelongsToAnElementAndTheFormControlsTakeItDirectly() {
        Diagnostics ok = new Diagnostics();
        page("""
                <div><input> <ix-tooltip>a</ix-tooltip> </input>
                <select><ix-tooltip>b</ix-tooltip><option value="x">X</option></select>
                <img src="a.png"><ix-tooltip>c</ix-tooltip></img></div>
                """, "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, ok);
        assertFalse(ok.hasErrors(), ok.entries().toString());

        Diagnostics bad = new Diagnostics();
        page("""
                <ix-tooltip>top</ix-tooltip>
                <div><ix-tooltip><span><ix-tooltip>nested</ix-tooltip></span></ix-tooltip></div>
                <img src="a.png"><span>x</span></img>
                <select><for each="xs" as="x" key="x"><ix-tooltip>c</ix-tooltip></for></select>
                """, "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, bad);
        assertEquals(List.of("view/misplaced-element@t.html:1:7", "view/misplaced-element@t.html:2:24",
                "view/invalid-children@t.html:3:18", "view/invalid-children@t.html:4:1"), DiagCodes.of(bad));
    }

    @Test
    void whitespaceCollapsesAndIsKeptOnlyBetweenSiblings() {
        Diagnostics diag = new Diagnostics();
        ParsedView t = page("<div>\n  <span>  a \n\t b  </span>\n  <span>c</span>\n</div>\n<div></div>",
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
        ParsedView t = page("<div class=\"ammo\"\n     bind:class:empty=\"isEmpty\"></div>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag);
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
        ParsedView t = page("<item count=\"3\"/><item stack=\"x\"/>", "t.html", tags, diag);
        assertEquals("3", el(t.roots().get(0)).attribute("count"));
        assertEquals(List.of("view/unknown-attribute@t.html:1:30"), DiagCodes.of(diag));

        Diagnostics plain = new Diagnostics();
        page("<item/>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, plain);
        assertEquals(List.of("view/unknown-tag@t.html:1:7"), DiagCodes.of(plain));
    }
}
