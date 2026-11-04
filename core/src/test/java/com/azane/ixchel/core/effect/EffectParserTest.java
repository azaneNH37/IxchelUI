package com.azane.ixchel.core.effect;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.runtime.DiagCodes;
import com.azane.ixchel.core.runtime.UiHosting;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectParserTest {
    @Test
    void theHeaderBecomesTypedParamsAndTheBodyKeepsItsLine() {
        Diagnostics diag = new Diagnostics();
        Effect e = EffectParser.parse("""
                // burns away
                kind layer;
                param edge-color { syntax: <color>; inherits: true; initial-value: #ff000080; }
                /* quoted, as @property writes it */ param size { syntax: '<length>'; inherits: false; initial-value: 3px; }

                vec4 effect(IxLayer l) { return vec4(size); }
                """, "mod:ui/fx/burn.ixeffect", diag);
        assertEquals(List.of(), DiagCodes.of(diag));
        assertEquals("burn", e.name());
        assertEquals(Effect.Kind.LAYER, e.kind());
        Effect.Param color = e.params().get(0);
        Effect.Param size = e.params().get(1);
        assertEquals(ParamType.COLOR, color.type());
        assertTrue(color.inherits());
        assertEquals(0x80FF0000, color.initial());
        assertEquals("edge_color", color.uniform());
        assertEquals(ParamType.LENGTH, size.type());
        assertEquals(3f, size.initial());
        assertEquals(5, e.valueCount(), "a color takes four floats");
        assertEquals(6, e.bodyLine());
        assertTrue(e.body().startsWith("vec4 effect(IxLayer l)"), e.body());
    }

    @Test
    void anUnusableHeaderIsReportedAtItsStatement() {
        assertEquals(List.of("effect/missing-kind@f.ixeffect:1:1"), codes("vec4 effect(IxBox b) { return vec4(0); }"));
        assertEquals(List.of("effect/unsupported-kind@f.ixeffect:1:1"), codes("kind backdrop;\nvec4 effect() {}"));
        assertEquals(List.of("effect/missing-descriptor@f.ixeffect:2:7"),
                codes("kind box;\nparam a { syntax: <number>; initial-value: 1; }\nvec4 effect() {}"));
        assertEquals(List.of("effect/unsupported-syntax@f.ixeffect:2:11"),
                codes("kind box;\nparam a { syntax: <integer>; inherits: false; initial-value: 1; }\nvec4 effect() {}"));
        assertEquals(List.of("effect/duplicate-param@f.ixeffect:3:7"), codes("""
                kind box;
                param a { syntax: <number>; inherits: false; initial-value: 1; }
                param a { syntax: <number>; inherits: false; initial-value: 2; }
                vec4 effect() {}"""));
        assertEquals(List.of("effect/invalid-header@f.ixeffect:1:1"), codes("kind box\nvec4 effect() {}"));
        assertEquals(List.of("effect/invalid-name@ui/Glow.ixeffect:1:1"), DiagCodes.of(parse("kind box;", "ui/Glow.ixeffect")));
    }

    @Test
    void aLengthInitialValueIsInPixels() {
        List<String> codes = codes("kind box;\nparam w { syntax: <length>; inherits: false; initial-value: 1em; }\nvec4 effect() {}");
        assertEquals(1, codes.size(), codes.toString());
        assertTrue(codes.get(0).startsWith("css/"), codes.toString());
    }

    @Test
    void theLibraryEffectsParse() {
        assertEquals(Effect.Kind.BOX, parseOk("@ixchel/effects/glint.ixeffect").kind());
        assertEquals(Effect.Kind.LAYER, parseOk("@ixchel/effects/dissolve.ixeffect").kind());
    }

    private static Effect parseOk(String id) {
        Diagnostics diag = new Diagnostics();
        Effect e = EffectParser.parse(UiHosting.NONE.library().read(id), id, diag);
        assertEquals(List.of(), DiagCodes.of(diag));
        return e;
    }

    private static List<String> codes(String text) {
        return DiagCodes.of(parse(text, "f.ixeffect"));
    }

    private static Diagnostics parse(String text, String id) {
        Diagnostics diag = new Diagnostics();
        Effect e = EffectParser.parse(text, id, diag);
        if (e != null) {
            assertTrue(diag.entries().isEmpty());
        }
        return diag;
    }
}
