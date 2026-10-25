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
     */
    public static Syntax<Length> length(boolean percent, boolean auto, boolean negative) {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : lengthOf(v, percent, auto, negative, diag);
        };
    }

    public static Syntax<Float> number(boolean negative) {
        return (value, pos, diag) -> {
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
        };
    }

    /** ARGB. {@code #rgb}, {@code #rgba}, {@code #rrggbb}, {@code #rrggbbaa}, {@code rgb()}/{@code rgba()}, a few names. */
    public static Syntax<Integer> color() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : colorOf(v, diag);
        };
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
