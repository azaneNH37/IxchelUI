package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.template.Translations;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiDocumentEffectTest {
    private static final String SHINE = """
            kind box;
            param speed { syntax: <number>; inherits: false; initial-value: 1; }
            param tint  { syntax: <color>;  inherits: true;  initial-value: #ffffff80; }
            vec4 effect(IxBox b) { return tint * speed; }
            """;

    private static Map<String, String> files(String css) {
        Map<String, String> files = new HashMap<>();
        files.put("t.css", css);
        files.put("fx/shine.ixeffect", SHINE);
        return files;
    }

    private static final String LINKS = """
            <link rel="stylesheet" href="t.css"/>
            <link rel="effect" href="fx/shine"/>
            <link rel="effect" href="@ixchel/effects/dissolve"/>
            """;

    static final class Burn extends ViewModel {
        final Value<Float> progress = state(0.25f);
    }

    @Test
    void aBoxEffectFillsTheBorderBoxAndALayerEffectRidesTheElementsLayer() {
        Burn m = new Burn();
        UiDocument doc = UiDocument.load(LINKS + """
                <div class="a"></div>
                <div class="b" bind:style:--dissolve-progress="progress"><div class="c"></div></div>
                """, "t.html", files("""
                .a { width: 20px; height: 10px; border-radius: 2px; background-image: paint(shine), url("m:t.png");
                     background-size: 4px 4px; --shine-speed: 2 }
                .b { width: 10px; height: 10px; filter: -ix-paint(dissolve) opacity(0.5); transform: translateX(4px);
                     --dissolve-edge-color: #ff000000 }
                .c { width: 2px; height: 2px; background-color: #fff }
                """)::get, HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        assertEquals("""
                image 0 0 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 4 0 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 8 0 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 12 0 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 16 0 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 0 4 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 4 4 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 8 4 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 12 4 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 16 4 4 4 m:t.png uv 0 0 1 1 #FFFFFFFF
                image 0 8 4 2 m:t.png uv 0 0 1 0.5 #FFFFFFFF
                image 4 8 4 2 m:t.png uv 0 0 1 0.5 #FFFFFFFF
                image 8 8 4 2 m:t.png uv 0 0 1 0.5 #FFFFFFFF
                image 12 8 4 2 m:t.png uv 0 0 1 0.5 #FFFFFFFF
                image 16 8 4 2 m:t.png uv 0 0 1 0.5 #FFFFFFFF
                paint shine 0 0 20 10 radii 2/2 2/2 2/2 2/2 [2 0.5019608 0.5019608 0.5019608 0.5019608]
                layer 3 9 4 4 1 paint(dissolve) opacity(0.5) paint dissolve [0.25 0 0 0 0 3] frame 0 10 10 10
                transform 1 0 0 1 4 0
                rect 0 10 2 2 #FFFFFFFF
                untransform
                unlayer""", GoldenTest.print(doc.frame(100, 100)));
    }

    static final class Speed extends ViewModel {
        final Value<Boolean> on = state(false);
    }

    @Test
    void aParamTransitionsAsItsDeclaredType() {
        Speed m = new Speed();
        UiDocument doc = UiDocument.load(LINKS + "<div bind:class:on=\"on\"></div>", "t.html", files("""
                div { width: 4px; height: 4px; background-image: paint(shine); transition: --shine-speed 1s linear, --shine-tint 1s linear }
                .on { --shine-speed: 3; --shine-tint: #ffffffff }
                """)::get, HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES, m, Bindings.PLAIN_TEXT);
        long[] now = {0};
        doc.clock(() -> now[0]);
        doc.frame(100, 100);
        m.on.set(true);
        doc.frame(100, 100);
        now[0] = 500_000_000L;
        assertEquals("paint shine 0 0 4 4 [2 0.7529412 0.7529412 0.7529412 0.7529412]",
                GoldenTest.print(doc.frame(100, 100)).lines().findFirst().orElseThrow(),
                "halfway from 1 to 3, and from alpha 0x80 to 0xFF in 8-bit color");
    }

    @Test
    void misusedEffectsAreReportedAtTheirUse() {
        Map<String, String> files = files("""
                .a { background-image: paint(nope) }
                .b { background-image: paint(dissolve) }
                .c { filter: -ix-paint(shine) }
                .d { --shine-sped: 2; --other: 1 }
                .e { backdrop-filter: -ix-paint(dissolve) }
                """);
        files.put("other/shine.ixeffect", SHINE);
        List<String> codes = DiagCodes.of(UiDocument.lint(LINKS + """
                <link rel="effect" href="other/shine"/>
                <link rel="effect" href="@ixchel/effects/sparkle"/>
                <div></div>
                <link rel="effect" href="fx/late"/>
                """, "t.html", files::get, HtmlElements.TAGS, Translations.NONE, null, Set.of()));
        assertEquals(List.of(
                "template/link-after-element@t.html:7:1",
                "load/file-not-found@t.html:5:1",
                "effect/duplicate-effect@t.html:4:1",
                "effect/unknown-effect@t.css:1:24",
                "effect/wrong-kind@t.css:2:24",
                "effect/wrong-kind@t.css:3:14",
                "effect/unknown-param@t.css:4:6",
                "css/unsupported-value@t.css:5:23"), codes);
    }

    @Test
    void anEffectNamedThroughVarIsReportedWhenPaintedAndLeftOut() {
        UiDocument doc = UiDocument.load(LINKS + "<div></div>", "t.html", files("""
                div { --fx: nope; width: 4px; height: 4px; background-color: #fff; background-image: paint(var(--fx)) }
                """)::get, HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("rect 0 0 4 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(List.of("effect/unknown-effect@t.html:4:1"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void aBrokenEffectFileFailsTheLoad() {
        Map<String, String> files = files("");
        files.put("fx/shine.ixeffect", "kind backdrop;\nvec4 effect() {}");
        UiLoadException e = assertThrows(UiLoadException.class, () -> UiDocument.load(LINKS + "<div></div>", "t.html", files::get,
                HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertEquals(List.of("effect/unsupported-kind@fx/shine.ixeffect:1:1"), DiagCodes.of(e));
    }
}
