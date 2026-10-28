package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Value grammars shared by the standard properties. */
public final class Syntaxes {
    private Syntaxes() {
    }

    /** {@code value} without whitespace tokens. */
    public static List<ComponentValue> significant(List<ComponentValue> value) {
        List<ComponentValue> out = new ArrayList<>(value.size());
        for (ComponentValue v : value) {
            if (!(v instanceof Token t && t.is(Token.Kind.WHITESPACE))) {
                out.add(v);
            }
        }
        return out;
    }

    public static <E extends Enum<E>> Syntax<E> keyword(Class<E> type) {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
                String name = t.value().toUpperCase(Locale.ROOT).replace('-', '_');
                for (E e : type.getEnumConstants()) {
                    if (e.name().equals(name)) {
                        return e;
                    }
                }
            }
            diag.error(v.pos(), "expected one of " + keywordList(type));
            return null;
        };
    }

    /**
     * A length in {@code px} (unitless {@code 0} allowed), optionally also a percentage and {@code auto}.
     * Interpolates between two lengths of the same unit; {@code auto} and mixed units do not. A
     * number from code is px.
     */
    public static Syntax<Length> length(boolean percent, boolean auto, boolean negative) {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : lengthOf(v, percent, auto, negative, diag);
        }, (a, b, p) -> {
            if (a.unit() != b.unit() || a.unit() == Length.Unit.AUTO) {
                return null;
            }
            float v = mix(a.value(), b.value(), p);
            return new Length(negative ? v : Math.max(0, v), a.unit());
        }, "px");
    }

    public static Syntax<Float> number(boolean negative) {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.is(Token.Kind.NUMBER)) {
                if (!negative && t.number() < 0) {
                    diag.error(t.pos(), "negative value not allowed");
                    return null;
                }
                return (float) t.number();
            }
            diag.error(v.pos(), "expected a number");
            return null;
        }, (a, b, p) -> {
            float v = mix(a, b, p);
            return negative ? v : Math.max(0, v);
        }, "");
    }

    /**
     * ARGB. {@code #rgb}, {@code #rgba}, {@code #rrggbb}, {@code #rrggbbaa}, {@code rgb()}/{@code rgba()}, a few names.
     * Interpolates in premultiplied sRGB, as CSS does for these color forms.
     */
    public static Syntax<Integer> color() {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : colorOf(v, diag);
        }, Syntaxes::mixColor, null);
    }

    /** A {@code <time>} in seconds, written with {@code s} or {@code ms}. */
    public static Syntax<Float> time(boolean negative) {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : timeOf(v, negative, diag);
        };
    }

    /** {@code linear}, {@code ease}, {@code ease-in}, {@code ease-out}, {@code ease-in-out} or {@code cubic-bezier()}. */
    public static Syntax<Easing> easing() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : easingOf(v, diag);
        };
    }

    /** One or more {@code item} values separated by commas; the result is unmodifiable. */
    public static <T> Syntax<List<T>> commaList(Syntax<T> item) {
        return (value, pos, diag) -> {
            List<List<ComponentValue>> items = splitCommas(value, pos, diag);
            if (items == null) {
                return null;
            }
            List<T> out = new ArrayList<>(items.size());
            for (List<ComponentValue> i : items) {
                T t = item.parse(i, i.get(0).pos(), diag);
                if (t == null) {
                    return null;
                }
                out.add(t);
            }
            return List.copyOf(out);
        };
    }

    /**
     * {@code transition-property}: {@code none} (an empty list), or comma-separated {@code all},
     * animatable longhands and shorthands whose longhands are all animatable, kept as written.
     */
    public static Syntax<List<String>> transitionProperty(PropertyRegistry registry) {
        return (value, pos, diag) -> {
            List<List<ComponentValue>> items = splitCommas(value, pos, diag);
            if (items == null) {
                return null;
            }
            List<String> out = new ArrayList<>(items.size());
            for (List<ComponentValue> i : items) {
                ComponentValue v = single(i, i.get(0).pos(), diag);
                String name = v == null ? null : transitionNameOf(v, registry, diag);
                if (name == null) {
                    return null;
                }
                if (name.equals("none")) {
                    if (items.size() > 1) {
                        diag.error(v.pos(), "'none' must be the only value");
                        return null;
                    }
                    return List.of();
                }
                out.add(name);
            }
            return List.copyOf(out);
        };
    }

    /**
     * {@code background-image}: {@code none} (an empty string) or one {@code url()}, quoted or not,
     * naming an image by resource location, e.g. {@code url("minecraft:textures/gui/icons.png")}, or
     * a sprite of the host's texture atlas by {@link #SPRITE} and the sprite's resource location,
     * e.g. {@code url("sprite:minecraft:block/water_still")}.
     */
    public static Syntax<String> backgroundImage() {
        return (value, pos, diag) -> {
            for (ComponentValue v : value) {
                if (v instanceof Token t && t.is(Token.Kind.COMMA)) {
                    diag.error(t.pos(), "multiple background layers are not supported");
                    return null;
                }
            }
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t) {
                if (t.isIdent("none")) {
                    return "";
                }
                if (t.is(Token.Kind.URL)) {
                    return resourceLocation(t.value(), t.pos(), diag);
                }
                if (t.is(Token.Kind.BAD_URL)) {
                    return null; // the tokenizer reported it
                }
            } else if (v instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("url")) {
                List<ComponentValue> args = significant(f.args());
                if (args.size() == 1 && args.get(0) instanceof Token s && s.is(Token.Kind.STRING)) {
                    return resourceLocation(s.value(), s.pos(), diag);
                }
                diag.error(f.pos(), "url() takes one string");
                return null;
            }
            diag.error(v.pos(), "expected none or url()");
            return null;
        };
    }

    /**
     * {@code background-size}: one or two of a non-negative length, percentage or {@code auto}, width
     * then height; a missing height is {@code auto}. {@code cover} and {@code contain} are not supported.
     */
    public static Syntax<BackgroundSize> backgroundSize() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() > 2) {
                diag.error(parts.get(2).pos(), "expected 1 or 2 values");
                return null;
            }
            ComponentValue first = parts.get(0);
            if (first instanceof Token t && (t.isIdent("cover") || t.isIdent("contain"))) {
                diag.error(t.pos(), "'" + t.value() + "' is not supported; give the size in px or %");
                return null;
            }
            Length width = lengthOf(first, true, true, false, diag);
            if (width == null) {
                return null;
            }
            if (parts.size() == 1) {
                return new BackgroundSize(width, Length.AUTO);
            }
            Length height = lengthOf(parts.get(1), true, true, false, diag);
            return height == null ? null : new BackgroundSize(width, height);
        };
    }

    /** Prefix of an image source that names an atlas sprite; the host resolves it. */
    public static final String SPRITE = "sprite:";

    /** A {@code namespace:path} resource location, or a bare path, optionally after {@link #SPRITE}; lower-case as Minecraft requires. */
    private static String resourceLocation(String src, SourcePos pos, Diagnostics diag) {
        String s = src.startsWith(SPRITE) ? src.substring(SPRITE.length()) : src;
        int colon = s.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : s.substring(0, colon);
        String path = s.substring(colon + 1);
        if (namespace.isEmpty() || path.isEmpty() || !allowed(namespace, "_-.") || !allowed(path, "_-./")) {
            diag.error(pos, "invalid resource location '" + src + "' (namespace:path, lower-case a-z, 0-9 and _ - . with / in the path)");
            return null;
        }
        return src;
    }

    private static boolean allowed(String s, String extra) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || extra.indexOf(c) >= 0)) {
                return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    private interface Mixer<T> {
        T mix(T from, T to, float p);
    }

    /** {@code numberUnit} as in {@link Syntax#numberUnit()}. */
    private static <T> Syntax<T> interpolating(Syntax<T> parser, Mixer<T> mixer, String numberUnit) {
        return new Syntax<>() {
            @Override
            public T parse(List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
                return parser.parse(value, pos, diag);
            }

            @Override
            public T interpolate(T from, T to, float p) {
                return mixer.mix(from, to, p);
            }

            @Override
            public String numberUnit() {
                return numberUnit;
            }
        };
    }

    private static float mix(float a, float b, float p) {
        return a + (b - a) * p;
    }

    private static Integer mixColor(Integer from, Integer to, float p) {
        int a = from;
        int b = to;
        float fromAlpha = (a >>> 24) / 255f;
        float toAlpha = (b >>> 24) / 255f;
        float alpha = Math.max(0, Math.min(1, mix(fromAlpha, toAlpha, p)));
        if (alpha == 0) {
            return 0;
        }
        int out = Math.round(alpha * 255) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = mix((a >> shift & 0xFF) * fromAlpha, (b >> shift & 0xFF) * toAlpha, p) / alpha;
            out |= Math.round(Math.max(0, Math.min(255, c))) << shift;
        }
        return out;
    }

    /** The comma-separated items of {@code value} without whitespace; {@code null} after an error. */
    static List<List<ComponentValue>> splitCommas(List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
        List<List<ComponentValue>> items = new ArrayList<>();
        List<ComponentValue> current = new ArrayList<>();
        SourcePos last = pos;
        for (ComponentValue v : significant(value)) {
            if (v instanceof Token t && t.is(Token.Kind.COMMA)) {
                if (current.isEmpty()) {
                    diag.error(t.pos(), "empty item before ','");
                    return null;
                }
                items.add(current);
                current = new ArrayList<>();
                last = t.pos();
            } else {
                current.add(v);
            }
        }
        if (current.isEmpty()) {
            diag.error(last, "empty item after ','");
            return null;
        }
        items.add(current);
        return items;
    }

    static Float timeOf(ComponentValue v, boolean negative, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.DIMENSION)) {
            double seconds;
            if (t.unit().equalsIgnoreCase("s")) {
                seconds = t.number();
            } else if (t.unit().equalsIgnoreCase("ms")) {
                seconds = t.number() / 1000;
            } else {
                diag.error(t.pos(), "expected a time in s or ms");
                return null;
            }
            if (!negative && seconds < 0) {
                diag.error(t.pos(), "negative time not allowed");
                return null;
            }
            return (float) seconds;
        }
        if (v instanceof Token t && t.is(Token.Kind.NUMBER) && t.number() == 0) {
            diag.error(t.pos(), "time needs a unit: 0s");
            return null;
        }
        diag.error(v.pos(), "expected a time in s or ms");
        return null;
    }

    private static final Map<String, Easing> EASING_KEYWORDS = Map.of(
            "linear", Easing.LINEAR,
            "ease", Easing.EASE,
            "ease-in", Easing.EASE_IN,
            "ease-out", Easing.EASE_OUT,
            "ease-in-out", Easing.EASE_IN_OUT);

    /** Whether {@code v} is meant as an easing function, supported or not. */
    static boolean looksLikeEasing(ComponentValue v) {
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            String name = t.value().toLowerCase(Locale.ROOT);
            return EASING_KEYWORDS.containsKey(name) || name.equals("step-start") || name.equals("step-end");
        }
        return v instanceof ComponentValue.Function;
    }

    static Easing easingOf(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            Easing e = EASING_KEYWORDS.get(t.value().toLowerCase(Locale.ROOT));
            if (e != null) {
                return e;
            }
        } else if (v instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("cubic-bezier")) {
            return cubicBezier(f, diag);
        }
        diag.error(v.pos(), "unsupported easing (use linear, ease, ease-in, ease-out, ease-in-out or cubic-bezier())");
        return null;
    }

    private static Easing cubicBezier(ComponentValue.Function f, Diagnostics diag) {
        float[] p = new float[4];
        int n = 0;
        for (ComponentValue a : significant(f.args())) {
            if (a instanceof Token t && t.is(Token.Kind.COMMA)) {
                continue;
            }
            if (!(a instanceof Token t && t.is(Token.Kind.NUMBER)) || n == 4) {
                diag.error(a.pos(), "cubic-bezier() takes four numbers");
                return null;
            }
            p[n++] = (float) t.number();
        }
        if (n != 4) {
            diag.error(f.pos(), "cubic-bezier() takes four numbers");
            return null;
        }
        if (p[0] < 0 || p[0] > 1 || p[2] < 0 || p[2] > 1) {
            diag.error(f.pos(), "cubic-bezier() x values must be between 0 and 1");
            return null;
        }
        return new Easing(p[0], p[1], p[2], p[3]);
    }

    /** A {@code transition-property} name, lower-cased; {@code none} is returned for the caller to check. */
    static String transitionNameOf(ComponentValue v, PropertyRegistry registry, Diagnostics diag) {
        if (!(v instanceof Token t && t.is(Token.Kind.IDENT))) {
            diag.error(v.pos(), "expected a property name");
            return null;
        }
        String name = t.value().toLowerCase(Locale.ROOT);
        if (name.equals("all") || name.equals("none")) {
            return name;
        }
        PropertyDef<?> def = registry.byName(name);
        Shorthand shorthand = def == null ? registry.shorthand(name) : null;
        if (def == null && shorthand == null) {
            diag.error(t.pos(), "unsupported property '" + name + "'");
            return null;
        }
        for (PropertyDef<?> d : def != null ? List.<PropertyDef<?>>of(def) : shorthand.longhands()) {
            if (!d.animatable()) {
                diag.error(t.pos(), "'" + d.name() + "' cannot be transitioned");
                return null;
            }
        }
        return name;
    }

    static Length lengthOf(ComponentValue v, boolean percent, boolean auto, boolean negative, Diagnostics diag) {
        if (v instanceof Token t) {
            switch (t.kind()) {
                case DIMENSION:
                    if (t.unit().equalsIgnoreCase("px")) {
                        return checkSign(Length.px((float) t.number()), t, negative, diag);
                    }
                    diag.error(t.pos(), "unsupported unit '" + t.unit() + "' (only px)");
                    return null;
                case NUMBER:
                    if (t.number() == 0) {
                        return Length.ZERO;
                    }
                    diag.error(t.pos(), "length needs a unit: " + fmt(t.number()) + "px");
                    return null;
                case PERCENTAGE:
                    if (percent) {
                        return checkSign(Length.percent((float) t.number()), t, negative, diag);
                    }
                    break;
                case IDENT:
                    if (auto && t.isIdent("auto")) {
                        return Length.AUTO;
                    }
                    break;
                default:
                    break;
            }
        }
        diag.error(v.pos(), "expected a length in px" + (percent ? ", a percentage" : "") + (auto ? " or auto" : ""));
        return null;
    }

    private static Length checkSign(Length l, Token t, boolean negative, Diagnostics diag) {
        if (!negative && l.value() < 0) {
            diag.error(t.pos(), "negative value not allowed");
            return null;
        }
        return l;
    }

    private static final Map<String, Integer> NAMED_COLORS = Map.of(
            "transparent", 0x00000000,
            "black", 0xFF000000,
            "white", 0xFFFFFFFF,
            "red", 0xFFFF0000,
            "lime", 0xFF00FF00,
            "blue", 0xFF0000FF,
            "yellow", 0xFFFFFF00,
            "gray", 0xFF808080,
            "grey", 0xFF808080);

    static Integer colorOf(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t) {
            if (t.is(Token.Kind.HASH) || t.is(Token.Kind.HASH_ID)) {
                Integer c = hexColor(t.value());
                if (c == null) {
                    diag.error(t.pos(), "invalid hex color #" + t.value());
                }
                return c;
            }
            if (t.is(Token.Kind.IDENT)) {
                Integer c = NAMED_COLORS.get(t.value().toLowerCase(Locale.ROOT));
                if (c == null) {
                    diag.error(t.pos(), "unsupported color name '" + t.value() + "' (use hex or rgb())");
                }
                return c;
            }
        } else if (v instanceof ComponentValue.Function f
                && (f.name().equalsIgnoreCase("rgb") || f.name().equalsIgnoreCase("rgba"))) {
            return rgbFunction(f, diag);
        }
        diag.error(v.pos(), "expected a color");
        return null;
    }

    private static Integer hexColor(String hex) {
        int n = hex.length();
        if (n != 3 && n != 4 && n != 6 && n != 8) {
            return null;
        }
        int[] d = new int[n];
        for (int i = 0; i < n; i++) {
            d[i] = Character.digit(hex.charAt(i), 16);
            if (d[i] < 0) {
                return null;
            }
        }
        int r, g, b, a = 255;
        if (n <= 4) {
            r = d[0] * 17;
            g = d[1] * 17;
            b = d[2] * 17;
            if (n == 4) {
                a = d[3] * 17;
            }
        } else {
            r = d[0] * 16 + d[1];
            g = d[2] * 16 + d[3];
            b = d[4] * 16 + d[5];
            if (n == 8) {
                a = d[6] * 16 + d[7];
            }
        }
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** {@code rgb(r g b [/ a])} or {@code rgb(r, g, b[, a])}; channels 0-255 or %, alpha 0-1 or %. */
    private static Integer rgbFunction(ComponentValue.Function f, Diagnostics diag) {
        List<Token> parts = new ArrayList<>();
        for (ComponentValue a : significant(f.args())) {
            if (!(a instanceof Token t)) {
                diag.error(a.pos(), "unexpected value in " + f.name() + "()");
                return null;
            }
            if (!t.is(Token.Kind.COMMA) && !t.isDelim('/')) {
                parts.add(t);
            }
        }
        if (parts.size() != 3 && parts.size() != 4) {
            diag.error(f.pos(), f.name() + "() takes 3 channels and an optional alpha");
            return null;
        }
        int argb = 0;
        for (int i = 0; i < 3; i++) {
            Integer c = channel(parts.get(i), 255, diag);
            if (c == null) {
                return null;
            }
            argb |= c << (16 - 8 * i);
        }
        int alpha = 255;
        if (parts.size() == 4) {
            Integer a = channel(parts.get(3), 1, diag);
            if (a == null) {
                return null;
            }
            alpha = a;
        }
        return alpha << 24 | argb;
    }

    /** Clamped 0-255; a NUMBER is scaled from {@code [0, max]}. */
    private static Integer channel(Token t, double max, Diagnostics diag) {
        double unit;
        if (t.is(Token.Kind.NUMBER)) {
            unit = t.number() / max;
        } else if (t.is(Token.Kind.PERCENTAGE)) {
            unit = t.number() / 100;
        } else {
            diag.error(t.pos(), "expected a number or percentage");
            return null;
        }
        return (int) Math.round(Math.max(0, Math.min(1, unit)) * 255);
    }

    static ComponentValue single(List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
        List<ComponentValue> s = significant(value);
        if (s.size() != 1) {
            diag.error(s.size() > 1 ? s.get(1).pos() : pos, "expected a single value");
            return null;
        }
        return s.get(0);
    }

    private static String keywordList(Class<? extends Enum<?>> type) {
        StringBuilder sb = new StringBuilder();
        for (Enum<?> e : type.getEnumConstants()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
        return sb.toString();
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
