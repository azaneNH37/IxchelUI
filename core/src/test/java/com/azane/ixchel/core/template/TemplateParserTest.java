package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
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

    @Test
    void goldenHudParsesIntoExpectedTree() throws IOException {
        String source;
        try (InputStream in = TemplateParserTest.class.getResourceAsStream("/golden/ammo-hud/hud.html")) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse(source, "hud.html", diag);
        assertTrue(diag.entries().isEmpty(), diag.entries().toString());
        assertEquals(1, t.roots().size());
        Element hand = el(t.roots().get(0));
        assertEquals("div", hand.tag());
        assertTrue(hand.hasClass("hand"));
        assertEquals(3, hand.children().size());
        Element gun = el(hand.children().get(0));
        assertEquals("img", gun.tag());
        assertEquals("tacz:textures/hud/gun.png", gun.attribute("src"));
        assertTrue(el(hand.children().get(1)).children().isEmpty());
        Element info = el(hand.children().get(2));
        Element counts = el(info.children().get(0));
        assertEquals("030", text(el(counts.children().get(0)).children().get(0)));
        Element side = el(counts.children().get(1));
        assertEquals("0120", text(el(side.children().get(0)).children().get(0)));
        assertEquals("tacz:textures/hud/fire_mode_auto.png", el(side.children().get(1)).attribute("src"));
        Element version = el(info.children().get(1));
        assertTrue(version.hasClass("version"));
        assertEquals("v1.1.4", text(version.children().get(0)));
    }

    @Test
    void mismatchedCloseTagReportsBothTagsAndPositions() {
        Diagnostics diag = new Diagnostics();
        TemplateParser.parse("<div>\n  <span>x</div>\n</div>", "t.html", diag);
        Diagnostic d = diag.entries().get(0);
        assertEquals(new SourcePos("t.html", 2, 10), d.pos());
        assertTrue(d.message().contains("</div>") && d.message().contains("<span>")
                && d.message().contains("t.html:2:3"), d.message());
    }

    @Test
    void unquotedAttributeValueIsAnError() {
        Diagnostics diag = new Diagnostics();
        TemplateParser.parse("<div class=hand></div>", "t.html", diag);
        assertEquals(1, diag.entries().size(), diag.entries().toString());
        assertEquals(new SourcePos("t.html", 1, 6), diag.entries().get(0).pos());
    }

    @Test
    void unknownTagAndUnknownAttributeAreBothReported() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<div onclick=\"x\"><button>go</button></div>", "t.html", diag);
        List<Diagnostic> errors = diag.entries();
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).message().contains("unknown attribute 'onclick' on <div>"));
        assertTrue(errors.get(1).message().contains("unknown tag <button>"));
        assertTrue(el(t.roots().get(0)).children().isEmpty());
    }

    @Test
    void entitiesAreDecodedInTextAndAttributes() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<span id=\"a&amp;b&#x41;\">&lt;3 &#65;&quot;</span>", "t.html", diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        Element span = el(t.roots().get(0));
        assertEquals("a&bA", span.attribute("id"));
        assertEquals("<3 A\"", text(span.children().get(0)));

        Diagnostics bad = new Diagnostics();
        TemplateParser.parse("<span>a &nbsp; b</span>", "t.html", bad);
        assertEquals(new SourcePos("t.html", 1, 9), bad.entries().get(0).pos());
    }

    @Test
    void styleContentIsCapturedRawWithStartPosition() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<style>\n  .a { color: red; }\n</style>\n<div/>", "t.html", diag);
        assertFalse(diag.hasErrors(), diag.entries().toString());
        assertEquals(1, t.roots().size());
        Template.StyleSource s = t.styles().get(0);
        assertEquals("\n  .a { color: red; }\n", s.css());
        assertEquals(new SourcePos("t.html", 1, 8), s.start());
    }

    @Test
    void whitespaceOnlyTextDroppedAndInnerWhitespaceCollapsed() {
        Diagnostics diag = new Diagnostics();
        Template t = TemplateParser.parse("<div>\n  <span>  a \n\t b  </span>\n</div>", "t.html", diag);
        Element div = el(t.roots().get(0));
        assertEquals(1, div.children().size());
        Node text = el(div.children().get(0)).children().get(0);
        assertEquals("a b", text(text));
        assertEquals(new SourcePos("t.html", 2, 11), text.pos());
    }
}
