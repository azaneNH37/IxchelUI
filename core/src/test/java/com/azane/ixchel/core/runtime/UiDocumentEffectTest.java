package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.binding.compile.Bindings;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.html.HtmlElements;
import com.azane.ixchel.core.view.Translations;
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

    /** {@code t.css} links both effects on its first line, as Shine and Dissolve; {@code css} starts on line 2. */
    private static Map<String, String> files(String css) {
        Map<String, String> files = new HashMap<>();
        files.put("t.css", "@stylesheet; @link \"fx/shine.ixeffect\"; @link \"@ixchel/effects/dissolve.ixeffect\";\n" + css);
        files.put("fx/shine.ixeffect", SHINE);
        return files;
    }

    private static final String LINKS = "<link href=\"t.css\"/>\n";

    static final class Burn extends ViewModel {
        final Value<Float> progress = state(0.25f);
    }

    @Test
    void aBoxEffectFillsTheBorderBoxAndALayerEffectRidesTheElementsLayer() {
        Burn m = new Burn();
        UiDocument doc = UiDocument.load(GoldenTest.page(LINKS + """
                <div class="a"></div>
                <div class="b" bind:style:--Dissolve-progress="progress"><div class="c"></div></div>
                """), "t.html", files("""
                .a { width: 20px; height: 10px; border-radius: 2px; background-image: paint(Shine), url("m:t.png");
                     background-size: 4px 4px; --Shine-speed: 2 }
                .b { width: 10px; height: 10px; filter: -ix-paint(Dissolve) opacity(0.5); transform: translateX(4px);
                     --Dissolve-edge-color: #ff000000 }
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
                layer 3 9 4 4 1 paint(@ixchel/effects/dissolve.ixeffect) opacity(0.5) paint dissolve [0.25 0 0 0 0 3] frame 0 10 10 10
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
        UiDocument doc = UiDocument.load(GoldenTest.page(LINKS + "<div bind:class:on=\"on\"></div>"), "t.html", files("""
                div { width: 4px; height: 4px; background-image: paint(Shine); transition: --Shine-speed 1s linear, --Shine-tint 1s linear }
                .on { --Shine-speed: 3; --Shine-tint: #ffffffff }
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

    /** Fails if two effect files of the same name share their params or their internal name. */
    @Test
    void effectsOfTheSameFileNameAreTwoEffectsUnderTheirAliases() {
        Map<String, String> files = files("""
                @link "other/shine.ixeffect" as Other;
                .a { width: 4px; height: 4px; background-image: paint(Shine); --Shine-speed: 2 }
                .b { width: 4px; height: 4px; background-image: paint(Other); --Other-speed: 3 }
                """);
        files.put("other/shine.ixeffect", SHINE.replace("tint * speed", "tint * speed * 2.0"));
        UiDocument doc = UiDocument.load(GoldenTest.page(LINKS + "<div class=\"a\"></div><div class=\"b\"></div>"), "t.html",
                files::get, HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("""
                paint shine 0 0 4 4 [2 0.5019608 0.5019608 0.5019608 0.5019608]
                paint shine 0 4 4 4 [3 0.5019608 0.5019608 0.5019608 0.5019608]""", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(List.of("fx/shine.ixeffect", "@ixchel/effects/dissolve.ixeffect", "other/shine.ixeffect"),
                doc.effects().all().stream().map(e -> e.id()).toList());
    }

    @Test
    void misusedEffectsAreReportedAtTheirUse() {
        Map<String, String> files = files("""
                @link "other/shine.ixeffect"; @link "@ixchel/effects/sparkle.ixeffect" as sparkle;
                .a { background-image: paint(shine) }
                .b { background-image: paint(Dissolve) }
                .c { filter: -ix-paint(Shine) }
                .d { --Shine-sped: 2; --other: 1; --shine-tint: red; --Glow-size: 1; --Shine: 1 }
                .e { backdrop-filter: -ix-paint(Dissolve) }
                @link "fx/late.ixeffect";
                """);
        files.put("other/shine.ixeffect", SHINE);
        List<String> codes = DiagCodes.of(UiDocument.lint(GoldenTest.page(LINKS + "<div style=\"--Glow-size: 1\"></div>"), "t.html",
                files::get, HtmlElements.TAGS, Translations.NONE, null, Set.of()));
        assertEquals(List.of(
                "css/misplaced-link@t.css:8:1",
                "effect/duplicate-alias@t.css:2:1",
                "link/file-not-found@t.css:2:31",
                "effect/invalid-alias@t.css:2:31",
                "effect/unknown-alias@t.css:3:30",
                "effect/unknown-alias@t.css:6:54",
                "effect/unknown-alias@t.html:2:13",
                "effect/wrong-kind@t.css:4:24",
                "effect/wrong-kind@t.css:5:14",
                "effect/unknown-param@t.css:6:6",
                "effect/unmarked-param@t.css:6:35",
                "effect/unknown-param@t.css:6:70",
                "css/unsupported-value@t.css:7:23"), codes);
    }

    @Test
    void anEffectNamedThroughVarIsReportedWhenPaintedAndLeftOut() {
        UiDocument doc = UiDocument.load(GoldenTest.page(LINKS + "<div></div>"), "t.html", files("""
                div { --fx: nope; width: 4px; height: 4px; background-color: #fff; background-image: paint(var(--fx)) }
                """)::get, HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES);
        assertEquals("rect 0 0 4 4 #FFFFFFFF", GoldenTest.print(doc.frame(100, 100)));
        assertEquals(List.of("effect/unknown-alias@t.html:2:1"), DiagCodes.of(doc.diagnostics()));
    }

    @Test
    void aBrokenEffectFileFailsTheLoad() {
        Map<String, String> files = files("");
        files.put("fx/shine.ixeffect", "kind backdrop;\nvec4 effect() {}");
        UiLoadException e = assertThrows(UiLoadException.class, () -> UiDocument.load(GoldenTest.page(LINKS + "<div></div>"), "t.html", files::get,
                HtmlElements.TAGS, GoldenTest.MONO, GoldenTest.NO_IMAGES));
        assertEquals(List.of("effect/unsupported-kind@fx/shine.ixeffect:1:1"), DiagCodes.of(e));
    }
}
