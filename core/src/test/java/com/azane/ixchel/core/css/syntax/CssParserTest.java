package com.azane.ixchel.core.css.syntax;

import com.azane.ixchel.core.diag.Diagnostic;
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
        List<Rule> rules = CssParser.parseStylesheet(".a {\n  width: 10px;\n\tCOLOR : red }", "hud.css", diag);
        Rule.Declaration color = rules.get(0).declarations().get(1);
        assertEquals("color", color.name());
        assertEquals(new SourcePos("hud.css", 3, 2), color.pos());
        assertEquals(1, color.value().size());
        assertFalse(diag.hasErrors());
    }

    @Test
    void importantIsStrippedFromValueEvenWithSpaces() {
        Diagnostics diag = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet("a { color: red ! IMPORTANT; --Gap: 4px }", "t.css", diag);
        Rule.Declaration color = rules.get(0).declarations().get(0);
        assertTrue(color.important());
        assertEquals(1, color.value().size());
        assertTrue(((Token) color.value().get(0)).isIdent("red"));
        Rule.Declaration custom = rules.get(0).declarations().get(1);
        assertEquals("--Gap", custom.name());
        assertFalse(custom.important());
    }

    @Test
    void atRuleIsReportedAndLaterRulesStillParse() {
        Diagnostics diag = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet(
                "@media (x) { .a { color: red } }\n@import 'x.css';\n.b { width: 1px }", "t.css", diag);
        assertEquals(1, rules.size());
        assertEquals("width", rules.get(0).declarations().get(0).name());
        assertEquals(2, diag.entries().size());
        assertEquals("unsupported at-rule @media", diag.entries().get(0).message());
        assertEquals(new SourcePos("t.css", 2, 1), diag.entries().get(1).pos());
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
                """, "t.css", diag);
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
        CssParser.parseDeclarations("color: red; .x { y: 1 }", new SourcePos("t.html", 1, 1), diag);
        assertEquals("nested rules are only allowed in a stylesheet", diag.entries().get(0).message());
    }

    @Test
    void selectorListPreludeIsPreservedAndTrimmed() {
        List<Rule> rules = CssParser.parseStylesheet("  .a > .b, #c  {}", "t.css", new Diagnostics());
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
        List<Rule> rules = CssParser.parseStylesheet("a { color red; 5: x; width: 2px }", "t.css", diag);
        List<Rule.Declaration> decls = rules.get(0).declarations();
        assertEquals(1, decls.size());
        assertEquals("width", decls.get(0).name());
        List<Diagnostic> e = diag.entries();
        assertEquals(new SourcePos("t.css", 1, 5), e.get(0).pos());
        assertEquals(new SourcePos("t.css", 1, 16), e.get(1).pos());
    }

    @Test
    void missingBlockAtEofIsAnErrorAndUnclosedBlockAWarning() {
        Diagnostics diag = new Diagnostics();
        assertTrue(CssParser.parseStylesheet(".a", "t.css", diag).isEmpty());
        assertTrue(diag.hasErrors());

        Diagnostics diag2 = new Diagnostics();
        List<Rule> rules = CssParser.parseStylesheet(".a { color: rgb(1, 2", "t.css", diag2);
        assertEquals("color", rules.get(0).declarations().get(0).name());
        assertFalse(diag2.hasErrors());
        assertEquals(1, diag2.entries().size());
        assertEquals("unclosed block", diag2.entries().get(0).message());
    }

    @Test
    void styleAttributePositionsAreRemappedToTheTemplate() {
        Diagnostics diag = new Diagnostics();
        List<Rule.Declaration> decls = CssParser.parseDeclarations(
                "width: 4px;\n  height: 2px; oops", new SourcePos("hud.html", 5, 12), diag);
        assertEquals(new SourcePos("hud.html", 5, 12), decls.get(0).pos());
        assertEquals(new SourcePos("hud.html", 6, 3), decls.get(1).pos());
        assertEquals(new SourcePos("hud.html", 5, 19), decls.get(0).value().get(0).pos());
        assertEquals(new SourcePos("hud.html", 6, 16), diag.entries().get(0).pos());
    }
}
