package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.text.TextMeasurer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Each fixture under {@code golden/<name>/} holds {@code hud.html}, {@code hud.css} and the expected
 * display list; {@code //} lines in the expectation are comments.
 */
class GoldenTest {
    /** Monospace stand-in for the vanilla font: 6px advance (7px bold), 9px line, baseline 7 at size 9. */
    static final TextMeasurer MONO = new TextMeasurer() {
        @Override
        public float width(String text, float size, boolean bold) {
            return text.length() * (bold ? 7 : 6) * size / 9;
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

    /** For documents without {@code background-size: auto}: every image is unknown. */
    static final ImageMeasurer NO_IMAGES = src -> null;

    /** The {@code <link>} line for {@link #sheet} as line 1, so template positions in {@code html} start at line 2. */
    static String linked(String html) {
        return "<link rel=\"stylesheet\" href=\"t.css\"/>\n" + html;
    }

    /** Serves {@code css} as {@code t.css}, the only linked file. */
    static Function<String, String> sheet(String css) {
        return Map.of("t.css", css)::get;
    }

    /** For templates without {@code <link>}: reading any file fails. */
    static final Function<String, String> NO_SHEETS = href -> {
        throw new IllegalArgumentException("no stylesheet " + href);
    };

    @ParameterizedTest
    @ValueSource(strings = "ammo-hud")
    void displayListMatchesExpectation(String name) throws IOException {
        String dir = "golden/" + name + "/";
        UiDocument doc = UiDocument.load(resource(dir + "hud.html"), "hud.html",
                Map.of("hud.css", resource(dir + "hud.css"))::get,
                TagRegistry.HTML, MONO, NO_IMAGES);
        DisplayList out = doc.frame(480, 270);

        String expected = resource(dir + "expected.txt").lines()
                .filter(l -> !l.startsWith("//") && !l.isBlank())
                .collect(Collectors.joining("\n"));
        assertEquals(expected, print(out));
    }

    static String print(DisplayList list) {
        StringBuilder sb = new StringBuilder();
        DrawCommand.Visitor<String> v = new DrawCommand.Visitor<>() {
            @Override
            public String visitRect(DrawCommand.Rect c) {
                return "rect " + n(c.x()) + " " + n(c.y()) + " " + n(c.width()) + " " + n(c.height()) + " " + argb(c.color());
            }

            @Override
            public String visitText(DrawCommand.Text c) {
                return "text " + n(c.x()) + " " + n(c.y()) + " " + n(c.size()) + " " + argb(c.color()) + " \"" + c.text() + "\""
                        + (c.flags() != 0 ? " flags " + c.flags() : "");
            }

            @Override
            public String visitImage(DrawCommand.Image c) {
                return "image " + n(c.x()) + " " + n(c.y()) + " " + n(c.width()) + " " + n(c.height()) + " " + c.src()
                        + " uv " + n(c.u0()) + " " + n(c.v0()) + " " + n(c.u1()) + " " + n(c.v1()) + " " + argb(c.tint());
            }

            @Override
            public String visitCustom(DrawCommand.Custom c) {
                return c.kind() + " " + n(c.x()) + " " + n(c.y()) + " " + n(c.width()) + " " + n(c.height()) + " " + c.payload();
            }

            @Override
            public String visitPushClip(DrawCommand.PushClip c) {
                return "clip " + n(c.x()) + " " + n(c.y()) + " " + n(c.width()) + " " + n(c.height());
            }

            @Override
            public String visitPopClip(DrawCommand.PopClip c) {
                return "unclip";
            }

            @Override
            public String visitPushTransform(DrawCommand.PushTransform c) {
                var m = c.matrix();
                return "transform " + n(m.a()) + " " + n(m.b()) + " " + n(m.c()) + " " + n(m.d()) + " " + n(m.e()) + " " + n(m.f());
            }

            @Override
            public String visitPopTransform(DrawCommand.PopTransform c) {
                return "untransform";
            }
        };
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(list.get(i).accept(v));
        }
        return sb.toString();
    }

    private static String n(float f) {
        return f == Math.rint(f) ? Integer.toString((int) f) : Float.toString(f);
    }

    private static String argb(int c) {
        return String.format(Locale.ROOT, "#%08X", c);
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = GoldenTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
