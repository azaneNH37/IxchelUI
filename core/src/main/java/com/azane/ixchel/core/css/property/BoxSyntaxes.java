package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.property.Keywords.ImageTiling;
import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.SourcePos;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.azane.ixchel.core.css.property.Syntaxes.colorOf;
import static com.azane.ixchel.core.css.property.Syntaxes.interpolating;
import static com.azane.ixchel.core.css.property.Syntaxes.isLengthToken;
import static com.azane.ixchel.core.css.property.Syntaxes.lengthOf;
import static com.azane.ixchel.core.css.property.Syntaxes.mix;
import static com.azane.ixchel.core.css.property.Syntaxes.mixColor;
import static com.azane.ixchel.core.css.property.Syntaxes.significant;
import static com.azane.ixchel.core.css.property.Syntaxes.single;
import static com.azane.ixchel.core.css.property.Syntaxes.splitCommas;

/** Value grammars of the box's decoration: background layers, radii, shadows and the border image. */
final class BoxSyntaxes {
    private BoxSyntaxes() {
    }

    /** {@code background-image}: comma-separated {@code none}, {@code url()} or {@code linear-gradient()}, top layer first. */
    static Syntax<List<CssImage>> backgroundImages() {
        return Syntaxes.commaList((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : image(v, true, diag);
        });
    }

