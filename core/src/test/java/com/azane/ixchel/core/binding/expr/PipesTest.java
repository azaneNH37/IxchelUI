package com.azane.ixchel.core.binding.expr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PipesTest {
    /** The problems of compiling {@code src} with no paths in scope, as {@code code@offset}. */
    private static List<String> errors(String src) throws Expr.SyntaxError {
        List<String> out = new ArrayList<>();
        Expressions.compile(Expr.parseExpression(src), p -> null, v -> String.valueOf(v),
                (code, at, message) -> out.add(code.id() + "@" + at));
        return out;
    }

    private static Object eval(String src) throws Expr.SyntaxError {
        List<String> out = new ArrayList<>();
        Expressions.Compiled c = Expressions.compile(Expr.parseExpression(src), p -> null, v -> String.valueOf(v),
                (code, at, message) -> out.add(code.id() + "@" + at));
        assertEquals(List.of(), out);
        return c.eval().apply(new Object[0]);
    }

    @Test
    void aPipeTakesTheWholeSum() throws Exception {
        assertEquals("1.5k", eval("1000 + 500 | compact"));
    }

    @Test
    void aPipeTakesTheWholeConditional() throws Exception {
        assertEquals("100%", eval("true ? 1 : 0.5 | percent"));
    }

    @Test
    void aParenthesizedPipeConcatenates() throws Exception {
        assertEquals("12.5k FE", eval("(12500 | compact) + ' FE'"));
    }

    @Test
    void compactTakesItsDigitsAsAnArgument() throws Exception {
        assertEquals("1.23M", eval("1234567 | compact(2)"));
    }

    @Test
    void aPipeOfAPipeChecksTheStringInput() throws Exception {
        assertEquals(List.of("binding/pipe-type@16"), errors("0.5 | percent | percent"));
    }

    @Test
    void compactArgumentMustBeAnInteger() throws Exception {
        assertEquals(List.of("binding/pipe-type@4"), errors("1 | compact(0.5)"));
    }

    @Test
    void anUnknownPipeIsReportedAtItsName() throws Exception {
        assertEquals(List.of("binding/unknown-pipe@4"), errors("1 | nope"));
    }

    @Test
    void doubleBarIsStillOr() throws Exception {
        assertEquals(true, eval("false || true"));
    }

    @Test
    void compactSwitchesUnitAtAThousandAndWhenRoundingReachesOne() throws Exception {
        assertEquals(List.of("999", "1k", "1M", "12.3", "-12.5k"), List.of(eval("999 | compact"), eval("1000 | compact"),
                eval("999950 | compact"), eval("12.34 | compact"), eval("-12500 | compact")));
    }

    @Test
    void durationIsMinutesSecondsBelowAnHour() throws Exception {
        assertEquals(List.of("1:02", "1:00:00", "0:00"),
                List.of(eval("1250 | duration"), eval("72000 | duration"), eval("0 | duration")));
    }

    @Test
    void percentRoundsToAWholePercent() throws Exception {
        assertEquals(List.of("0%", "100%", "46%"),
                List.of(eval("0 | percent"), eval("1 | percent"), eval("0.456 | percent")));
    }

    /** Fails if an example the reference shows for a pipe stops compiling with no paths in scope. */
    @Test
    void everyDocumentedExampleCompiles() throws Exception {
        for (Expressions.Pipe p : Expressions.PIPES) {
            for (String e : p.examples()) {
                assertEquals(List.of(), errors(e), e);
            }
        }
    }
}
