package com.azane.ixchel.core.runtime;

import com.azane.ixchel.core.css.property.Filter;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.paint.ImageMeasurer;
import com.azane.ixchel.core.view.TagRegistry;
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
        public boolean hasFont(String font) {
            return true;
        }

        @Override
        public float width(String text, float size, boolean bold, String font) {
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

    /** The {@code <link>} of {@link #sheet} and {@code <page>} as line 1, so view positions in {@code html} start at line 2. */
    static String linked(String html) {
        return "<link href=\"t.css\"/><page>\n" + html + "</page>";
    }

    /** {@code html} as a page: its leading {@code <link>}s above the root, the rest as the page's content on the same line. */
    static String page(String html) {
        int end = 0;
        java.util.regex.Matcher m = LEADING_LINK.matcher(html);
        while (m.find(end) && m.start() == end) {
            end = m.end();
        }
        return html.substring(0, end) + "<page>" + html.substring(end) + "</page>";
    }

    private static final java.util.regex.Pattern LEADING_LINK = java.util.regex.Pattern.compile("\\s*<link [^>]*(/>|></link>)");

    /** Serves {@code css} as {@code t.css}, the only linked file; its first line starts after {@code @stylesheet;}. */
    static Function<String, String> sheet(String css) {
        return Map.of("t.css", "@stylesheet; " + css)::get;
    }

    /** For views without {@code <link>}: reading any file fails. */
    static final Function<String, String> NO_SHEETS = href -> {
        throw new IllegalArgumentException("no stylesheet " + href);
    };

    @ParameterizedTest
    @ValueSource(strings = "ammo-hud")
    void displayListMatchesExpectation(String name) throws IOException {
        String dir = "golden/" + name + "/";
        UiDocument doc = UiDocument.load(resource(dir + "hud.html"), "hud.html",
                Map.of("hud.css", resource(dir + "hud.css"))::get, com.azane.ixchel.core.html.HtmlElements.TAGS, MONO, NO_IMAGES);
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
            public String visitFill(DrawCommand.Fill c) {
                String paint;
                if (c.gradient() == null) {
                    paint = argb(c.color());
                } else {
                    var g = c.gradient();
                    StringBuilder s = new StringBuilder("gradient " + n(g.x0()) + " " + n(g.y0()) + " " + n(g.x1()) + " " + n(g.y1()));
                    for (int i = 0; i < g.offsets().length; i++) {
                        s.append(' ').append(n(g.offsets()[i])).append(':').append(argb(g.colors()[i]));
                    }
                    paint = s.toString();
                }
                return "fill " + shape(c.shape()) + " clip " + n(c.clipX()) + " " + n(c.clipY()) + " " + n(c.clipWidth()) + " "
                        + n(c.clipHeight()) + " " + paint + crisp(c.crisp());
            }

            @Override
            public String visitBorder(DrawCommand.Border c) {
                return "border " + shape(c.outer()) + " widths " + n(c.top()) + " " + n(c.right()) + " " + n(c.bottom()) + " "
                        + n(c.left()) + " " + argb(c.topColor()) + " " + argb(c.rightColor()) + " " + argb(c.bottomColor()) + " "
                        + argb(c.leftColor()) + crisp(c.crisp());
            }

            @Override
            public String visitShadow(DrawCommand.Shadow c) {
                return (c.inset() ? "inset-shadow " : "shadow ") + shape(c.shape()) + " sigma " + n(c.sigma()) + " "
                        + argb(c.color()) + " clip " + shape(c.clip()) + crisp(c.crisp());
            }

            @Override
            public String visitText(DrawCommand.Text c) {
                return "text " + n(c.x()) + " " + n(c.y()) + " " + n(c.size()) + " " + argb(c.color()) + " \"" + c.text() + "\""
                        + (c.flags() != 0 ? " flags " + c.flags() : "") + (c.font() != null ? " font " + c.font() : "");
            }

            @Override
            public String visitImage(DrawCommand.Image c) {
                return "image " + n(c.x()) + " " + n(c.y()) + " " + n(c.width()) + " " + n(c.height()) + " " + c.src()
                        + " uv " + n(c.u0()) + " " + n(c.v0()) + " " + n(c.u1()) + " " + n(c.v1()) + " " + argb(c.tint())
                        + (c.filter() != DrawCommand.Image.Filter.AUTO ? " " + c.filter().name().toLowerCase(Locale.ROOT) : "");
            }

            @Override
            public String visitCustom(DrawCommand.Custom c) {
                return c.kind() + " " + n(c.x()) + " " + n(c.y()) + " " + n(c.width()) + " " + n(c.height()) + " " + c.payload();
            }

            @Override
            public String visitPaint(DrawCommand.Paint c) {
                return "paint " + c.effect().name() + " " + shape(c.shape()) + values(c.values());
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

            @Override
            public String visitPushLayer(DrawCommand.PushLayer c) {
                StringBuilder paints = new StringBuilder();
                for (DrawCommand.LayerPaint p : c.paints()) {
                    paints.append(" paint ").append(p.effect().name()).append(values(p.values()));
                }
                if (c.frame() != null) {
                    paints.append(" frame ").append(shape(c.frame()));
                }
                return "layer " + n(c.x()) + " " + n(c.y()) + " " + n(c.width()) + " " + n(c.height()) + " " + n(c.opacity())
                        + filters(c.filters()) + (c.blend() == Keywords.MixBlendMode.NORMAL ? "" : " " + c.blend()) + paints;
            }

            @Override
            public String visitPopLayer(DrawCommand.PopLayer c) {
                return "unlayer";
            }

            @Override
            public String visitBackdrop(DrawCommand.Backdrop c) {
                return "backdrop " + shape(c.shape()) + filters(c.filters()) + (c.belowGroup() ? " below" : "");
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

    private static String shape(com.azane.ixchel.core.paint.RoundRect r) {
        var q = r.radii();
        String s = n(r.x()) + " " + n(r.y()) + " " + n(r.width()) + " " + n(r.height());
        if (q.isZero()) {
            return s;
        }
        String radii = s + " radii " + n(q.topLeftX()) + "/" + n(q.topLeftY()) + " " + n(q.topRightX()) + "/" + n(q.topRightY()) + " "
                + n(q.bottomRightX()) + "/" + n(q.bottomRightY()) + " " + n(q.bottomLeftX()) + "/" + n(q.bottomLeftY());
        var c = q.corners();
        if (c.equals(com.azane.ixchel.core.paint.RoundRect.Corners.ROUND)) {
            return radii;
        }
        return radii + " corners " + lower(c.topLeft()) + " " + lower(c.topRight()) + " " + lower(c.bottomRight()) + " "
                + lower(c.bottomLeft());
    }

    private static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }

    private static String crisp(boolean crisp) {
        return crisp ? " crisp" : "";
    }

    private static String filters(java.util.List<Filter> filters) {
        StringBuilder sb = new StringBuilder();
        for (Filter f : filters) {
            sb.append(' ').append(f.kind().name().toLowerCase(Locale.ROOT)).append('(');
            switch (f.kind()) {
                case BLUR -> sb.append(n(f.sigma()));
                case DROP_SHADOW -> sb.append(n(f.x())).append(' ').append(n(f.y())).append(' ').append(n(f.sigma()))
                        .append(' ').append(argb(f.color()));
                case PAINT -> sb.append(f.effect());
                default -> sb.append(n(f.amount()));
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static String values(float[] values) {
        StringBuilder sb = new StringBuilder(" [");
        for (int i = 0; i < values.length; i++) {
            sb.append(i == 0 ? "" : " ").append(n(values[i]));
        }
        return sb.append(']').toString();
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
