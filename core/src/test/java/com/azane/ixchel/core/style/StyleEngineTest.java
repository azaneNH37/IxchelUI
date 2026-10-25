package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleEngineTest {
    private static final SourcePos POS = new SourcePos("t.html", 1, 1);

    private static StyleEngine engine(String css, Diagnostics diag) {
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(css, "t.css", diag), CssProperties.REGISTRY, diag);
        return new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
    }

    private static Element el(Element parent, String tag, String... classes) {
        Element e = new Element(tag, POS);
        for (String c : classes) {
            e.addClass(c);
        }
        parent.appendChild(e);
        return e;
    }

    @Test
    void cascadeOrdersBySpecificityThenSourceThenInlineThenImportant() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("""
                #x { width: 1px }
                .a { width: 2px; height: 5px !important }
                div.a { width: 3px }
                div.a { width: 4px }
                .b { color: #f00 !important }
                """, diag);
        Element root = new Element("#root", POS);
        Element e = el(root, "div", "a", "b");
        e.setAttribute("id", "x");
        Element f = el(root, "div", "a");
        f.setInlineStyle(CssParser.parseDeclarations("width: 9px; height: 7px; color: blue", POS, diag));
        engine.restyle(root);

        assertEquals(List.of(), diag.entries());
        assertEquals(Length.px(1), e.computed().get(CssProperties.WIDTH));
        assertEquals(0xFFFF0000, e.computed().get(CssProperties.COLOR));
        assertEquals(Length.px(9), f.computed().get(CssProperties.WIDTH));
        assertEquals(Length.px(5), f.computed().get(CssProperties.HEIGHT));
    }

    @Test
    void parentClassChangeRestylesInheritedValuesButLeavesCleanSiblingsAlone() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("""
                .warn { color: #ff0 }
                .warn > span { font-size: 18px }
                """, diag);
        Element root = new Element("#root", POS);
        Element panel = el(root, "div");
        Element span = el(panel, "span");
        Element sibling = el(root, "div");
        engine.restyle(root);
        ComputedStyle siblingBefore = sibling.computed();
        ComputedStyle spanBefore = span.computed();

        panel.addClass("warn");
        engine.restyle(root);

        assertNotSame(spanBefore, span.computed());
        assertEquals(0xFFFFFF00, span.computed().get(CssProperties.COLOR));
        assertEquals(Length.px(18), span.computed().get(CssProperties.FONT_SIZE));
        assertSame(siblingBefore, sibling.computed());
    }

    @Test
    void descendantChildAndHoverSelectorsMatchOnlyTheirTargets() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("""
                .list .item { width: 1px }
                .list > .item { height: 1px }
                .item:hover { color: #000 }
                """, diag);
        Element root = new Element("#root", POS);
        Element list = el(root, "div", "list");
        Element direct = el(list, "div", "item");
        Element nested = el(el(list, "div"), "div", "item");
        direct.setState(Element.HOVER, true);
        engine.restyle(root);

        assertEquals(Length.px(1), nested.computed().get(CssProperties.WIDTH));
        assertEquals(Length.AUTO, nested.computed().get(CssProperties.HEIGHT));
        assertEquals(Length.px(1), direct.computed().get(CssProperties.HEIGHT));
        assertEquals(0xFF000000, direct.computed().get(CssProperties.COLOR));
        assertEquals(0xFFFFFFFF, nested.computed().get(CssProperties.COLOR));
    }

    @Test
    void shorthandExpandsAndInvalidInputIsReportedAtItsPosition() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("""
                .a { margin: 1px 2px; padding: 3px }
                .a + .b { width: 1px }
                .c { widht: 1px; height: 10; color: #12 }
                """, diag);
        Element root = new Element("#root", POS);
        Element a = el(root, "div", "a");
        engine.restyle(root);

        assertEquals(Length.px(1), a.computed().get(CssProperties.MARGIN_BOTTOM));
        assertEquals(Length.px(2), a.computed().get(CssProperties.MARGIN_LEFT));
        assertEquals(Length.px(3), a.computed().get(CssProperties.PADDING_RIGHT));
        List<String> errors = diag.entries().stream().map(Diagnostic::toString).toList();
        assertEquals(4, errors.size(), errors::toString);
        assertTrue(errors.get(0).startsWith("t.css:2:4: error: unsupported combinator"), errors.get(0));
        assertTrue(errors.get(1).startsWith("t.css:3:6: error: unsupported property 'widht'"), errors.get(1));
        assertTrue(errors.get(2).contains("t.css:3:26:") && errors.get(2).contains("10px"), errors.get(2));
        assertTrue(errors.get(3).startsWith("t.css:3:37: error: invalid hex color"), errors.get(3));
    }
}