    /** {@code border-image-source}: {@code none} or {@code url()}. */
    static Syntax<CssImage> imageSource() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : image(v, false, diag);
        };
    }

    /** {@code content}: {@code normal}, {@code none} or the empty string; any text is reported as unsupported. */
    static Syntax<Keywords.Content> content() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t) {
                if (t.isIdent("normal")) {
                    return Keywords.Content.NORMAL;
                }
                if (t.isIdent("none")) {
                    return Keywords.Content.NONE;
                }
                if (t.is(Token.Kind.STRING) && t.value().isEmpty()) {
                    return Keywords.Content.EMPTY;
                }
            }
            diag.report(PropertyCode.UNSUPPORTED_VALUE, v.pos(), "content takes only \"\" or none; ::before and ::after are decoration",
                    "put text in the template, where it is translated");
            return null;
        };
    }

    /**
     * An image source: {@code none}, a {@code url()}, quoted or not, naming an image by resource
     * location, e.g. {@code url("minecraft:textures/gui/icons.png")}, or a sprite of the host's
     * texture atlas by {@link Syntaxes#SPRITE} and the sprite's resource location, e.g.
     * {@code url("sprite:minecraft:block/water_still")}; with {@code background}, also
     * {@code linear-gradient()} and {@code paint(name)}, a box effect of the document.
     */
    private static CssImage image(ComponentValue v, boolean background, Diagnostics diag) {
        if (v instanceof Token t) {
            if (t.isIdent("none")) {
                return CssImage.NONE;
            }
            if (t.is(Token.Kind.URL)) {
                String src = Syntaxes.resourceLocation(t.value(), t.pos(), diag);
                return src == null ? null : new CssImage.Url(src);
            }
            if (t.is(Token.Kind.BAD_URL)) {
                return null; // the tokenizer reported it
            }
        } else if (v instanceof ComponentValue.Function f) {
            String name = f.name().toLowerCase(Locale.ROOT);
            if (name.equals("url")) {
                List<ComponentValue> args = significant(f.args());
                if (args.size() == 1 && args.get(0) instanceof Token s && s.is(Token.Kind.STRING)) {
                    String src = Syntaxes.resourceLocation(s.value(), s.pos(), diag);
                    return src == null ? null : new CssImage.Url(src);
                }
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "url() takes one string");
                return null;
            }
            if (name.endsWith("-gradient")) {
                if (!background) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "a gradient is not supported here", "use url()");
                    return null;
                }
                if (name.equals("linear-gradient")) {
                    return linearGradient(f, diag);
                }
                diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "'" + name + "()' is not supported", "use linear-gradient()");
                return null;
            }
            if (name.equals("paint")) {
                if (!background) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "paint() is only supported in background-image", "use url()");
                    return null;
                }
                List<ComponentValue> args = significant(f.args());
                if (args.size() == 1 && args.get(0) instanceof Token n && n.is(Token.Kind.IDENT)) {
                    return new CssImage.Paint(n.value());
                }
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "paint() takes the name of a box effect");
                return null;
            }
        }
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), background ? "expected none, url(), linear-gradient() or paint()" : "expected none or url()");
        return null;
    }

    /**
     * {@code linear-gradient([<angle> | to <side or corner>,] <color> [<length-percentage>{1,2}], ...)}
     * with at least two color stops; color hints are not supported.
     */
    private static CssImage linearGradient(ComponentValue.Function f, Diagnostics diag) {
        List<List<ComponentValue>> items = splitCommas(f.args(), f.pos(), diag);
        if (items == null) {
            return null;
        }
        float angle = 180;
        int cornerX = 0;
        int cornerY = 0;
        int first = 0;
        List<ComponentValue> head = items.get(0);
        if (head.get(0) instanceof Token to && to.isIdent("to")) {
            first = 1;
            if (head.size() < 2 || head.size() > 3) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, to.pos(), "expected one or two sides after 'to'");
                return null;
            }
            int x = 0;
            int y = 0;
            for (int k = 1; k < head.size(); k++) {
                ComponentValue s = head.get(k);
                int sx = 0;
                int sy = 0;
                if (s instanceof Token t && t.is(Token.Kind.IDENT)) {
                    switch (t.value().toLowerCase(Locale.ROOT)) {
                        case "left" -> sx = -1;
                        case "right" -> sx = 1;
                        case "top" -> sy = -1;
                        case "bottom" -> sy = 1;
                        default -> {
                        }
                    }
                }
                if (sx == 0 && sy == 0 || sx != 0 && x != 0 || sy != 0 && y != 0) {
                    diag.report(PropertyCode.FUNCTION_ARGUMENTS, s.pos(), "expected left or right and top or bottom, each at most once");
                    return null;
                }
                x += sx;
                y += sy;
            }
            if (x != 0 && y != 0) {
                angle = Float.NaN;
                cornerX = x;
                cornerY = y;
            } else {
                angle = x == 1 ? 90 : x == -1 ? 270 : y == -1 ? 0 : 180;
            }
        } else if (head.size() == 1 && isAngle(head.get(0))) {
            Float a = Syntaxes.angle(head.get(0), diag);
            if (a == null) {
                return null;
            }
            angle = a;
            first = 1;
        }
        List<CssImage.LinearGradient.Stop> stops = new ArrayList<>();
        for (int i = first; i < items.size(); i++) {
            List<ComponentValue> s = items.get(i);
            if (s.size() == 1 && (isLengthToken(s.get(0)) || s.get(0) instanceof Token p && p.is(Token.Kind.PERCENTAGE))) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, s.get(0).pos(), "color hints are not supported", "give the color stop a color");
                return null;
            }
            if (s.size() > 3) {
                diag.report(PropertyCode.VALUE_COUNT, s.get(3).pos(), "expected a color and at most two positions");
                return null;
            }
            Integer color = colorOf(s.get(0), diag);
            if (color == null) {
                return null;
            }
            if (s.size() == 1) {
                stops.add(new CssImage.LinearGradient.Stop(color, null));
            }
            for (int k = 1; k < s.size(); k++) {
                Length p = lengthOf(s.get(k), true, false, true, diag);
                if (p == null) {
                    return null;
                }
                stops.add(new CssImage.LinearGradient.Stop(color, p));
            }
        }
        if (stops.size() < 2) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "linear-gradient() needs at least two color stops");
            return null;
        }
        return new CssImage.LinearGradient(angle, cornerX, cornerY, List.copyOf(stops));
    }

    private static boolean isAngle(ComponentValue v) {
        if (!(v instanceof Token t)) {
            return false;
        }
        if (t.is(Token.Kind.NUMBER)) {
            return t.number() == 0;
        }
        if (!t.is(Token.Kind.DIMENSION)) {
            return false;
        }
        String unit = t.unit().toLowerCase(Locale.ROOT);
        return unit.equals("deg") || unit.equals("rad") || unit.equals("grad") || unit.equals("turn");
    }

    /** {@code background-position}: comma-separated positions, each as {@link Shorthand#position}. */
    static Shorthand.Expander backgroundPositions(Syntax<Length> offset) {
        Shorthand.Expander one = Shorthand.position(offset);
        return (value, pos, diag) -> {
            List<List<ComponentValue>> items = splitCommas(value, pos, diag);
            if (items == null) {
                return null;
            }
            List<Length> xs = new ArrayList<>(items.size());
            List<Length> ys = new ArrayList<>(items.size());
            for (List<ComponentValue> i : items) {
                Object[] xy = one.expand(i, i.get(0).pos(), diag);
                if (xy == null) {
                    return null;
                }
                xs.add((Length) xy[0]);
                ys.add((Length) xy[1]);
            }
            return new Object[]{List.copyOf(xs), List.copyOf(ys)};
        };
    }

    /** {@code border-*-radius}: one or two non-negative lengths or percentages, horizontal then vertical. */
    static Syntax<CornerRadius> cornerRadius() {
        return interpolating((value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() > 2) {
                diag.report(PropertyCode.VALUE_COUNT, parts.get(2).pos(), "expected 1 or 2 values");
                return null;
            }
            Length x = lengthOf(parts.get(0), true, false, false, diag);
            if (x == null) {
                return null;
            }
            Length y = parts.size() == 1 ? x : lengthOf(parts.get(1), true, false, false, diag);
            return y == null ? null : new CornerRadius(x, y);
        }, (a, b, p) -> {
            Length x = mixLength(a.x(), b.x(), p);
            Length y = mixLength(a.y(), b.y(), p);
            return x == null || y == null ? null : new CornerRadius(x, y);
        }, "px");
    }

    private static Length mixLength(Length a, Length b, float p) {
        return Length.mix(a, b, p, true);
    }

    /**
     * {@code border-radius}: 1-4 horizontal radii for top-left, top-right, bottom-right, bottom-left
     * (expanded as {@code margin}'s sides), then optionally {@code /} and 1-4 vertical radii.
     */
    static Shorthand.Expander borderRadius() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            int slash = -1;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i) instanceof Token t && t.isDelim('/')) {
                    if (slash >= 0) {
                        diag.report(PropertyCode.INVALID_VALUE, t.pos(), "expected at most one '/'");
                        return null;
                    }
                    slash = i;
                }
            }
            List<ComponentValue> hs = slash < 0 ? parts : parts.subList(0, slash);
            Length[] h = radii(hs, pos, diag);
            if (h == null) {
                return null;
            }
            Length[] v = h;
            if (slash >= 0) {
                v = radii(parts.subList(slash + 1, parts.size()), parts.get(slash).pos(), diag);
                if (v == null) {
                    return null;
                }
            }
            Object[] out = new Object[4];
            for (int i = 0; i < 4; i++) {
                out[i] = new CornerRadius(h[i], v[i]);
            }
            return out;
        };
    }

    private static Length[] radii(List<ComponentValue> parts, SourcePos pos, Diagnostics diag) {
        if (parts.isEmpty() || parts.size() > 4) {
            diag.report(PropertyCode.VALUE_COUNT, parts.size() > 4 ? parts.get(4).pos() : pos, "expected 1 to 4 radii");
            return null;
        }
        Length[] l = new Length[parts.size()];
        for (int i = 0; i < l.length; i++) {
            l[i] = lengthOf(parts.get(i), true, false, false, diag);
            if (l[i] == null) {
                return null;
            }
        }
        return boxSides(l);
    }

    private static <T> T[] boxSides(T[] v) {
        T[] out = java.util.Arrays.copyOf(v, 4);
        switch (v.length) {
            case 1 -> {
                out[1] = v[0];
                out[2] = v[0];
                out[3] = v[0];
            }
            case 2 -> {
                out[2] = v[0];
                out[3] = v[1];
            }
            case 3 -> out[3] = v[1];
            default -> {
            }
        }
        return out;
    }

    /**
     * {@code box-shadow}: {@code none} or comma-separated shadows, top first: {@code inset} optionally,
     * an x and a y offset, an optional non-negative blur and an optional spread, and a color, the
     * color and {@code inset} before or after the lengths. A missing color (CSS {@code currentcolor})
     * is not supported. Two lists interpolate shadow by shadow, the shorter padded with transparent
     * shadows at no offset; not when the shadows at one index differ in {@code inset}.
     */
    static Syntax<List<BoxShadow>> boxShadow() {
        return interpolating((value, pos, diag) -> {
            List<ComponentValue> all = significant(value);
            if (all.size() == 1 && all.get(0) instanceof Token t && t.isIdent("none")) {
                return List.of();
            }
            List<List<ComponentValue>> items = splitCommas(value, pos, diag);
            if (items == null) {
                return null;
            }
            List<BoxShadow> out = new ArrayList<>(items.size());
            for (List<ComponentValue> item : items) {
                BoxShadow s = shadow(item, diag);
                if (s == null) {
                    return null;
                }
                out.add(s);
            }
            return List.copyOf(out);
        }, BoxSyntaxes::mixShadows, null);
    }

    private static BoxShadow shadow(List<ComponentValue> item, Diagnostics diag) {
        boolean inset = false;
        ComponentValue color = null;
        List<ComponentValue> lengths = new ArrayList<>(4);
        for (ComponentValue v : item) {
            if (v instanceof Token t && t.isIdent("inset")) {
                if (inset) {
                    diag.report(PropertyCode.REPEATED_COMPONENT, t.pos(), "'inset' is given twice");
                    return null;
                }
                inset = true;
            } else if (isLengthToken(v)) {
                lengths.add(v);
            } else {
                if (color != null) {
                    diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "a shadow has one color");
                    return null;
                }
                color = v;
            }
        }
        if (lengths.size() < 2 || lengths.size() > 4) {
            diag.report(PropertyCode.VALUE_COUNT, item.get(0).pos(), "expected two offsets, an optional blur and spread, and a color");
            return null;
        }
        if (color == null) {
            diag.report(PropertyCode.UNSUPPORTED_VALUE, item.get(0).pos(), "a box-shadow without a color is not supported",
                    "add the color, e.g. 0 2px 4px #80000000");
            return null;
        }
        float[] l = new float[4];
        for (int i = 0; i < lengths.size(); i++) {
            Length len = lengthOf(lengths.get(i), false, false, i != 2, diag);
            if (len == null) {
                return null;
            }
            l[i] = len.value();
        }
        Integer c = colorOf(color, diag);
        return c == null ? null : new BoxShadow(l[0], l[1], l[2], l[3], c, inset);
    }

    private static List<BoxShadow> mixShadows(List<BoxShadow> from, List<BoxShadow> to, float p) {
        int n = Math.max(from.size(), to.size());
        List<BoxShadow> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BoxShadow a = i < from.size() ? from.get(i) : null;
            BoxShadow b = i < to.size() ? to.get(i) : null;
            if (a == null) {
                a = new BoxShadow(0, 0, 0, 0, 0, b.inset());
            } else if (b == null) {
                b = new BoxShadow(0, 0, 0, 0, 0, a.inset());
            }
            if (a.inset() != b.inset()) {
                return null;
            }
            out.add(new BoxShadow(mix(a.x(), b.x(), p), mix(a.y(), b.y(), p), Math.max(0, mix(a.blur(), b.blur(), p)),
                    mix(a.spread(), b.spread(), p), mixColor(a.color(), b.color(), p), a.inset()));
        }
        return List.copyOf(out);
    }

    /** {@code border-image-slice}: 1-4 non-negative numbers (image pixels) or percentages, and {@code fill} first or last. */
    static Syntax<BorderImageSlice> borderImageSlice() {
        return (value, pos, diag) -> slice(significant(value), pos, diag);
    }

    private static BorderImageSlice slice(List<ComponentValue> parts, SourcePos pos, Diagnostics diag) {
        boolean fill = false;
        List<Length> sides = new ArrayList<>(4);
        for (int i = 0; i < parts.size(); i++) {
            ComponentValue v = parts.get(i);
            if (v instanceof Token t && t.isIdent("fill") && (i == 0 || i == parts.size() - 1) && !fill) {
                fill = true;
                continue;
            }
            if (v instanceof Token t && (t.is(Token.Kind.NUMBER) || t.is(Token.Kind.PERCENTAGE)) && t.number() >= 0) {
                sides.add(t.is(Token.Kind.NUMBER) ? Length.px((float) t.number()) : Length.percent((float) t.number()));
                continue;
            }
            diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected a non-negative number, a percentage or fill");
            return null;
        }
        if (sides.isEmpty() || sides.size() > 4) {
            diag.report(PropertyCode.VALUE_COUNT, pos, "expected 1 to 4 slice offsets");
            return null;
        }
        Length[] s = boxSides(sides.toArray(new Length[0]));
        return new BorderImageSlice(s[0], s[1], s[2], s[3], fill);
    }

    /** {@code border-image-width}: 1-4 of a non-negative length, percentage, number or {@code auto}. */
    static Syntax<BorderImageWidth> borderImageWidth() {
        return (value, pos, diag) -> width(significant(value), pos, diag);
    }

    private static BorderImageWidth width(List<ComponentValue> parts, SourcePos pos, Diagnostics diag) {
        if (parts.isEmpty() || parts.size() > 4) {
            diag.report(PropertyCode.VALUE_COUNT, parts.size() > 4 ? parts.get(4).pos() : pos, "expected 1 to 4 widths");
            return null;
        }
        BorderImageWidth.Side[] s = new BorderImageWidth.Side[parts.size()];
        for (int i = 0; i < s.length; i++) {
            ComponentValue v = parts.get(i);
            if (v instanceof Token t && t.is(Token.Kind.NUMBER) && t.number() != 0) {
                if (t.number() < 0) {
                    diag.report(PropertyCode.NEGATIVE_VALUE, t.pos(), "negative value not allowed");
                    return null;
                }
                s[i] = new BorderImageWidth.Side(null, (float) t.number());
                continue;
            }
            Length l = lengthOf(v, true, true, false, diag);
            if (l == null) {
                return null;
            }
            s[i] = new BorderImageWidth.Side(l, 0);
        }
        s = boxSides(s);
        return new BorderImageWidth(s[0], s[1], s[2], s[3]);
    }

    /** {@code border-image-repeat}: one keyword for both axes, or horizontal then vertical. */
    static Syntax<List<ImageTiling>> borderImageRepeat() {
        return (value, pos, diag) -> tiling(significant(value), pos, diag);
    }

    private static List<ImageTiling> tiling(List<ComponentValue> parts, SourcePos pos, Diagnostics diag) {
        if (parts.isEmpty() || parts.size() > 2) {
            diag.report(PropertyCode.VALUE_COUNT, parts.size() > 2 ? parts.get(2).pos() : pos, "expected 1 or 2 values");
            return null;
        }
        ImageTiling[] out = new ImageTiling[parts.size()];
        for (int i = 0; i < out.length; i++) {
            ComponentValue v = parts.get(i);
            if (v instanceof Token t && t.isIdent("space")) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "'space' is not supported", "use repeat or round");
                return null;
            }
            out[i] = Syntaxes.keyword(ImageTiling.class).parse(List.of(v), v.pos(), diag);
            if (out[i] == null) {
                return null;
            }
        }
        return out.length == 1 ? List.of(out[0], out[0]) : List.of(out[0], out[1]);
    }

    private static boolean isTiling(ComponentValue v) {
        return v instanceof Token t && (t.isIdent("stretch") || t.isIdent("repeat") || t.isIdent("round") || t.isIdent("space"));
    }

    private static boolean isSlicePart(ComponentValue v) {
        return v instanceof Token t && (t.is(Token.Kind.NUMBER) || t.is(Token.Kind.PERCENTAGE) || t.isIdent("fill"));
    }

    private static boolean isWidthPart(ComponentValue v) {
        return v instanceof Token t && (t.is(Token.Kind.NUMBER) || t.is(Token.Kind.PERCENTAGE) || t.is(Token.Kind.DIMENSION)
                || t.isIdent("auto"));
    }

    /**
     * {@code border-image}: a source, a slice optionally followed by {@code /} and a width, and a
     * repeat, in any order; omitted ones are reset to their initial values. Longhands: source, slice,
     * width, repeat. An outset (a second {@code /}) is not supported.
     */
    static Shorthand.Expander borderImage() {
        return (value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            Object source = null;
            Object slice = null;
            Object width = null;
            Object repeat = null;
            int i = 0;
            while (i < parts.size()) {
                ComponentValue v = parts.get(i);
                int j = i;
                if (isTiling(v)) {
                    while (j < parts.size() && j < i + 2 && isTiling(parts.get(j))) {
                        j++;
                    }
                    if (repeat != null) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "the repeat is given twice");
                        return null;
                    }
                    repeat = tiling(parts.subList(i, j), v.pos(), diag);
                    if (repeat == null) {
                        return null;
                    }
                } else if (isSlicePart(v)) {
                    while (j < parts.size() && isSlicePart(parts.get(j))) {
                        j++;
                    }
                    if (slice != null) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "the slice is given twice");
                        return null;
                    }
                    slice = slice(parts.subList(i, j), v.pos(), diag);
                    if (slice == null) {
                        return null;
                    }
                    if (j < parts.size() && parts.get(j) instanceof Token t && t.isDelim('/')) {
                        int k = ++j;
                        while (j < parts.size() && isWidthPart(parts.get(j))) {
                            j++;
                        }
                        width = width(parts.subList(k, j), t.pos(), diag);
                        if (width == null) {
                            return null;
                        }
                        if (j < parts.size() && parts.get(j) instanceof Token o && o.isDelim('/')) {
                            diag.report(PropertyCode.UNSUPPORTED_VALUE, o.pos(), "border-image-outset is not supported");
                            return null;
                        }
                    }
                } else {
                    j++;
                    if (source != null) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "the source is given twice");
                        return null;
                    }
                    source = image(v, false, diag);
                    if (source == null) {
                        return null;
                    }
                }
                i = j;
            }
            return new Object[]{source == null ? CssImage.NONE : source, slice == null ? BorderImageSlice.INITIAL : slice,
                    width == null ? BorderImageWidth.INITIAL : width,
                    repeat == null ? List.of(ImageTiling.STRETCH, ImageTiling.STRETCH) : repeat};
        };
    }
}
