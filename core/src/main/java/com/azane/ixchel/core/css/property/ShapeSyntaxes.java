package com.azane.ixchel.core.css.property;

import com.azane.ixchel.core.css.syntax.ComponentValue;
import com.azane.ixchel.core.css.syntax.Token;
import com.azane.ixchel.core.diag.Diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.azane.ixchel.core.css.property.Syntaxes.interpolating;
import static com.azane.ixchel.core.css.property.Syntaxes.lengthOf;
import static com.azane.ixchel.core.css.property.Syntaxes.significant;
import static com.azane.ixchel.core.css.property.Syntaxes.splitCommas;

/** Value grammar of {@code clip-path}: the CSS Shapes 1 basic shapes and how they interpolate. */
final class ShapeSyntaxes {
    private ShapeSyntaxes() {
    }

    /**
     * {@code clip-path}: {@code none} or one basic shape, optionally with the geometry box
     * {@code border-box} (the only one supported, and the default). {@code path()}, {@code url()},
     * the other boxes and the newer shape functions are reported as unsupported. Two shapes of the
     * same function interpolate argument by argument: radii only when both are lengths or the same
     * keyword, polygons only with the same fill rule and vertex count (CSS Shapes 1 §4.1).
     */
    static Syntax<ClipPath> clipPath() {
        return interpolating((value, pos, diag) -> {
            List<ComponentValue> parts = significant(value);
            if (parts.size() == 1 && parts.get(0) instanceof Token t && t.isIdent("none")) {
                return ClipPath.NONE;
            }
            ComponentValue.Function shape = null;
            for (ComponentValue v : parts) {
                if (v instanceof Token t && t.is(Token.Kind.IDENT) && isGeometryBox(t.value())) {
                    if (!t.isIdent("border-box")) {
                        diag.report(PropertyCode.UNSUPPORTED_VALUE, t.pos(), "clip-path geometry box '" + t.value() + "' is not supported",
                                "leave it out: shapes are in the border box");
                        return null;
                    }
                    continue;
                }
                if (v instanceof Token t && (t.is(Token.Kind.URL) || t.is(Token.Kind.BAD_URL))
                        || v instanceof ComponentValue.Function f && f.name().equalsIgnoreCase("url")) {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, v.pos(), "clip-path: url() (an SVG clipPath) is not supported",
                            "use inset(), circle(), ellipse() or polygon()");
                    return null;
                }
                if (v instanceof ComponentValue.Function f && shape == null) {
                    shape = f;
                    continue;
                }
                diag.report(PropertyCode.INVALID_VALUE, v.pos(), "expected none or one basic shape, optionally with border-box");
                return null;
            }
            if (shape == null) {
                diag.report(PropertyCode.UNSUPPORTED_VALUE, parts.get(0).pos(), "clip-path with only a geometry box is not supported",
                        "use inset(0 round <radius>) for the rounded border box");
                return null;
            }
            return switch (shape.name().toLowerCase(Locale.ROOT)) {
                case "inset" -> inset(shape, diag);
                case "circle" -> circle(shape, diag);
                case "ellipse" -> ellipse(shape, diag);
                case "polygon" -> polygon(shape, diag);
                case "path", "shape", "rect", "xywh" -> {
                    diag.report(PropertyCode.UNSUPPORTED_VALUE, shape.pos(), "clip-path: " + shape.name() + "() is not supported",
                            "use inset(), circle(), ellipse() or polygon()");
                    yield null;
                }
                default -> {
                    diag.report(PropertyCode.INVALID_VALUE, shape.pos(), "expected inset(), circle(), ellipse() or polygon()");
                    yield null;
                }
            };
        }, ShapeSyntaxes::mix, null);
    }

    private static boolean isGeometryBox(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "border-box", "padding-box", "content-box", "margin-box", "fill-box", "stroke-box", "view-box" -> true;
            default -> false;
        };
    }

    /** {@code inset(<length-percentage>{1,4} [round <border-radius>]?)}, the sides as {@code margin} takes them. */
    private static ClipPath inset(ComponentValue.Function f, Diagnostics diag) {
        List<ComponentValue> args = significant(f.args());
        int round = args.size();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) instanceof Token t && t.isIdent("round")) {
                round = i;
                break;
            }
        }
        if (round == 0 || round > 4) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, round > 4 ? args.get(4).pos() : f.pos(), "inset() takes 1 to 4 offsets, then optionally 'round' and radii");
            return null;
        }
        Length[] sides = new Length[4];
        for (int i = 0; i < round; i++) {
            sides[i] = lengthOf(args.get(i), true, false, true, diag);
            if (sides[i] == null) {
                return null;
            }
        }
        switch (round) {
            case 1 -> sides[1] = sides[2] = sides[3] = sides[0];
            case 2 -> {
                sides[2] = sides[0];
                sides[3] = sides[1];
            }
            case 3 -> sides[3] = sides[1];
            default -> {
            }
        }
        Object[] radii = {CornerRadius.ZERO, CornerRadius.ZERO, CornerRadius.ZERO, CornerRadius.ZERO};
        if (round < args.size()) {
            List<ComponentValue> rest = args.subList(round + 1, args.size());
            if (rest.isEmpty()) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, args.get(round).pos(), "expected radii after 'round'");
                return null;
            }
            radii = BoxSyntaxes.borderRadius().expand(rest, rest.get(0).pos(), diag);
            if (radii == null) {
                return null;
            }
        }
        return new ClipPath.Inset(sides[0], sides[1], sides[2], sides[3],
                (CornerRadius) radii[0], (CornerRadius) radii[1], (CornerRadius) radii[2], (CornerRadius) radii[3]);
    }

    /** {@code circle([<shape-radius>]? [at <position>]?)}. */
    private static ClipPath circle(ComponentValue.Function f, Diagnostics diag) {
        List<ComponentValue> args = significant(f.args());
        int at = at(args);
        if (at > 1) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, args.get(1).pos(), "circle() takes one radius, then optionally 'at' and a position");
            return null;
        }
        ClipPath.Radius r = at == 1 ? radius(args.get(0), diag) : ClipPath.Radius.CLOSEST_SIDE;
        Length[] center = center(f, args, at, diag);
        return r == null || center == null ? null : new ClipPath.Circle(r, center[0], center[1]);
    }

    /** {@code ellipse([<shape-radius>{2}]? [at <position>]?)}. */
    private static ClipPath ellipse(ComponentValue.Function f, Diagnostics diag) {
        List<ComponentValue> args = significant(f.args());
        int at = at(args);
        if (at != 0 && at != 2) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, args.get(0).pos(), "ellipse() takes no radii or two, then optionally 'at' and a position");
            return null;
        }
        ClipPath.Radius rx = ClipPath.Radius.CLOSEST_SIDE;
        ClipPath.Radius ry = ClipPath.Radius.CLOSEST_SIDE;
        if (at == 2) {
            rx = radius(args.get(0), diag);
            ry = rx == null ? null : radius(args.get(1), diag);
        }
        Length[] center = center(f, args, at, diag);
        return rx == null || ry == null || center == null ? null : new ClipPath.Ellipse(rx, ry, center[0], center[1]);
    }

    /** The index of {@code at} in {@code args}, or their count. */
    private static int at(List<ComponentValue> args) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) instanceof Token t && t.isIdent("at")) {
                return i;
            }
        }
        return args.size();
    }

    private static ClipPath.Radius radius(ComponentValue v, Diagnostics diag) {
        if (v instanceof Token t && t.isIdent("closest-side")) {
            return ClipPath.Radius.CLOSEST_SIDE;
        }
        if (v instanceof Token t && t.isIdent("farthest-side")) {
            return ClipPath.Radius.FARTHEST_SIDE;
        }
        Length l = lengthOf(v, true, false, false, diag);
        return l == null ? null : new ClipPath.Radius(l, false);
    }

    /** The position after {@code at}, as {@code transform-origin} takes it; the center when there is none. */
    private static Length[] center(ComponentValue.Function f, List<ComponentValue> args, int at, Diagnostics diag) {
        if (at == args.size()) {
            return new Length[]{Length.percent(50), Length.percent(50)};
        }
        List<ComponentValue> position = args.subList(at + 1, args.size());
        if (position.isEmpty()) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, args.get(at).pos(), "expected a position after 'at'");
            return null;
        }
        TransformOrigin o = Syntaxes.transformOrigin().parse(position, position.get(0).pos(), diag);
        return o == null ? null : new Length[]{o.x(), o.y()};
    }

    /** {@code polygon([<fill-rule>,]? [<length-percentage> <length-percentage>]#)}, at most {@value ClipPath#MAX_POINTS} vertices. */
    private static ClipPath polygon(ComponentValue.Function f, Diagnostics diag) {
        List<List<ComponentValue>> items = splitCommas(f.args(), f.pos(), diag);
        if (items == null) {
            return null;
        }
        boolean evenOdd = false;
        int first = 0;
        List<ComponentValue> head = items.get(0);
        if (head.size() == 1 && head.get(0) instanceof Token t && (t.isIdent("nonzero") || t.isIdent("evenodd"))) {
            evenOdd = t.isIdent("evenodd");
            first = 1;
        }
        int n = items.size() - first;
        if (n == 0) {
            diag.report(PropertyCode.FUNCTION_ARGUMENTS, f.pos(), "polygon() needs at least one vertex");
            return null;
        }
        if (n > ClipPath.MAX_POINTS) {
            diag.report(PropertyCode.UNSUPPORTED_VALUE, items.get(first + ClipPath.MAX_POINTS).get(0).pos(),
                    "polygon() with more than " + ClipPath.MAX_POINTS + " vertices is not supported");
            return null;
        }
        List<Length> points = new ArrayList<>(2 * n);
        for (int i = first; i < items.size(); i++) {
            List<ComponentValue> p = items.get(i);
            if (p.size() != 2) {
                diag.report(PropertyCode.FUNCTION_ARGUMENTS, p.get(0).pos(), "a polygon vertex is an x and a y");
                return null;
            }
            for (ComponentValue c : p) {
                Length l = lengthOf(c, true, false, true, diag);
                if (l == null) {
                    return null;
                }
                points.add(l);
            }
        }
        return new ClipPath.Polygon(evenOdd, List.copyOf(points));
    }

    private static ClipPath mix(ClipPath a, ClipPath b, float p) {
        if (a instanceof ClipPath.Inset x && b instanceof ClipPath.Inset y) {
            Length top = Length.mix(x.top(), y.top(), p, false);
            Length right = Length.mix(x.right(), y.right(), p, false);
            Length bottom = Length.mix(x.bottom(), y.bottom(), p, false);
            Length left = Length.mix(x.left(), y.left(), p, false);
            CornerRadius tl = mix(x.topLeft(), y.topLeft(), p);
            CornerRadius tr = mix(x.topRight(), y.topRight(), p);
            CornerRadius br = mix(x.bottomRight(), y.bottomRight(), p);
            CornerRadius bl = mix(x.bottomLeft(), y.bottomLeft(), p);
            return top == null || right == null || bottom == null || left == null || tl == null || tr == null || br == null || bl == null
                    ? null : new ClipPath.Inset(top, right, bottom, left, tl, tr, br, bl);
        }
        if (a instanceof ClipPath.Circle x && b instanceof ClipPath.Circle y) {
            ClipPath.Radius r = mix(x.radius(), y.radius(), p);
            Length cx = Length.mix(x.centerX(), y.centerX(), p, false);
            Length cy = Length.mix(x.centerY(), y.centerY(), p, false);
            return r == null || cx == null || cy == null ? null : new ClipPath.Circle(r, cx, cy);
        }
        if (a instanceof ClipPath.Ellipse x && b instanceof ClipPath.Ellipse y) {
            ClipPath.Radius rx = mix(x.radiusX(), y.radiusX(), p);
            ClipPath.Radius ry = mix(x.radiusY(), y.radiusY(), p);
            Length cx = Length.mix(x.centerX(), y.centerX(), p, false);
            Length cy = Length.mix(x.centerY(), y.centerY(), p, false);
            return rx == null || ry == null || cx == null || cy == null ? null : new ClipPath.Ellipse(rx, ry, cx, cy);
        }
        if (a instanceof ClipPath.Polygon x && b instanceof ClipPath.Polygon y && x.evenOdd() == y.evenOdd()
                && x.points().size() == y.points().size()) {
            List<Length> points = new ArrayList<>(x.points().size());
            for (int i = 0; i < x.points().size(); i++) {
                Length l = Length.mix(x.points().get(i), y.points().get(i), p, false);
                if (l == null) {
                    return null;
                }
                points.add(l);
            }
            return new ClipPath.Polygon(x.evenOdd(), List.copyOf(points));
        }
        return null;
    }

    private static CornerRadius mix(CornerRadius a, CornerRadius b, float p) {
        Length x = Length.mix(a.x(), b.x(), p, true);
        Length y = Length.mix(a.y(), b.y(), p, true);
        return x == null || y == null ? null : new CornerRadius(x, y);
    }

    /** Two lengths mix; two keywords only when they are the same one, which stays. */
    private static ClipPath.Radius mix(ClipPath.Radius a, ClipPath.Radius b, float p) {
        if (a.length() == null || b.length() == null) {
            return a.equals(b) ? a : null;
        }
        Length l = Length.mix(a.length(), b.length(), p, true);
        return l == null ? null : new ClipPath.Radius(l, false);
    }
}
