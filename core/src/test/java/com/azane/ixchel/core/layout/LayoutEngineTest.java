package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.style.StyleEngine;
import com.azane.ixchel.core.style.StyleSheet;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.template.TemplateParser;
import com.azane.ixchel.core.text.TextMeasurer;
import com.azane.ixchel.core.text.TextShaper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutEngineTest {
    private static final TextMeasurer MONO = new TextMeasurer() {
        @Override
        public float width(String text, float size) {
            return text.length() * 6;
        }

        @Override
        public float lineHeight(float size) {
            return size;
        }
    };

    @Test
    void colorOnlyRestyleDoesNotRunTaffyButASizeChangeDoes() {
        Diagnostics diag = new Diagnostics();
        Element root = new Element("#root", new SourcePos("t.html", 1, 1));
        for (Node n : TemplateParser.parse("<div class=\"a\">x</div>", "t.html", TagRegistry.HTML, diag).roots()) {
            root.appendChild(n);
        }
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(
                ".a { width: 10px } .hot { color: #f00 } .wide { width: 20px }", "t.css", diag), CssProperties.REGISTRY, diag);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
        LayoutEngine layout = new LayoutEngine(new TextShaper(MONO), CssProperties.REGISTRY);
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer());
        assertEquals(1, layout.computeCount);

        Element a = (Element) root.children().get(0);
        a.addClass("hot");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer());
        assertEquals(1, layout.computeCount);

        a.addClass("wide");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer());
        assertEquals(2, layout.computeCount);
        assertEquals(20, a.box().width());
    }

    @Test
    void equalWidthTextChangeSkipsTaffyButAWiderOneReflows() {
        Diagnostics diag = new Diagnostics();
        Element root = new Element("#root", new SourcePos("t.html", 1, 1));
        for (Node n : TemplateParser.parse("<div class=\"row\"><span>x5</span><div class=\"mark\"></div></div>",
                "t.html", TagRegistry.HTML, diag).roots()) {
            root.appendChild(n);
        }
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(
                ".row { flex-direction: row; align-items: flex-start } .mark { width: 2px; height: 2px }",
                "t.css", diag), CssProperties.REGISTRY, diag);
        StyleEngine style = new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
        LayoutEngine layout = new LayoutEngine(new TextShaper(MONO), CssProperties.REGISTRY);
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer());
        Element row = (Element) root.children().get(0);
        TextNode text = (TextNode) ((Element) row.children().get(0)).children().get(0);
        Element mark = (Element) row.children().get(1);
        assertEquals(1, layout.computeCount);
        assertEquals(12, mark.box().x());

        text.setText("x6");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer());
        assertEquals(1, layout.computeCount);
        assertEquals("x6", layout.lines(text).get(0).text());

        text.setText("x10");
        style.restyle(root);
        layout.layout(root, 100, 100, new TopLayer());
        assertEquals(2, layout.computeCount);
        assertEquals(18, mark.box().x());
        assertEquals("x10", layout.lines(text).get(0).text());
    }
}
