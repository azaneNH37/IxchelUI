package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.css.property.ClipPath;
import com.azane.ixchel.core.css.property.CornerRadius;
import com.azane.ixchel.core.css.property.Length;

import java.util.List;

/**
 * A {@code clip-path} resolved against an element's border box, in the coordinates the element's
 * box is painted in (before its own transform): a rounded rectangle ({@code inset()}; a
 * {@code circle()} or {@code ellipse()} is one with all four radii half its sides) or a polygon.
 * Both the painter (through {@link DrawCommand.PushLayer#clip()}) and hit testing use it.
 */
@HostApi
public sealed interface ClipShape permits ClipShape.Box, ClipShape.Polygon {
    /** Whether {@code (x, y)} is inside; an edge counts as inside. */
    boolean contains(float x, float y);

    /** {@code {x0, y0, x1, y1}}, the bounding box; empty ({@code x1 <= x0}) when nothing is inside. */
    float[] bounds();

    /** A rounded rectangle; its corner shapes are all round. */
    record Box(RoundRect shape) implements ClipShape {
        @Override
        public boolean contains(float x, float y) {
            RoundRect s = shape;
            float x0 = s.x(), y0 = s.y(), x1 = x0 + s.width(), y1 = y0 + s.height();
            if (x < x0 || y < y0 || x > x1 || y > y1) {
                return false;
            }
            RoundRect.Radii r = s.radii();
            boolean left = x < (x0 + x1) / 2, top = y < (y0 + y1) / 2;
            float rx = top ? (left ? r.topLeftX() : r.topRightX()) : (left ? r.bottomLeftX() : r.bottomRightX());
            float ry = top ? (left ? r.topLeftY() : r.topRightY()) : (left ? r.bottomLeftY() : r.bottomRightY());
            if (rx <= 0 || ry <= 0) {
                return true;
            }
            float cx = left ? x0 + rx : x1 - rx, cy = top ? y0 + ry : y1 - ry;
            if ((left ? x >= cx : x <= cx) || (top ? y >= cy : y <= cy)) {
                return true;
            }
            float u = (x - cx) / rx, v = (y - cy) / ry;
            return u * u + v * v <= 1;
        }

        @Override
        public float[] bounds() {
            return new float[]{shape.x(), shape.y(), shape.x() + shape.width(), shape.y() + shape.height()};
        }
    }

    /** {@code points}: x then y of each vertex; {@code evenOdd} the {@code evenodd} fill rule, otherwise {@code nonzero}. */
    record Polygon(float[] points, boolean evenOdd) implements ClipShape {
        @Override
        public boolean contains(float x, float y) {
            int winding = 0;
            int n = points.length / 2;
            for (int i = 0; i < n; i++) {
                float ax = points[2 * i], ay = points[2 * i + 1];
                int j = (i + 1) % n;
                float bx = points[2 * j], by = points[2 * j + 1];
                float cross = (bx - ax) * (y - ay) - (x - ax) * (by - ay);
                if (cross == 0 && Math.min(ax, bx) <= x && x <= Math.max(ax, bx) && Math.min(ay, by) <= y && y <= Math.max(ay, by)) {
                    return true; // on the edge
                }
                if (ay <= y) {
                    if (by > y && cross > 0) {
                        winding++;
                    }
                } else if (by <= y && cross < 0) {
                    winding--;
                }
            }
            return evenOdd ? (winding & 1) != 0 : winding != 0;
        }

        @Override
        public float[] bounds() {
            float x0 = Float.POSITIVE_INFINITY, y0 = Float.POSITIVE_INFINITY;
            float x1 = Float.NEGATIVE_INFINITY, y1 = Float.NEGATIVE_INFINITY;
            for (int i = 0; i < points.length; i += 2) {
                x0 = Math.min(x0, points[i]);
                x1 = Math.max(x1, points[i]);
                y0 = Math.min(y0, points[i + 1]);
                y1 = Math.max(y1, points[i + 1]);
            }
            return points.length < 6 ? new float[]{0, 0, 0, 0} : new float[]{x0, y0, x1, y1};
        }
    }

