package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.Presence;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.style.StyleEngine;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.core.view.ViewParser;
import com.azane.ixchel.core.text.TextMeasurer;
import com.azane.ixchel.core.text.TextShaper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutEngineTest {
    private static final TextMeasurer MONO = new TextMeasurer() {
        @Override
        public float width(String text, float size, boolean bold) {
            return text.length() * 6;
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

    @Test
    void colorOnlyRestyleDoesNotRunTaffyButASizeChangeDoes() {
        Diagnostics diag = new Diagnostics();
        Element root = new Element(ElementType.plain("#root"), new SourcePos("t.html", 1, 1));
        for (Node n : ViewParser.parse("<page>" + "<div class=\"a\">x</div>" + "</page>", "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag).roots()) {
            root.appendChild(n);
        }
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(
                ".a { width: 10px } .hot { color: #f00 } .wide { width: 20px }", "t.css", diag), CssProperties.REGISTRY, diag);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
        LayoutEngine layout = new LayoutEngine(new TextShaper(MONO), CssProperties.REGISTRY, diag);
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer(Presence.instant()));
        assertEquals(1, layout.computeCount);

        Element a = (Element) root.children().get(0);
        a.addClass("hot");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer(Presence.instant()));
        assertEquals(1, layout.computeCount);

        a.addClass("wide");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer(Presence.instant()));
        assertEquals(2, layout.computeCount);
        assertEquals(20, a.box().width());
    }

    @Test
    void equalWidthTextChangeSkipsTaffyButAWiderOneReflows() {
        Diagnostics diag = new Diagnostics();
        Element root = new Element(ElementType.plain("#root"), new SourcePos("t.html", 1, 1));
        for (Node n : ViewParser.parse("<page>" + "<div class=\"row\"><span>x5</span><div class=\"mark\"></div></div>" + "</page>",
                "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag).roots()) {
            root.appendChild(n);
        }
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(
                ".row { flex-direction: row; align-items: flex-start } .mark { width: 2px; height: 2px }",
                "t.css", diag), CssProperties.REGISTRY, diag);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
        LayoutEngine layout = new LayoutEngine(new TextShaper(MONO), CssProperties.REGISTRY, diag);
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer(Presence.instant()));
        Element row = (Element) root.children().get(0);
        TextNode text = (TextNode) ((Element) row.children().get(0)).children().get(0);
        Element mark = (Element) row.children().get(1);
        assertEquals(1, layout.computeCount);
        assertEquals(12, mark.box().x());

        text.setText("x6");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer(Presence.instant()));
        assertEquals(1, layout.computeCount);
        assertEquals("x6", layout.fragments(text).get(0).text());

        text.setText("x10");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer(Presence.instant()));
        assertEquals(2, layout.computeCount);
        assertEquals(18, mark.box().x());
        assertEquals("x10", layout.fragments(text).get(0).text());
    }

    @Test
    void theTooltipIsPlacedAtThePointerFlipsAtTheEdgesAndNeverReflowsTheDocument() {
        Diagnostics diag = new Diagnostics();
        Element root = new Element(ElementType.plain("#root"), new SourcePos("t.html", 1, 1));
        for (Node n : ViewParser.parse("<page>" + "<div class=\"a\">x<ix-tooltip class=\"tip\">t</ix-tooltip></div>" + "</page>",
                "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag).roots()) {
            root.appendChild(n);
        }
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(
                ".a { width: 10px } .tip { width: 30px; height: 30px }", "t.css", diag), CssProperties.REGISTRY, diag);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
        LayoutEngine layout = new LayoutEngine(new TextShaper(MONO), CssProperties.REGISTRY, diag);
        TopLayer top = new TopLayer(Presence.instant());
        Element a = (Element) root.children().get(0);
        Element tip = (Element) a.children().get(1);
        style.restyle(root);
        layout.layout(root, 100, 100, top);
        assertEquals(1, layout.computeCount);
        assertEquals(10, a.box().width(), "the tooltip takes no room in its parent");

        top.showAtPointer(tip, 50, 50);
        style.restyle(root);
        layout.layout(root, 100, 100, top);
        assertEquals(58, tip.box().x(), "right of the pointer");
        assertEquals(34, tip.box().y(), "and above it");

        top.showAtPointer(tip, 80, 90);
        layout.placeAtPointer(top);
        assertEquals(42, tip.box().x(), "flipped left of the pointer at the right edge");
        assertEquals(70, tip.box().y(), "moved up to stay above the bottom edge");

        top.showAtPointer(null, 0, 0);
        style.restyle(root);
        layout.layout(root, 100, 100, top);
        assertEquals(1, layout.computeCount, "showing, moving and hiding the tooltip never laid the document out again");
    }

    @Test
    void aTooltipNarrowedByMaxWidthIsAsTallAsItsWrappedText() {
        Diagnostics diag = new Diagnostics();
        Element root = new Element(ElementType.plain("#root"), new SourcePos("t.html", 1, 1));
        for (Node n : ViewParser.parse("<page>" + "<div>x<ix-tooltip class=\"tip\"><div class=\"text\">aaaa bbbb cccc</div></ix-tooltip></div>" + "</page>",
                "t.html", com.azane.ixchel.core.html.HtmlElements.TAGS, diag).roots()) {
            root.appendChild(n);
        }
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(
                ".tip { flex-direction: column; max-width: 30px } .text { display: block }", "t.css", diag),
                CssProperties.REGISTRY, diag);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
        LayoutEngine layout = new LayoutEngine(new TextShaper(MONO), CssProperties.REGISTRY, diag);
        TopLayer top = new TopLayer(Presence.instant());
        Element tip = (Element) ((Element) root.children().get(0)).children().get(1);
        Element text = (Element) tip.children().get(0);
        top.showAtPointer(tip, 10, 50);
        style.restyle(root);
        layout.layout(root, 100, 100, top);
        assertTrue(tip.box().width() <= 30, "narrowed to its max-width");
        assertEquals(text.box().height(), tip.box().height(), "the tooltip holds all three wrapped lines");
        assertEquals(27, text.box().height(), "three 9px lines");
    }
}
