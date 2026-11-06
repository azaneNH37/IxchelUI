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

    /** Where an image is used: what it may be besides {@code none} and {@code url()}. */
    private enum Use {
        /** Also gradients and {@code paint()}. */
        BACKGROUND,
        /** Also gradients. */
        MASK,
        BORDER
    }

    /** {@code background-image}: comma-separated {@code none}, {@code url()}, gradients or {@code paint()}, top layer first. */
    static Syntax<List<CssImage>> backgroundImages() {
        return Syntaxes.commaList((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : image(v, Use.BACKGROUND, diag);
        });
    }

    /** {@code mask-image}: comma-separated {@code none}, {@code url()} or gradients, top layer first. */
    static Syntax<List<CssImage>> maskImages() {
        return Syntaxes.commaList((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : image(v, Use.MASK, diag);
        });
    }

    /**
     * {@code background-clip}: per layer {@code border-box}, {@code padding-box} or {@code content-box};
     * or {@code text} alone, which clips every layer and the color to the element's text.
     */
    static Syntax<List<Keywords.BackgroundClip>> backgroundClip() {
        Syntax<List<Keywords.BackgroundClip>> list = Syntaxes.commaList(Syntaxes.keyword(Keywords.BackgroundClip.class));
        return (value, pos, diag) -> {
            List<Keywords.BackgroundClip> clips = list.parse(value, pos, diag);
            if (clips != null && clips.size() > 1 && clips.contains(Keywords.BackgroundClip.TEXT)) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, pos, "background-clip: text is supported only for all layers at once",
                        "write background-clip: text alone");
                return null;
            }
            return clips;
        };
    }

    /** {@code mask-mode}: per layer {@code match-source} or {@code alpha}, which are the same for images and gradients. */
    static Syntax<List<Keywords.MaskMode>> maskMode() {
        return Syntaxes.commaList((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.isIdent("luminance")) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "mask-mode: luminance is not supported", "use alpha");
                return null;
            }
            Keywords.MaskMode m = Syntaxes.keywordOf(v, Keywords.MaskMode.class);
            if (m == null) {
                diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected match-source or alpha");
            }
            return m;
        });
    }

    /** {@code mask-composite}: per layer {@code add}, the only operator supported. */
    static Syntax<List<Keywords.MaskComposite>> maskComposite() {
        return Syntaxes.commaList((value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && (t.isIdent("subtract") || t.isIdent("intersect") || t.isIdent("exclude"))) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "mask-composite: " + t.value() + " is not supported", "use add");
                return null;
            }
            Keywords.MaskComposite m = Syntaxes.keywordOf(v, Keywords.MaskComposite.class);
            if (m == null) {
                diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected add");
            }
            return m;
        });
    }

    /**
     * {@code outline-style}: {@code none} or {@code solid}; the other CSS line styles and {@code auto}
     * are reported as unsupported.
     */
    static Syntax<Keywords.BorderStyle> outlineStyle() {
        Syntax<Keywords.BorderStyle> keyword = Syntaxes.keyword(Keywords.BorderStyle.class);
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            if (v == null) {
                return null;
            }
            if (v instanceof Token t && t.is(Token.Kind.IDENT) && Shorthand.isLineStyle(t.value())
                    && Syntaxes.keywordOf(t, Keywords.BorderStyle.class) == null) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "outline-style '" + t.value() + "' is not supported", "use solid");
                return null;
            }
            return keyword.parse(value, pos, diag);
        };
    }

    /** {@code border-image-source}: {@code none} or {@code url()}. */
    static Syntax<CssImage> imageSource() {
        return (value, pos, diag) -> {
            ComponentValue v = single(value, pos, diag);
            return v == null ? null : image(v, Use.BORDER, diag);
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
                    "put text in the view, where it is translated");
            return null;
        };
    }

    /**
     * An image source: {@code none}, a {@code url()}, quoted or not, naming an image by resource
     * location, e.g. {@code url("minecraft:textures/gui/icons.png")}, or a sprite of the host's
     * texture atlas by {@link Syntaxes#SPRITE} and the sprite's resource location, e.g.
     * {@code url("sprite:minecraft:block/water_still")}; in a background or a mask, also the
     * gradients, and in a background {@code paint(name)}, a box effect of the document.
     */
    private static CssImage image(ComponentValue v, Use use, Diagnostics diag) {
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
                if (use == Use.BORDER) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "a gradient is not supported here", "use url()");
                    return null;
                }
                return switch (name) {
                    case "linear-gradient" -> linearGradient(f, false, diag);
                    case "repeating-linear-gradient" -> linearGradient(f, true, diag);
                    case "radial-gradient" -> radialGradient(f, false, diag);
                    case "repeating-radial-gradient" -> radialGradient(f, true, diag);
                    case "conic-gradient" -> conicGradient(f, diag);
                    default -> {
                        diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "'" + name + "()' is not supported",
                                "use linear-gradient(), radial-gradient(), conic-gradient() or a repeating linear or radial one");
                        yield null;
                    }
                };
            }
            if (name.equals("paint")) {
                if (use != Use.BACKGROUND) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, f.pos(), "paint() is only supported in background-image",
                            use == Use.MASK ? "use url() or a gradient" : "use url()");
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
        diag.report(PropertyCode.INVALID_VALUE, v.pos(), switch (use) {
            case BACKGROUND -> "expected none, url(), a gradient or paint()";
            case MASK -> "expected none, url() or a gradient";
            case BORDER -> "expected none or url()";
        });
        return null;
    }

    /**
     * {@code [repeating-]linear-gradient([<angle> | to <side or corner>,] <color> [<length-percentage>{1,2}], ...)}
     * with at least two color stops; color hints and color interpolation methods are not supported.
     */
    private static CssImage linearGradient(ComponentValue.Function f, boolean repeating, Diagnostics diag) {
        List<List<ComponentValue>> items = splitCommas(f.args(), f.pos(), diag);
        if (items == null) {
            return null;
        }
        float angle = 180;
        int cornerX = 0;
        int cornerY = 0;
        int first = 0;
        List<ComponentValue> head = items.get(0);
        if (interpolationMethod(head, diag)) {
            return null;
        }
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
        List<CssImage.Stop> stops = stops(f, items, first, false, diag);
        return stops == null ? null : new CssImage.LinearGradient(angle, cornerX, cornerY, stops, repeating);
    }

    /**
     * {@code [repeating-]radial-gradient([<shape> || <size>] [at <position>], <color> [<length-percentage>{1,2}], ...)}:
     * the shape {@code circle} or {@code ellipse}; the size a keyword ({@code closest-side},
     * {@code farthest-side}, {@code closest-corner}, {@code farthest-corner}, the default), a length
     * (a circle's radius) or two lengths or percentages (an ellipse's radii); the position one or two
     * of a length, a percentage or {@code left}, {@code center}, {@code right}, {@code top},
     * {@code bottom}, as {@code transform-origin} takes them. Without a shape, one length makes a
     * circle, otherwise it is an ellipse.
     */
    private static CssImage radialGradient(ComponentValue.Function f, boolean repeating, Diagnostics diag) {
        List<List<ComponentValue>> items = splitCommas(f.args(), f.pos(), diag);
        if (items == null) {
            return null;
        }
        List<ComponentValue> head = items.get(0);
        if (interpolationMethod(head, diag)) {
            return null;
        }
        Boolean circle = null;
        CssImage.Extent extent = null;
        List<ComponentValue> sizes = new ArrayList<>(2);
        Length cx = Length.percent(50);
        Length cy = Length.percent(50);
        int first = 0;
        if (isRadialHead(head.get(0))) {
            first = 1;
            int at = head.size();
            int lastSize = -2;
            for (int k = 0; k < head.size(); k++) {
                ComponentValue v = head.get(k);
                if (v instanceof Token t && t.isIdent("at")) {
                    at = k;
                    break;
                }
                if (v instanceof Token t && (t.isIdent("circle") || t.isIdent("ellipse"))) {
                    if (circle != null) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, t.pos(), "the shape is given twice");
                        return null;
                    }
                    circle = t.isIdent("circle");
                    continue;
                }
                CssImage.Extent e = extentOf(v);
                if (e != null) {
                    if (extent != null || !sizes.isEmpty()) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "the size is given twice");
                        return null;
                    }
                    extent = e;
                    continue;
                }
                if (isLengthToken(v) || v instanceof Token p && p.is(Token.Kind.PERCENTAGE)) {
                    if (extent != null || sizes.size() == 2 || !sizes.isEmpty() && lastSize != k - 1) {
                        diag.report(PropertyCode.REPEATED_COMPONENT, v.pos(), "the size is given twice");
                        return null;
                    }
                    sizes.add(v);
                    lastSize = k;
                    continue;
                }
                if (v instanceof Token t && (t.isIdent("contain") || t.isIdent("cover"))) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "'" + t.value() + "' is an old size keyword",
                            "use closest-side or farthest-corner");
                    return null;
                }
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, v.pos(),
                        "expected circle or ellipse, a size (closest-side, farthest-side, closest-corner, farthest-corner or lengths) or 'at'");
                return null;
            }
            if (at < head.size()) {
                List<ComponentValue> position = head.subList(at + 1, head.size());
                if (position.isEmpty()) {
                    diag.report(PropertyCode.FUNCTION_ARGUMENTS, head.get(at).pos(), "expected a position after 'at'");
                    return null;
                }
                TransformOrigin o = Syntaxes.transformOrigin().parse(position, position.get(0).pos(), diag);
                if (o == null) {
                    return null;
                }
                cx = o.x();
                cy = o.y();
            }
        }
        Length rx = null;
        Length ry = null;
        if (!sizes.isEmpty()) {
            if (circle == null) {
                circle = sizes.size() == 1;
            }
            if (circle && sizes.size() != 1 || !circle && sizes.size() != 2) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, sizes.get(0).pos(),
                        circle ? "a circle takes one radius" : "an ellipse takes two radii");
                return null;
            }
            rx = lengthOf(sizes.get(0), !circle, false, false, diag);
            ry = circle ? rx : lengthOf(sizes.get(1), true, false, false, diag);
            if (rx == null || ry == null) {
                return null;
            }
            extent = CssImage.Extent.EXPLICIT;
        }
        List<CssImage.Stop> stops = stops(f, items, first, false, diag);
        return stops == null ? null : new CssImage.RadialGradient(circle != null && circle,
                extent == null ? CssImage.Extent.FARTHEST_CORNER : extent, rx, ry, cx, cy, stops, repeating);
    }

    private static boolean isRadialHead(ComponentValue v) {
        if (isLengthToken(v) || v instanceof Token p && p.is(Token.Kind.PERCENTAGE)) {
            return true;
        }
        return v instanceof Token t && (t.isIdent("circle") || t.isIdent("ellipse") || t.isIdent("at") || t.isIdent("contain")
                || t.isIdent("cover") || extentOf(t) != null);
    }

    private static CssImage.Extent extentOf(ComponentValue v) {
        if (!(v instanceof Token t) || !t.is(Token.Kind.IDENT)) {
            return null;
        }
        return switch (t.value().toLowerCase(Locale.ROOT)) {
            case "closest-side" -> CssImage.Extent.CLOSEST_SIDE;
            case "farthest-side" -> CssImage.Extent.FARTHEST_SIDE;
            case "closest-corner" -> CssImage.Extent.CLOSEST_CORNER;
            case "farthest-corner" -> CssImage.Extent.FARTHEST_CORNER;
            default -> null;
        };
    }

    /**
     * {@code conic-gradient([from <angle>] [at <position>], <color> [<angle> | <percentage>]{0,2}, ...)}:
     * the position as {@code radial-gradient()} takes it.
     */
    private static CssImage conicGradient(ComponentValue.Function f, Diagnostics diag) {
        List<List<ComponentValue>> items = splitCommas(f.args(), f.pos(), diag);
        if (items == null) {
            return null;
        }
        List<ComponentValue> head = items.get(0);
        if (interpolationMethod(head, diag)) {
            return null;
        }
        float from = 0;
        Length cx = Length.percent(50);
        Length cy = Length.percent(50);
        int first = 0;
        if (head.get(0) instanceof Token t && (t.isIdent("from") || t.isIdent("at"))) {
            first = 1;
            int k = 0;
            if (t.isIdent("from")) {
                if (head.size() < 2) {
                    diag.report(PropertyCode.FUNCTION_ARGUMENTS, t.pos(), "expected an angle after 'from'");
                    return null;
                }
                Float a = Syntaxes.angle(head.get(1), diag);
                if (a == null) {
                    return null;
                }
                from = a;
                k = 2;
            }
            if (k < head.size()) {
                if (!(head.get(k) instanceof Token at && at.isIdent("at")) || k + 1 == head.size()) {
                    diag.report(PropertyCode.FUNCTION_ARGUMENTS, head.get(k).pos(), "expected 'at' and a position");
                    return null;
                }
                List<ComponentValue> position = head.subList(k + 1, head.size());
                TransformOrigin o = Syntaxes.transformOrigin().parse(position, position.get(0).pos(), diag);
                if (o == null) {
                    return null;
                }
                cx = o.x();
                cy = o.y();
            }
        }
        List<CssImage.Stop> stops = stops(f, items, first, true, diag);
        return stops == null ? null : new CssImage.ConicGradient(from, cx, cy, stops);
    }

    /** Whether {@code head} starts with {@code in}, a color interpolation method, which is reported as unsupported. */
    private static boolean interpolationMethod(List<ComponentValue> head, Diagnostics diag) {
        for (ComponentValue v : head) {
            if (v instanceof Token t && t.isIdent("in")) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "color interpolation methods are not supported",
                        "gradients interpolate in premultiplied sRGB");
                return true;
            }
        }
        return false;
    }

    /**
     * The color stops {@code items[first..]}: a color and at most two positions each, at least two
     * stops; {@code angular} takes angles and percentages of the turn instead of lengths and
     * percentages of the line. Color hints are not supported.
     */
    private static List<CssImage.Stop> stops(ComponentValue.Function f, List<List<ComponentValue>> items, int first,
                                             boolean angular, Diagnostics diag) {
        List<CssImage.Stop> stops = new ArrayList<>();
        for (int i = first; i < items.size(); i++) {
            List<ComponentValue> s = items.get(i);
            if (s.size() == 1 && (isLengthToken(s.get(0)) || isAngle(s.get(0)) || s.get(0) instanceof Token p && p.is(Token.Kind.PERCENTAGE))) {
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
                stops.add(new CssImage.Stop(color, null));
            }
            for (int k = 1; k < s.size(); k++) {
                Length p = angular ? turnOf(s.get(k), diag) : lengthOf(s.get(k), true, false, true, diag);
                if (p == null) {
                    return null;
                }
                stops.add(new CssImage.Stop(color, p));
            }
        }
        if (stops.size() < 2) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), f.name() + "() needs at least two color stops");
            return null;
        }
        return List.copyOf(stops);
    }

    /** A conic stop position: a percentage, or an angle as its percentage of the turn. */
    private static Length turnOf(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.is(Token.Kind.PERCENTAGE)) {
            return Length.percent((float) t.number());
        }
        if (!isAngle(v)) {
            diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected an angle or a percentage");
            return null;
        }
        Float a = Syntaxes.angle(v, diag);
        return a == null ? null : Length.percent(a / 3.6f);
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
                    source = image(v, Use.BORDER, diag);
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