    /** {@code p} in the border box {@code (x, y, w, h)} (CSS Shapes 1 §3.1); {@code null} for {@code none}. */
    static ClipShape of(ClipPath p, float x, float y, float w, float h) {
        if (p instanceof ClipPath.Inset i) {
            float top = i.top().resolve(h), bottom = i.bottom().resolve(h);
            float left = i.left().resolve(w), right = i.right().resolve(w);
            // Insets adding up to more than the box are reduced in proportion.
            if (left + right > w && left + right > 0) {
                float f = Math.max(0, w) / (left + right);
                left *= f;
                right *= f;
            }
            if (top + bottom > h && top + bottom > 0) {
                float f = Math.max(0, h) / (top + bottom);
                top *= f;
                bottom *= f;
            }
            float iw = Math.max(0, w - left - right), ih = Math.max(0, h - top - bottom);
            RoundRect.Radii radii = new RoundRect.Radii(rx(i.topLeft(), w), ry(i.topLeft(), h), rx(i.topRight(), w), ry(i.topRight(), h),
                    rx(i.bottomRight(), w), ry(i.bottomRight(), h), rx(i.bottomLeft(), w), ry(i.bottomLeft(), h),
                    RoundRect.Corners.ROUND).fit(iw, ih);
            return new Box(new RoundRect(x + left, y + top, iw, ih, radii));
        }
        if (p instanceof ClipPath.Circle c) {
            float cx = x + c.centerX().resolve(w), cy = y + c.centerY().resolve(h);
            float r;
            if (c.radius().length() != null) {
                r = c.radius().length().resolve((float) (Math.hypot(w, h) / Math.sqrt(2)));
            } else {
                float sx = side(cx, x, x + w, c.radius().farthest()), sy = side(cy, y, y + h, c.radius().farthest());
                r = c.radius().farthest() ? Math.max(sx, sy) : Math.min(sx, sy);
            }
            return ellipse(cx, cy, r, r);
        }
        if (p instanceof ClipPath.Ellipse e) {
            float cx = x + e.centerX().resolve(w), cy = y + e.centerY().resolve(h);
            float rx = e.radiusX().length() != null ? e.radiusX().length().resolve(w) : side(cx, x, x + w, e.radiusX().farthest());
            float ry = e.radiusY().length() != null ? e.radiusY().length().resolve(h) : side(cy, y, y + h, e.radiusY().farthest());
            return ellipse(cx, cy, rx, ry);
        }
        if (p instanceof ClipPath.Polygon g) {
            List<Length> l = g.points();
            float[] points = new float[l.size()];
            for (int k = 0; k < points.length; k += 2) {
                points[k] = x + l.get(k).resolve(w);
                points[k + 1] = y + l.get(k + 1).resolve(h);
            }
            return new Polygon(points, g.evenOdd());
        }
        return null;
    }

    private static Box ellipse(float cx, float cy, float rx, float ry) {
        rx = Math.max(0, rx);
        ry = Math.max(0, ry);
        return new Box(new RoundRect(cx - rx, cy - ry, 2 * rx, 2 * ry,
                new RoundRect.Radii(rx, ry, rx, ry, rx, ry, rx, ry, RoundRect.Corners.ROUND)));
    }

    /** The distance from {@code c} to the nearer or the farther of {@code lo} and {@code hi}. */
    private static float side(float c, float lo, float hi, boolean farthest) {
        float a = Math.abs(c - lo), b = Math.abs(hi - c);
        return farthest ? Math.max(a, b) : Math.min(a, b);
    }

    private static float rx(CornerRadius r, float w) {
        return r.x().resolve(w);
    }

    private static float ry(CornerRadius r, float h) {
        return r.y().resolve(h);
    }
}
