package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.runtime.DiagCodes;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CssParserTest {
    @Test
    void declarationOnLineThreeReportsExactPosition() {
        Diagnostics diag = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet(".a {\n  width: 10px;\n\tCOLOR : red }", "hud.css", diag).rules();
        Rule.Declaration color = rules.get(0).declarations().get(1);
        assertEquals("color", color.name());
        assertEquals(new SourcePos("hud.css", 3, 2), color.pos());
        assertEquals(1, color.value().size());
        assertFalse(diag.hasErrors());
    }

    @Test
    void importantIsStrippedFromValueEvenWithSpaces() {
        Diagnostics diag = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet("a { color: red ! IMPORTANT; --Gap: 4px }", "t.css", diag).rules();
        Rule.Declaration color = rules.get(0).declarations().get(0);
        assertTrue(color.important());
        assertEquals(1, color.value().size());
        assertTrue(((Token) color.value().get(0)).isIdent("red"));
        Rule.Declaration custom = rules.get(0).declarations().get(1);
        assertEquals("--Gap", custom.name());
        assertFalse(custom.important());
    }

    @Test
    void kindAndLeadingLinksAreCollectedAndMisplacedOnesReportedWhileLaterRulesStillParse() {
        Diagnostics diag = new Diagnostics();
        ParsedSheet sheet = CssParser.parseStylesheet("""
                @partial; @link 'base.css'; @link "@ixchel/effects/glint.ixeffect" as Glint;
                @link url(x.css); @link "x.css" as X; @partial;
                @media (x) { .a { color: red } }
                .b { width: 1px }
                @link "late.css";""", "t.css", diag);
        assertEquals(ParsedSheet.Kind.PARTIAL, sheet.kind());
        assertEquals(1, sheet.rules().size());
        assertEquals("width", sheet.rules().get(0).declarations().get(0).name());
        assertEquals(List.of("base.css null", "@ixchel/effects/glint.ixeffect Glint"),
                sheet.links().stream().map(l -> l.href() + " " + l.alias()).toList());
        assertEquals(List.of("css/invalid-link@t.css:2:1", "css/invalid-link@t.css:2:19", "css/invalid-kind@t.css:2:39",
                "css/unsupported-at-rule@t.css:3:1", "css/misplaced-link@t.css:5:1"), DiagCodes.of(diag));
    }

    private static String text(List<ComponentValue> values) {
        StringBuilder out = new StringBuilder();
        for (ComponentValue v : values) {
            if (v instanceof ComponentValue.Function f) {
                out.append(f.name()).append('(').append(text(f.args())).append(')');
            } else {
                out.append(((Token) v).value());
            }
        }
        return out.toString();
    }

    @Test
    void nestedRulesExpandAfterTheirParentWithAmpersandOrADescendantDefault() {
        Diagnostics diag = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet("""
                .a, .b { color: red; &:hover { x: 1 } > .c { x: 2 } .d { x: 3; :not(&.e) { x: 4 } } x: 5 }
                """, "t.css", diag).rules();
        StringBuilder out = new StringBuilder();
        for (Rule r : rules) {
            out.append(text(r.prelude())).append(" {").append(r.declarations().size()).append("}\n");
        }
        assertEquals("""
                .a, .b {2}
                .a:hover,.b:hover {1}
                .a > .c,.b > .c {1}
                .a .d,.b .d {1}
                :not(.a .d.e),:not(.b .d.e) {1}
                """, out.toString());
        assertFalse(diag.hasErrors());
    }

    @Test
    void nestedRuleInAStyleAttributeIsRejected() {
        Diagnostics diag = new Diagnostics();
        CssParser.parseDeclarations("color: red; .x { y: 1 }", new SourcePos("t.html", 1, 1), diag);
        assertEquals(List.of("css/nested-rule-not-allowed@t.html:1:13"), DiagCodes.of(diag));
    }

    @Test
    void selectorListPreludeIsPreservedAndTrimmed() {
        List<Rule> rules = CssParser.parseStylesheet("  .a > .b, #c  {}", "t.css", new Diagnostics()).rules();
        List<ComponentValue> p = rules.get(0).prelude();
        StringBuilder sb = new StringBuilder();
        for (ComponentValue v : p) {
            Token t = (Token) v;
            sb.append(switch (t.kind()) {
                case WHITESPACE -> " ";
                case COMMA -> ",";
                case HASH_ID -> "#" + t.value();
                default -> t.value();
            });
        }
        assertEquals(".a > .b, #c", sb.toString());
        assertEquals(new SourcePos("t.css", 1, 3), rules.get(0).pos());
    }

    @Test
    void invalidDeclarationSkipsOnlyToNextSemicolon() {
        Diagnostics diag = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet("a { color red; 5: x; width: 2px }", "t.css", diag).rules();
        List<Rule.Declaration> decls = rules.get(0).declarations();
        assertEquals(1, decls.size());
        assertEquals("width", decls.get(0).name());
        assertEquals(List.of("css/missing-colon@t.css:1:5", "css/expected-declaration@t.css:1:16"), DiagCodes.of(diag));
    }

    @Test
    void missingBlockAtEofIsAnError() {
        Diagnostics diag = new Diagnostics();
        assertTrue(CssParser.parseStylesheet(".a", "t.css", diag).rules().isEmpty());
        assertEquals(List.of("css/expected-block@t.css:1:1"), DiagCodes.of(diag));
    }

    @Test
    void unclosedBlockAtEofIsAWarningAndKeepsItsDeclarations() {
        Diagnostics diag = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet(".a { color: rgb(1, 2", "t.css", diag).rules();
        assertEquals("color", rules.get(0).declarations().get(0).name());
        assertFalse(diag.hasErrors());
        assertEquals(List.of("css/unclosed-block@t.css:1:13"), DiagCodes.of(diag));
    }

    @Test
    void styleAttributePositionsAreRemappedToTheTemplate() {
        Diagnostics diag = new Diagnostics();
        List<Rule.Declaration> decls = CssParser.parseDeclarations(
                "width: 4px;\n  height: 2px; oops", new SourcePos("hud.html", 5, 12), diag);
        assertEquals(new SourcePos("hud.html", 5, 12), decls.get(0).pos());
        assertEquals(new SourcePos("hud.html", 6, 3), decls.get(1).pos());
        assertEquals(new SourcePos("hud.html", 5, 19), decls.get(0).value().get(0).pos());
        assertEquals(List.of("css/missing-colon@hud.html:6:16"), DiagCodes.of(diag));
    }

    @Test
    void fontFaceWithoutOneNameAndOneFontFileIsAnErrorAndSkipped() {
        Diagnostics diag = new Diagnostics();
        ParsedSheet sheet = CssParser.parseStylesheet("""
                @font-face { font-family: "A"; src: url("a.png") }
                @font-face { font-family: "a:b"; src: url("b.ttf") }
                @font-face { font-family: "C"; src: url("c.otf"); font-weight: bold }
                @font-face { src: url("d.ttf") }
                @font-face { font-family: E; src: url(e.ttf) }
                """, "t.css", diag);
        assertEquals(List.of(new ParsedSheet.FontFace("E", "e.ttf", new SourcePos("t.css", 5, 1))), sheet.fontFaces());
        assertEquals(List.of("css/invalid-font-face@t.css:1:32", "css/invalid-font-face@t.css:2:14",
                "css/invalid-font-face@t.css:3:51", "css/invalid-font-face@t.css:4:1"), DiagCodes.of(diag));
    }
}
