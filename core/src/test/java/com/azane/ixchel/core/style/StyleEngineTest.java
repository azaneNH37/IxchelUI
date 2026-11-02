package com.azane.ixchel.core.style;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.syntax.CssParser;
import com.azane.ixchel.core.runtime.DiagCodes;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.Element;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class StyleEngineTest {
    private static final SourcePos POS = new SourcePos("t.html", 1, 1);

    private static StyleEngine engine(String css, Diagnostics diag) {
        StyleSheet sheet = StyleSheet.compile(CssParser.parseStylesheet(css, "t.css", diag), CssProperties.REGISTRY, diag);
        return new StyleEngine(CssProperties.REGISTRY, List.of(sheet), diag);
    }

    private static Element el(Element parent, String tag, String... classes) {
        Element e = new Element(ElementType.plain(tag), POS);
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
        Element root = new Element(ElementType.plain("#root"), POS);
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
    void attributeSelectorsCountAsClassesAndAnAttributeChangeRestyles() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("""
                input[type=checkbox] { width: 1px }
                .a { width: 2px }
                [type="checkbox"] { height: 3px }
                .a { height: 4px }
                [disabled] { min-width: 5px }
                """, diag);
        Element root = new Element(ElementType.plain("#root"), POS);
        Element e = el(root, "input", "a");
        e.setAttribute("type", "checkbox");
        engine.restyle(root);
        assertEquals(List.of(), diag.entries());
        assertEquals(Length.px(1), e.computed().get(CssProperties.WIDTH), "tag + attribute beats a class");
        assertEquals(Length.px(4), e.computed().get(CssProperties.HEIGHT), "an attribute ties a class; the later rule wins");

        e.setAttribute("type", "text");
        e.setAttribute("disabled", "");
        engine.restyle(root);
        assertEquals(Length.px(2), e.computed().get(CssProperties.WIDTH), "a changed attribute value stops matching");
        assertEquals(Length.px(5), e.computed().get(CssProperties.MIN_WIDTH), "[name] matches any value, the empty one too");
    }

    @Test
    void parentClassChangeRestylesInheritedValuesButLeavesCleanSiblingsAlone() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("""
                .warn { color: #ff0 }
                .warn > span { font-size: 18px }
                """, diag);
        Element root = new Element(ElementType.plain("#root"), POS);
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
        Element root = new Element(ElementType.plain("#root"), POS);
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
        Element root = new Element(ElementType.plain("#root"), POS);
        Element a = el(root, "div", "a");
        engine.restyle(root);

        assertEquals(Length.px(1), a.computed().get(CssProperties.MARGIN_BOTTOM));
        assertEquals(Length.px(2), a.computed().get(CssProperties.MARGIN_LEFT));
        assertEquals(Length.px(3), a.computed().get(CssProperties.PADDING_RIGHT));
        assertEquals(List.of("css/unsupported-combinator@t.css:2:4", "css/unknown-property@t.css:3:6",
                "css/missing-unit@t.css:3:26", "css/invalid-value@t.css:3:37"), DiagCodes.of(diag));
    }

    @Test
    void boundValuesReplaceTheStyleAttributesDeclarationsButNotImportantRules() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine(".a { width: 1px; height: 2px !important; color: #f00 }", diag);
        Element root = new Element(ElementType.plain("#root"), POS);
        Element a = el(root, "div", "a");
        a.setInlineStyle(CssParser.parseDeclarations("width: 9px !important; margin: 3px", POS, diag));
        a.setBoundStyle(CssProperties.WIDTH, Length.px(20));
        a.setBoundStyle(CssProperties.HEIGHT, Length.px(30));
        a.setBoundStyle(CssProperties.MARGIN_TOP, Length.px(7));
        a.setBoundStyle(CssProperties.COLOR, Declarations.Wide.INHERIT);
        engine.restyle(root);

        assertEquals(List.of(), diag.entries());
        assertEquals(Length.px(20), a.computed().get(CssProperties.WIDTH));
        assertEquals(Length.px(2), a.computed().get(CssProperties.HEIGHT));
        assertEquals(Length.px(7), a.computed().get(CssProperties.MARGIN_TOP));
        assertEquals(Length.px(3), a.computed().get(CssProperties.MARGIN_LEFT));
        assertEquals(0xFFFFFFFF, a.computed().get(CssProperties.COLOR));

        a.setBoundStyle(CssProperties.MARGIN_TOP, Length.px(7));
        assertFalse(a.isStyleDirty(), "an equal bound value is no change");
        a.setBoundStyle(CssProperties.WIDTH, null);
        engine.restyle(root);
        assertEquals(Length.px(9), a.computed().get(CssProperties.WIDTH));
    }

    @Test
    void replacingTheInlineStyleKeepsOneCompiledEntryPerElement() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("", diag);
        Element root = new Element(ElementType.plain("#root"), POS);
        Element a = el(root, "div");
        Element b = el(root, "div");
        b.setInlineStyle(CssParser.parseDeclarations("height: 4px", POS, diag));
        for (int i = 0; i < 500; i++) {
            a.setInlineStyle(CssParser.parseDeclarations("width: " + i + "px", POS, diag));
            engine.restyle(root);
        }

        assertEquals(Length.px(499), a.computed().get(CssProperties.WIDTH));
        assertEquals(Length.px(4), b.computed().get(CssProperties.HEIGHT));
        assertEquals(2, engine.inlineCacheSize());
    }

    @Test
    void customPropertiesInheritSubstituteAndAnUnresolvableValueFallsBackToUnsetReportedOnce() {
        Diagnostics diag = new Diagnostics();
        StyleEngine engine = engine("""
                :root { --fg: #00f; --edge: 2px solid var(--fg); --w: var(--loop); --loop: var(--w) }
                .row { --fg: #f00; color: var(--fg); border: var(--edge) }
                .fb { width: var(--missing, 5px); background-color: var(--loop, #0f0) }
                .bad { color: var(--missing); width: var(--fg) }
                """, diag);
        Element root = new Element(ElementType.plain("#root"), POS);
        Element top = el(root, "div");
        top.setInlineStyle(CssParser.parseDeclarations("color: #0f0", POS, diag));
        Element row = el(top, "div", "row");
        Element fb = el(top, "div", "fb");
        Element bad = el(top, "div", "bad");
        Element bad2 = el(top, "div", "bad");
        Element nested = el(row, "div", "row");
        engine.restyle(root);

        assertEquals(0xFFFF0000, row.computed().get(CssProperties.COLOR));
        assertEquals(Length.px(2), row.computed().get(CssProperties.BORDER_TOP_WIDTH));
        assertEquals(0xFF0000FF, row.computed().get(CssProperties.BORDER_TOP_COLOR),
                "--edge was substituted where it was declared, with :root's --fg");
        assertEquals(0xFFFF0000, nested.computed().get(CssProperties.COLOR));
        assertFalse(row.computed().custom().containsKey("--w"), "a cycle leaves both properties undefined");
        assertEquals(Length.px(5), fb.computed().get(CssProperties.WIDTH));
        assertEquals(0xFF00FF00, fb.computed().get(CssProperties.BACKGROUND_COLOR));
        assertEquals(0xFF00FF00, bad.computed().get(CssProperties.COLOR), "an inherited property inherits");
        assertEquals(Length.AUTO, bad.computed().get(CssProperties.WIDTH), "others take their initial value");
        assertEquals(Length.AUTO, bad2.computed().get(CssProperties.WIDTH));
        assertEquals(List.of("style/undefined-var@t.css:4:8", "style/invalid-after-var@t.css:4:31"), DiagCodes.of(diag));

        bad.setBoundCustom("--missing", CssParser.parseValue("#fff", POS, diag));
        engine.restyle(root);
        assertEquals(0xFFFFFFFF, bad.computed().get(CssProperties.COLOR));
    }
}
