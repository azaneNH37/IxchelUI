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
            E e = keywordOf(v, type);
            if (e != null) {
                return e;
            }
            diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected one of " + keywordList(type));
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
        }, (a, b, p) -> Length.mix(a, b, p, !negative), "px");
    }

    public static Syntax<Float> number(boolean negative) {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.is(Token.Kind.NUMBER)) {
                if (!negative && t.number() < 0) {
                    diag.report(PropertyCode.NEGATIVE_VALUE, t.pos(), "negative value not allowed");
                    return null;
                }
                return (float) t.number();
            }
            diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a number");
            return null;
        }, (a, b, p) -> {
            float v = mix(a, b, p);
            return negative ? v : Math.max(0, v);
        }, "");
    }

    /** {@code line-height}: {@code normal}, a non-negative number, length or percentage. */
    public static Syntax<LineHeight> lineHeight() {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.isIdent("normal")) {
                return LineHeight.NORMAL;
            }
            if (v instanceof Token t && t.is(Token.Kind.NUMBER)) {
                if (t.number() < 0) {
                    diag.report(PropertyCode.NEGATIVE_VALUE, t.pos(), "negative value not allowed");
                    return null;
                }
                return LineHeight.factor((float) t.number());
            }
            Length l = lengthOf(v, true, false, false, diag);
            return l == null ? null : LineHeight.of(l);
        }, (a, b, p) -> {
            if (a.kind() != b.kind() || a.kind() == LineHeight.Kind.NORMAL) {
                return null;
            }
            return a.kind() == LineHeight.Kind.FACTOR ? LineHeight.factor(Math.max(0, mix(a.factor(), b.factor(), p)))
                    : LineHeight.of(Length.mix(a.length(), b.length(), p, true));
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

    /** A {@code <length>} in px as a plain number, for an effect parameter; interpolates. */
    public static Syntax<Float> pxLength() {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            Length l = v == null ? null : lengthOf(v, false, false, true, diag);
            return l == null ? null : l.value();
        }, Syntaxes::mix, "px");
    }

    /** A {@code <percentage>} as a fraction ({@code 50%} is 0.5), for an effect parameter; interpolates. */
    public static Syntax<Float> percentage() {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.is(Token.Kind.PERCENTAGE)) {
                return (float) t.number() / 100;
            }
            diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a percentage");
            return null;
        }, Syntaxes::mix, null);
    }

    /** An {@code <angle>} in degrees, for an effect parameter; interpolates. */
    public static Syntax<Float> angle() {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : angle(v, diag);
        }, Syntaxes::mix, null);
    }

    /** A {@code <time>} in seconds, negative allowed, for an effect parameter; interpolates. */
    public static Syntax<Float> interpolatingTime() {
        return interpolating(time(true), Syntaxes::mix, null);
    }

    /** {@code linear}, {@code ease}, {@code ease-in}, {@code ease-out}, {@code ease-in-out}, {@code step-start}, {@code step-end}, {@code cubic-bezier()} or {@code steps()}. */
    public static Syntax<Easing> easing() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : easingOf(v, diag);
        };
    }

    /**
     * One or more {@code item} values separated by commas; the result is unmodifiable. A bound number
     * is one item, in {@code item}'s {@link Syntax#numberUnit()}.
     */
    public static <T> Syntax<List<T>> commaList(Syntax<T> item) {
        return new Syntax<>() {
            @Override
            public List<T> parse(List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
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
            }

            @Override
            public String numberUnit() {
                return item.numberUnit();
            }
        };
    }

    /**
     * {@link #commaList} whose lists interpolate item by item with {@code item}'s interpolation, as a
     * CSS repeatable list: lists of different lengths are first repeated to their least common
     * multiple. {@code null} when any pair of items does not interpolate.
     */
    public static <T> Syntax<List<T>> interpolatingList(Syntax<T> item) {
        Syntax<List<T>> list = commaList(item);
        return interpolating(list, (from, to, p) -> {
            int a = from.size();
            int b = to.size();
            int n = a / gcd(a, b) * b;
            List<T> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                T v = item.interpolate(from.get(i % a), to.get(i % b), p);
                if (v == null) {
                    return null;
                }
                out.add(v);
            }
            return List.copyOf(out);
        }, item.numberUnit());
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /** {@code letter-spacing}: {@code normal} (0) or a length in px, negative allowed; interpolates. */
    public static Syntax<Length> letterSpacing() {
        return interpolating((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.isIdent("normal")) {
                return Length.ZERO;
            }
            if (v instanceof Token t && t.is(Token.Kind.PERCENTAGE)) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "a percentage letter-spacing is not supported", "use em");
                return null;
            }
            return lengthOf(v, false, false, true, diag);
        }, (a, b, p) -> Length.mix(a, b, p, false), "px");
    }

    /**
     * {@code aspect-ratio}: {@code auto} (0), a positive number, or {@code <width> / <height>};
     * {@code auto} with a ratio is the ratio, as for every non-replaced box. A ratio with a zero
     * side is degenerate, which CSS treats as {@code auto}. Two ratios interpolate by their logarithm,
     * as CSS does; {@code auto} does not interpolate.
     */
    public static Syntax<Float> aspectRatio() {
        return interpolating((value, pos, diag) -> {
            List<ComponentValue> parts = new ArrayList<>(significant(value));
            if (parts.size() > 1 && parts.get(0) instanceof Token t && t.isIdent("auto")) {
                parts.remove(0);
            } else if (parts.size() > 1 && parts.get(parts.size() - 1) instanceof Token t && t.isIdent("auto")) {
                parts.remove(parts.size() - 1);
            }
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("auto")) {
                return 0f;
            }
            Float w = ratioSide(parts.get(0), diag);
            if (w == null) {
                return null;
            }
            float h = 1;
            if (parts.size() > 1) {
                if (parts.size() != 3 || !(parts.get(1) instanceof Token slash && slash.isDelim('/'))) {
                    diag.report(PropertyCode.INVALID_VALUE, parts.get(1).pos(), "expected auto, a number or <width> / <height>");
                    return null;
                }
                Float s = ratioSide(parts.get(2), diag);
                if (s == null) {
                    return null;
                }
                h = s;
            }
            return w == 0 || h == 0 ? 0f : w / h;
        }, (a, b, p) -> a <= 0 || b <= 0 ? null : (float) Math.exp(mix((float) Math.log(a), (float) Math.log(b), p)), "");
    }

    private static Float ratioSide(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.NUMBER)) {
            if (t.number() < 0) {
                diag.report(PropertyCode.NEGATIVE_VALUE, t.pos(), "negative value not allowed");
                return null;
            }
            return (float) t.number();
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected auto, a number or <width> / <height>");
        return null;
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
                        diag.report(PropertyCode.NONE_NOT_ALONE, v.pos(), "'none' must be the only value");
                        return null;
                    }
                    return List.of();
                }
                out.add(name);
            }
            return List.copyOf(out);
        };
    }

    /** {@code animation-name}: comma-separated {@code none} or keyframes names, identifiers or strings. */
    public static Syntax<List<String>> animationName() {
        return commaList((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : animationNameOf(v, diag);
        });
    }

    /**
     * {@code font-family}: comma-separated Minecraft font ids, each a string or written bare as
     * {@code namespace:path} or {@code path}; a missing namespace is {@code minecraft}. Kept as full ids.
     */
    public static Syntax<List<String>> fontFamily() {
        return commaList((value, pos, diag) -> {
            String id = null;
            if (value.size() == 1 && value.get(0) instanceof Token t && (t.is(Token.Kind.STRING) || t.is(Token.Kind.IDENT))) {
                id = t.value();
            } else if (value.size() == 3 && value.get(0) instanceof Token ns && ns.is(Token.Kind.IDENT)
                    && value.get(1) instanceof Token c && c.is(Token.Kind.COLON)
                    && value.get(2) instanceof Token path && path.is(Token.Kind.IDENT)) {
                id = ns.value() + ":" + path.value();
            }
            if (id == null || id.startsWith(SPRITE)) {
                diag.report(PropertyCode.INVALID_VALUE, pos, "expected a font id such as minecraft:uniform or \"mod:path\"");
                return null;
            }
            if (resourceLocation(id, pos, diag) == null) {
                return null;
            }
            return id.indexOf(':') < 0 ? "minecraft:" + id : id;
        });
    }

    /** {@code animation-iteration-count} item: a non-negative number, {@code infinite} as positive infinity. */
    public static Syntax<Float> iterationCount() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : iterationCountOf(v, diag);
        };
    }

    /** A keyframes name as written, or {@code none}; {@code null} after an error. */
    static String animationNameOf(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            String name = t.value();
            if (name.equalsIgnoreCase("none")) {
                return "none";
            }
            if (!name.equalsIgnoreCase("inherit") && !name.equalsIgnoreCase("initial") && !name.equalsIgnoreCase("unset")
                    && !name.equalsIgnoreCase("default")) {
                return name;
            }
        } else if (v instanceof Token t && t.is(Token.Kind.STRING)) {
            return t.value();
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a keyframes name or none");
        return null;
    }

    static Float iterationCountOf(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.isIdent("infinite")) {
            return Float.POSITIVE_INFINITY;
        }
        if (v instanceof Token t && t.is(Token.Kind.NUMBER) && t.number() >= 0) {
            return (float) t.number();
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a non-negative number or infinite");
        return null;
    }

    /** The constant of {@code type} named by {@code v}, an identifier, or {@code null} without a report. */
    static <E extends Enum<E>> E keywordOf(ComponentValue v, Class<E> type) {
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            String name = t.value().toUpperCase(Locale.ROOT).replace('-', '_');
            for (E e : type.getEnumConstants()) {
                if (e.name().equals(name)) {
                    return e;
                }
            }
        }
        return null;
    }

    /**
     * {@code background-size}: one or two of a non-negative length, percentage or {@code auto}, width
     * then height; a missing height is {@code auto}. {@code cover} and {@code contain} are not supported.
     */
    public static Syntax<BackgroundSize> backgroundSize() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() > 2) {
                diag.report(PropertyCode.VALUE_COUNT, parts.get(2).pos(), "expected 1 or 2 values");
                return null;
            }
            ComponentValue first = parts.get(0);
            if (first instanceof Token t && (t.isIdent("cover") || t.isIdent("contain"))) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "'" + t.value() + "' is not supported",
                        "give the size in px or %");
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

    /** {@code scrollbar-color}: {@code auto} or a thumb color then a track color. */
    public static Syntax<ScrollbarColor> scrollbarColor() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("auto")) {
                return ScrollbarColor.AUTO;
            }
            if (parts.size() != 2) {
                diag.report(PropertyCode.VALUE_COUNT, parts.get(0).pos(), "expected auto or a thumb and a track color");
                return null;
            }
            Integer thumb = colorOf(parts.get(0), diag);
            Integer track = thumb == null ? null : colorOf(parts.get(1), diag);
            return track == null ? null : new ScrollbarColor(thumb, track);
        };
    }

    /**
     * {@code text-shadow}: {@code none} or one shadow, two px offsets, an optional blur that must be 0,
     * and a color before or after them. Several shadows, a blur and a missing color (CSS
     * {@code currentcolor}) are not supported. Interpolates offsets and color, {@code none} counting as
     * a transparent shadow at no offset.
     */
    public static Syntax<TextShadow> textShadow() {
        return interpolating((value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("none")) {
                return TextShadow.NONE;
            }
            for (ComponentValue v : parts) {
                if (v instanceof Token t && t.is(Token.Kind.COMMA)) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "several text shadows are not supported",
                            "keep one shadow");
                    return null;
                }
            }
            int from = 0;
            int to = parts.size();
            ComponentValue color = null;
            if (!isLengthToken(parts.get(0))) {
                color = parts.get(from++);
            } else if (!isLengthToken(parts.get(to - 1))) {
                color = parts.get(--to);
            }
            if (to - from < 2 || to - from > 3) {
                diag.report(PropertyCode.VALUE_COUNT, parts.get(0).pos(), "expected two offsets, an optional blur of 0 and a color");
                return null;
            }
            if (color == null) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, parts.get(0).pos(), "a text-shadow without a color is not supported",
                        "add the color, e.g. 1px 1px 0 #3F3F3F");
                return null;
            }
            Length x = lengthOf(parts.get(from), false, false, true, diag);
            Length y = x == null ? null : lengthOf(parts.get(from + 1), false, false, true, diag);
            if (y == null) {
                return null;
            }
            if (to - from == 3) {
                Length blur = lengthOf(parts.get(from + 2), false, false, false, diag);
                if (blur == null) {
                    return null;
                }
                if (blur.value() != 0) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, parts.get(from + 2).pos(),
                            "a blurred text-shadow is not supported", "use a blur of 0");
                    return null;
                }
            }
            Integer c = colorOf(color, diag);
            return c == null ? null : new TextShadow(x.value(), y.value(), c);
        }, (a, b, p) -> new TextShadow(mix(a.x(), b.x(), p), mix(a.y(), b.y(), p), mixColor(a.color(), b.color(), p)),
                null);
    }

    static boolean isLengthToken(ComponentValue v) {
        return v instanceof Token t && (t.is(Token.Kind.DIMENSION) || t.is(Token.Kind.NUMBER)) || CalcParser.isMath(v);
    }

    /**
     * {@code transform}: {@code none} or a list of {@code translate()}, {@code translateX()},
     * {@code translateY()}, {@code scale()}, {@code scaleX()}, {@code scaleY()}, {@code rotate()},
     * {@code skew()}, {@code skewX()} and {@code skewY()} (angles in {@code deg}, {@code rad},
     * {@code grad} or {@code turn}). Two lists interpolate function by function when their kinds
     * match, a missing side counting as {@code none}; others jump. 3D functions are not supported.
     */
    public static Syntax<Transform> transform() {
        return interpolating((value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("none")) {
                return Transform.NONE;
            }
            List<Transform.Fn> out = new ArrayList<>(parts.size());
            for (ComponentValue v : parts) {
                if (!(v instanceof ComponentValue.Function f)) {
                    diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected none or transform functions");
                    return null;
                }
                Transform.Fn fn = transformFn(f, diag);
                if (fn == null) {
                    return null;
                }
                out.add(fn);
            }
            return new Transform(List.copyOf(out));
        }, Syntaxes::mixTransform, null);
    }

    private static Transform.Fn transformFn(ComponentValue.Function f, Diagnostics diag) {
        String name = f.name().toLowerCase(Locale.ROOT);
        List<ComponentValue> args = new ArrayList<>();
        for (ComponentValue a : significant(f.args())) {
            if (!(a instanceof Token t && t.is(Token.Kind.COMMA))) {
                args.add(a);
            }
        }
        int max = name.equals("translate") || name.equals("scale") || name.equals("skew") ? 2 : 1;
        if (args.isEmpty() || args.size() > max) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), name + "() takes " + (max == 2 ? "1 or 2 values" : "1 value"));
            return null;
        }
        switch (name) {
            case "translate", "translatex", "translatey" -> {
                Length a = lengthOf(args.get(0), true, false, true, diag);
                Length b = args.size() == 2 ? lengthOf(args.get(1), true, false, true, diag) : Length.ZERO;
                if (a == null || b == null) {
                    return null;
                }
                return name.equals("translatey")
                        ? new Transform.Fn(Transform.Kind.TRANSLATE, Length.ZERO, a)
                        : new Transform.Fn(Transform.Kind.TRANSLATE, a, b);
            }
            case "scale", "scalex", "scaley" -> {
                Float a = factor(args.get(0), diag);
                Float b = args.size() == 2 ? factor(args.get(1), diag) : a;
                if (a == null || b == null) {
                    return null;
                }
                return new Transform.Fn(Transform.Kind.SCALE,
                        Length.px(name.equals("scaley") ? 1 : a), Length.px(name.equals("scalex") ? 1 : b));
            }
            case "rotate" -> {
                Float deg = angle(args.get(0), diag);
                return deg == null ? null : new Transform.Fn(Transform.Kind.ROTATE, Length.px(deg), Length.ZERO);
            }
            case "skew", "skewx", "skewy" -> {
                Float a = angle(args.get(0), diag);
                Float b = args.size() == 2 ? angle(args.get(1), diag) : Float.valueOf(0);
                if (a == null || b == null) {
                    return null;
                }
                return name.equals("skewy")
                        ? new Transform.Fn(Transform.Kind.SKEW, Length.ZERO, Length.px(a))
                        : new Transform.Fn(Transform.Kind.SKEW, Length.px(a), Length.px(b));
            }
            default -> {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "unsupported transform function '" + f.name() + "'",
                        "use translate, translateX, translateY, scale, scaleX, scaleY, rotate, skew, skewX or skewY");
                return null;
            }
        }
    }

    private static Float factor(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.NUMBER)) {
            return (float) t.number();
        }
        if (v instanceof Token t && t.is(Token.Kind.PERCENTAGE)) {
            return (float) t.number() / 100;
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a number or percentage");
        return null;
    }

    /** Degrees. */
    static Float angle(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t) {
            if (t.is(Token.Kind.NUMBER) && t.number() == 0) {
                return 0f;
            }
            if (t.is(Token.Kind.DIMENSION)) {
                double n = t.number();
                switch (t.unit().toLowerCase(Locale.ROOT)) {
                    case "deg":
                        return (float) n;
                    case "rad":
                        return (float) Math.toDegrees(n);
                    case "grad":
                        return (float) (n * 0.9);
                    case "turn":
                        return (float) (n * 360);
                    default:
                        break;
                }
            }
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected an angle (deg, rad, grad or turn)");
        return null;
    }

    private static Transform mixTransform(Transform from, Transform to, float p) {
        int n = Math.max(from.functions().size(), to.functions().size());
        List<Transform.Fn> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Transform.Fn a = i < from.functions().size() ? from.functions().get(i) : null;
            Transform.Fn b = i < to.functions().size() ? to.functions().get(i) : null;
            if (a == null) {
                a = b.identity();
            } else if (b == null) {
                b = a.identity();
            }
            if (a.kind() != b.kind()) {
                return null;
            }
            out.add(new Transform.Fn(a.kind(), Length.mix(a.x(), b.x(), p, false), Length.mix(a.y(), b.y(), p, false)));
        }
        return new Transform(List.copyOf(out));
    }

    /**
     * {@code transform-origin}: one or two of a length, a percentage or a keyword ({@code left},
     * {@code center}, {@code right}, {@code top}, {@code bottom}); a single keyword {@code top} or
     * {@code bottom} sets the vertical side, a missing side is {@code center}.
     */
    public static Syntax<TransformOrigin> transformOrigin() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() > 2) {
                diag.report(PropertyCode.VALUE_COUNT, parts.get(2).pos(), "expected 1 or 2 values");
                return null;
            }
            Length center = Length.percent(50);
            ComponentValue first = parts.get(0);
            if (parts.size() == 1 && first instanceof Token t && (t.isIdent("top") || t.isIdent("bottom"))) {
                return new TransformOrigin(center, Length.percent(t.isIdent("top") ? 0 : 100));
            }
            Length x = originSide(first, "left", "right", diag);
            Length y = parts.size() == 2 ? originSide(parts.get(1), "top", "bottom", diag) : center;
            return x == null || y == null ? null : new TransformOrigin(x, y);
        };
    }

    private static Length originSide(ComponentValue v, String start, String end, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            if (t.isIdent(start)) {
                return Length.percent(0);
            }
            if (t.isIdent("center")) {
                return Length.percent(50);
            }
            if (t.isIdent(end)) {
                return Length.percent(100);
            }
            diag.report(PropertyCode.INVALID_VALUE, t.pos(), "expected a length, a percentage, " + start + ", center or " + end);
            return null;
        }
        return lengthOf(v, true, false, true, diag);
    }

    /** Prefix of an image source that names an atlas sprite; the host resolves it. */
    public static final String SPRITE = "sprite:";

    /** A {@code namespace:path} resource location, or a bare path, optionally after {@link #SPRITE}; lower-case as Minecraft requires. */
    static String resourceLocation(String src, SourcePos pos, Diagnostics diag) {
        String s = src.startsWith(SPRITE) ? src.substring(SPRITE.length()) : src;
        int colon = s.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : s.substring(0, colon);
        String path = s.substring(colon + 1);
        if (namespace.isEmpty() || path.isEmpty() || !allowed(namespace, "_-.") || !allowed(path, "_-./")) {
            diag.report(PropertyCode.INVALID_RESOURCE_LOCATION, pos, "invalid resource location '" + src + "'",
                    "write namespace:path, lower-case a-z, 0-9 and _ - . with / in the path");
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
    interface Mixer<T> {
        T mix(T from, T to, float p);
    }

    /** {@code numberUnit} as in {@link Syntax#numberUnit()}. */
    static <T> Syntax<T> interpolating(Syntax<T> parser, Mixer<T> mixer, String numberUnit) {
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

    static float mix(float a, float b, float p) {
        return a + (b - a) * p;
    }

    static Integer mixColor(Integer from, Integer to, float p) {
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
                    diag.report(PropertyCode.EMPTY_LIST_ITEM, t.pos(), "empty item before ','");
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
            diag.report(PropertyCode.EMPTY_LIST_ITEM, last, "empty item after ','");
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
                diag.report(PropertyCode.INVALID_VALUE, t.pos(), "expected a time in s or ms");
                return null;
            }
            if (!negative && seconds < 0) {
                diag.report(PropertyCode.NEGATIVE_VALUE, t.pos(), "negative time not allowed");
                return null;
            }
            return (float) seconds;
        }
        if (v instanceof Token t && t.is(Token.Kind.NUMBER) && t.number() == 0) {
            diag.report(PropertyCode.MISSING_UNIT, t.pos(), "time needs a unit: 0s");
            return null;
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a time in s or ms");
        return null;
    }

    private static final Map<String, Easing> EASING_KEYWORDS = Map.of(
            "linear", Easing.LINEAR,
            "ease", Easing.EASE,
            "ease-in", Easing.EASE_IN,
            "ease-out", Easing.EASE_OUT,
            "ease-in-out", Easing.EASE_IN_OUT,
            "step-start", Easing.STEP_START,
            "step-end", Easing.STEP_END);

    private static final Map<String, Easing.Steps.Position> STEP_POSITIONS = Map.of(
            "jump-start", Easing.Steps.Position.JUMP_START,
            "start", Easing.Steps.Position.JUMP_START,
            "jump-end", Easing.Steps.Position.JUMP_END,
            "end", Easing.Steps.Position.JUMP_END,
            "jump-none", Easing.Steps.Position.JUMP_NONE,
            "jump-both", Easing.Steps.Position.JUMP_BOTH);

    /** Whether {@code v} is meant as an easing function, supported or not. */
    static boolean looksLikeEasing(ComponentValue v) {
        if (v instanceof Token t && t.is(Token.Kind.IDENT)) {
            return EASING_KEYWORDS.containsKey(t.value().toLowerCase(Locale.ROOT));
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
        } else if (v instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("steps")) {
            return steps(f, diag);
        }
        diag.report(PropertyCode.UNSUPPORTED_VALUE, v.pos(), "unsupported easing",
                "use linear, ease, ease-in, ease-out, ease-in-out, step-start, step-end, cubic-bezier() or steps()");
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
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, a.pos(), "cubic-bezier() takes four numbers");
                return null;
            }
            p[n++] = (float) t.number();
        }
        if (n != 4) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "cubic-bezier() takes four numbers");
            return null;
        }
        if (p[0] < 0 || p[0] > 1 || p[2] < 0 || p[2] > 1) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "cubic-bezier() x values must be between 0 and 1");
            return null;
        }
        return new Easing.CubicBezier(p[0], p[1], p[2], p[3]);
    }

    /** {@code steps(<integer>[, <step-position>])}. */
    private static Easing steps(ComponentValue.Function f, Diagnostics diag) {
        List<List<ComponentValue>> args = splitCommas(f.args(), f.pos(), diag);
        if (args == null) {
            return null;
        }
        if (args.size() > 2 || args.get(0).size() != 1
                || !(args.get(0).get(0) instanceof Token n && n.is(Token.Kind.NUMBER) && n.number() == Math.rint(n.number()))) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "steps() takes an integer and an optional step position");
            return null;
        }
        Easing.Steps.Position position = Easing.Steps.Position.JUMP_END;
        if (args.size() == 2) {
            List<ComponentValue> p = args.get(1);
            position = p.size() == 1 && p.get(0) instanceof Token t && t.is(Token.Kind.IDENT)
                    ? STEP_POSITIONS.get(t.value().toLowerCase(Locale.ROOT)) : null;
            if (position == null) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, p.get(0).pos(),
                        "expected jump-start, jump-end, jump-none, jump-both, start or end");
                return null;
            }
        }
        int count = (int) n.number();
        if (count < (position == Easing.Steps.Position.JUMP_NONE ? 2 : 1)) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, n.pos(),
                    position == Easing.Steps.Position.JUMP_NONE ? "steps() with jump-none needs at least 2 steps"
                            : "steps() needs at least 1 step");
            return null;
        }
        return new Easing.Steps(count, position);
    }

    /**
     * A {@code transition-property} name, lower-cased; {@code none} is returned for the caller to check.
     * A custom property name is kept as written: whether it is a document's typed effect param, which
     * transitions, is known only to the document's registry, so the others are ignored when transitioning.
     */
    static String transitionNameOf(ComponentValue v, PropertyRegistry registry, Diagnostics diag) {
        if (!(v instanceof Token t && t.is(Token.Kind.IDENT))) {
            diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a property name");
            return null;
        }
        if (t.value().startsWith("--")) {
            return t.value();
        }
        String name = t.value().toLowerCase(Locale.ROOT);
        if (name.equals("all") || name.equals("none")) {
            return name;
        }
        PropertyDef<?> def = registry.byName(name);
        Shorthand shorthand = def == null ? registry.shorthand(name) : null;
        if (def == null && shorthand == null) {
            diag.report(PropertyCode.UNKNOWN_PROPERTY, t.pos(), "unsupported property '" + name + "'");
            return null;
        }
        for (PropertyDef<?> d : def != null ? List.<PropertyDef<?>>of(def) : shorthand.longhands()) {
            if (!d.animatable()) {
                diag.report(PropertyCode.NOT_ANIMATABLE, t.pos(), "'" + d.name() + "' cannot be transitioned");
                return null;
            }
        }
        return name;
    }

    static Length lengthOf(ComponentValue v, boolean percent, boolean auto, boolean negative, Diagnostics diag) {
        if (v instanceof ComponentValue.Function f && CalcParser.isMath(f)) {
            return CalcParser.length(f, percent, negative, diag);
        }
        if (v instanceof Token t) {
            switch (t.kind()) {
                case DIMENSION:
                    if (t.unit().equalsIgnoreCase("px")) {
                        return checkSign(Length.px((float) t.number()), t, negative, diag);
                    }
                    diag.report(PropertyCode.UNSUPPORTED_UNIT, t.pos(), "unsupported unit '" + t.unit() + "'", "use px, em, rem, vw or vh");
                    return null;
                case NUMBER:
                    if (t.number() == 0) {
                        return Length.ZERO;
                    }
                    diag.report(PropertyCode.MISSING_UNIT, t.pos(), "length needs a unit: " + fmt(t.number()) + "px");
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
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a length in px" + (percent ? ", a percentage" : "") + (auto ? " or auto" : ""));
        return null;
    }

    private static Length checkSign(Length l, Token t, boolean negative, Diagnostics diag) {
        if (!negative && l.value() < 0) {
            diag.report(PropertyCode.NEGATIVE_VALUE, t.pos(), "negative value not allowed");
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
                    diag.report(PropertyCode.INVALID_VALUE, t.pos(), "invalid hex color #" + t.value());
                }
                return c;
            }
            if (t.is(Token.Kind.IDENT)) {
                Integer c = NAMED_COLORS.get(t.value().toLowerCase(Locale.ROOT));
                if (c == null) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "unsupported color name '" + t.value() + "'",
                            "use hex or rgb()");
                }
                return c;
            }
        } else if (v instanceof ComponentValue.Function f
                && (f.name().equalsIgnoreCase("rgb") || f.name().equalsIgnoreCase("rgba"))) {
            return rgbFunction(f, diag);
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a color");
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
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, a.pos(), "unexpected value in " + f.name() + "()");
                return null;
            }
            if (!t.is(Token.Kind.COMMA) && !t.isDelim('/')) {
                parts.add(t);
            }
        }
        if (parts.size() != 3 && parts.size() != 4) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), f.name() + "() takes 3 channels and an optional alpha");
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
            diag.report(PropertyCode.INVALID_VALUE, t.pos(), "expected a number or percentage");
            return null;
        }
        return (int) Math.round(Math.max(0, Math.min(1, unit)) * 255);
    }

    static ComponentValue single(List<ComponentValue> value, SourcePos pos, Diagnostics diag) {
        List<ComponentValue> s = significant(value);
        if (s.size() != 1) {
            diag.report(PropertyCode.VALUE_COUNT, s.size() > 1 ? s.get(1).pos() : pos, "expected a single value");
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
