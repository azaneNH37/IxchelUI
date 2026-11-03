package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.azane.ixchel.core.css.property.Syntaxes.angle;
import static com.azane.ixchel.core.css.property.Syntaxes.colorOf;
import static com.azane.ixchel.core.css.property.Syntaxes.interpolating;
import static com.azane.ixchel.core.css.property.Syntaxes.isLengthToken;
import static com.azane.ixchel.core.css.property.Syntaxes.lengthOf;
import static com.azane.ixchel.core.css.property.Syntaxes.mix;
import static com.azane.ixchel.core.css.property.Syntaxes.mixColor;
import static com.azane.ixchel.core.css.property.Syntaxes.single;
import static com.azane.ixchel.core.css.property.Syntaxes.significant;

/** Value grammars of the compositing effects: filters and blend modes. */
final class EffectSyntaxes {
    private EffectSyntaxes() {
    }

    /**
     * {@code filter} and {@code backdrop-filter}: {@code none} or a list of {@code grayscale()},
     * {@code sepia()}, {@code saturate()}, {@code hue-rotate()}, {@code invert()}, {@code brightness()},
     * {@code contrast()}, {@code opacity()} (a number or percentage, 1 when omitted; the first four of
     * these and {@code opacity()} stop at 1), {@code blur()} (a length, 0 when omitted) and
     * {@code drop-shadow()} (two offsets, an optional blur and a color); with {@code paint}, also
     * {@code -ix-paint(name)}, a layer effect of the document. Two lists interpolate function by function
     * when their kinds (and effects) match, the shorter one padded with functions that change nothing;
     * others jump.
     */
    static Syntax<List<Filter>> filters(boolean paint) {
        return interpolating((value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("none")) {
                return List.of();
            }
            List<Filter> out = new ArrayList<>(parts.size());
            for (ComponentValue v : parts) {
                if (!(v instanceof ComponentValue.Function f)) {
                    diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected none or filter functions");
                    return null;
                }
                Filter filter = filter(f, paint, diag);
                if (filter == null) {
                    return null;
                }
                out.add(filter);
            }
            return List.copyOf(out);
        }, EffectSyntaxes::mixFilters, null);
    }

    private static Filter filter(ComponentValue.Function f, boolean paint, Diagnostics diag) {
        String name = f.name().toLowerCase(Locale.ROOT);
        List<ComponentValue> args = significant(f.args());
        if (name.equals("-ix-paint")) {
            if (!paint) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "-ix-paint() is not supported in backdrop-filter",
                        "backdrop effects are not supported yet; use -ix-paint() in filter");
                return null;
            }
            if (args.size() != 1 || !(args.get(0) instanceof Token t && t.is(Token.Kind.IDENT))) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "-ix-paint() takes the name of a layer effect");
                return null;
            }
            return Filter.paint(t.value());
        }
        Filter.Kind kind = switch (name) {
            case "grayscale" -> Filter.Kind.GRAYSCALE;
            case "sepia" -> Filter.Kind.SEPIA;
            case "saturate" -> Filter.Kind.SATURATE;
            case "hue-rotate" -> Filter.Kind.HUE_ROTATE;
            case "invert" -> Filter.Kind.INVERT;
            case "brightness" -> Filter.Kind.BRIGHTNESS;
            case "contrast" -> Filter.Kind.CONTRAST;
            case "opacity" -> Filter.Kind.OPACITY;
            case "blur" -> Filter.Kind.BLUR;
            case "drop-shadow" -> Filter.Kind.DROP_SHADOW;
            default -> null;
        };
        if (kind == null) {
            diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "unsupported filter function '" + f.name() + "'",
                    "use grayscale, sepia, saturate, hue-rotate, invert, brightness, contrast, opacity, blur, drop-shadow"
                            + (paint ? " or -ix-paint" : ""));
            return null;
        }
        if (kind == Filter.Kind.DROP_SHADOW) {
            return dropShadow(f, args, diag);
        }
        if (args.size() > 1) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), name + "() takes at most 1 value");
            return null;
        }
        if (kind == Filter.Kind.HUE_ROTATE) {
            Float deg = args.isEmpty() ? Float.valueOf(0) : angle(args.get(0), diag);
            return deg == null ? null : Filter.amount(kind, deg);
        }
        if (kind == Filter.Kind.BLUR) {
            if (args.isEmpty()) {
                return new Filter(kind, 0, 0, 0, 0, 0, null);
            }
            Length l = lengthOf(args.get(0), false, false, false, diag);
            return l == null ? null : new Filter(kind, 0, 0, 0, l.value(), 0, null);
        }
        float amount = 1;
        if (!args.isEmpty()) {
            ComponentValue a = args.get(0);
            if (a instanceof Token t && t.is(Token.Kind.NUMBER)) {
                amount = (float) t.number();
            } else if (a instanceof Token t && t.is(Token.Kind.PERCENTAGE)) {
                amount = (float) t.number() / 100;
            } else {
                diag.report(PropertyCode.INVALID_VALUE, a.pos(), "expected a number or percentage");
                return null;
            }
            if (amount < 0) {
                diag.report(PropertyCode.NEGATIVE_VALUE, a.pos(), name + "() does not take a negative amount");
                return null;
            }
        }
        boolean capped = kind == Filter.Kind.GRAYSCALE || kind == Filter.Kind.SEPIA || kind == Filter.Kind.INVERT
                || kind == Filter.Kind.OPACITY;
        return Filter.amount(kind, capped ? Math.min(1, amount) : amount);
    }

    /** {@code drop-shadow(<color>? <x> <y> <blur>?)}, the color before or after the lengths. */
    private static Filter dropShadow(ComponentValue.Function f, List<ComponentValue> args, Diagnostics diag) {
        ComponentValue color = null;
        List<ComponentValue> lengths = new ArrayList<>(3);
        for (ComponentValue v : args) {
            if (isLengthToken(v)) {
                lengths.add(v);
            } else if (color == null) {
                color = v;
            } else {
                diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "a drop shadow has one color");
                return null;
            }
        }
        if (lengths.size() < 2 || lengths.size() > 3) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "drop-shadow() takes two offsets, an optional blur and a color");
            return null;
        }
        if (color == null) {
            diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "a drop-shadow() without a color is not supported",
                    "add the color, e.g. drop-shadow(0 2px 2px #80000000)");
            return null;
        }
        float[] l = new float[3];
        for (int i = 0; i < lengths.size(); i++) {
            Length len = lengthOf(lengths.get(i), false, false, i != 2, diag);
            if (len == null) {
                return null;
            }
            l[i] = len.value();
        }
        Integer c = colorOf(color, diag);
        // The blur length is twice the standard deviation, as box-shadow's.
        return c == null ? null : new Filter(Filter.Kind.DROP_SHADOW, 0, l[0], l[1], l[2] / 2, c, null);
    }

    private static List<Filter> mixFilters(List<Filter> from, List<Filter> to, float p) {
        int n = Math.max(from.size(), to.size());
        List<Filter> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Filter a = i < from.size() ? from.get(i) : null;
            Filter b = i < to.size() ? to.get(i) : null;
            if (a == null && b.kind() == Filter.Kind.PAINT || b == null && a.kind() == Filter.Kind.PAINT) {
                return null;
            }
            if (a == null) {
                a = Filter.identity(b.kind());
            } else if (b == null) {
                b = Filter.identity(a.kind());
            }
            if (a.kind() != b.kind()) {
                return null;
            }
            if (a.kind() == Filter.Kind.PAINT) {
                if (!a.effect().equals(b.effect())) {
                    return null;
                }
                out.add(a);
                continue;
            }
            out.add(new Filter(a.kind(), Math.max(0, mix(a.amount(), b.amount(), p)), mix(a.x(), b.x(), p), mix(a.y(), b.y(), p),
                    Math.max(0, mix(a.sigma(), b.sigma(), p)), mixColor(a.color(), b.color(), p), null));
        }
        return out;
    }

    /**
     * {@code mix-blend-mode}: the modes the host composites with fixed blending, {@code normal},
     * {@code multiply}, {@code screen}, {@code plus-lighter}, {@code darken} and {@code lighten}; the
     * other CSS modes are reported as unsupported.
     */
    static Syntax<Keywords.MixBlendMode> mixBlendMode() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
                switch (t.value().toLowerCase(Locale.ROOT)) {
                    case "normal":
                        return Keywords.MixBlendMode.NORMAL;
                    case "multiply":
                        return Keywords.MixBlendMode.MULTIPLY;
                    case "screen":
                        return Keywords.MixBlendMode.SCREEN;
                    case "plus-lighter":
                        return Keywords.MixBlendMode.PLUS_LIGHTER;
                    case "darken":
                        return Keywords.MixBlendMode.DARKEN;
                    case "lighten":
                        return Keywords.MixBlendMode.LIGHTEN;
                    case "overlay", "color-dodge", "color-burn", "hard-light", "soft-light", "difference", "exclusion",
                         "hue", "saturation", "color", "luminosity", "plus-darker":
                        diag.report(PropertyCode.UNSUPPORTED_VALUE, v.pos(), "mix-blend-mode '" + t.value() + "' is not supported",
                                "use normal, multiply, screen, plus-lighter, darken or lighten");
                        return null;
                    default:
                        break;
                }
            }
            diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a blend mode");
            return null;
        };
    }
}
