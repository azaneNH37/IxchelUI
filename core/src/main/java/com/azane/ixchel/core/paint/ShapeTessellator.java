package com.azane.ixchel.core.paint;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Turns {@link DrawCommand.Fill}, {@link DrawCommand.Border} and {@link DrawCommand.Shadow} into
 * textured quads over a {@link MaskAtlas}, for a host that draws position-color-texture quads.
 *
 * <p>Each shape is a coverage function over the plane. Its region is cut at every line where the
 * function starts or stops depending on x or y (a corner's extent, an edge's blur), into cells: a cell
 * where it depends on neither is one solid quad, where it depends on one axis a stretched strip mask,
 * and where on both (a corner) a 2D mask. Masks are keyed by the shapes relative to the cell, so a
 * corner moving or a box resizing reuses them. A gradient or a border with sides of different colors
 * further cuts the cells into convex pieces whose vertex colors interpolate exactly.
 *
 * <p>Blur is CSS's: a Gaussian whose standard deviation is half the blur radius, integrated exactly
 * across rows and numerically along the corners.
 */
public final class ShapeTessellator {
    /** Floats per vertex in a mesh: x, y, u, v and the ARGB color as {@link Float#intBitsToFloat} bits. */
    public static final int STRIDE = 5;
    private static final float QUANTUM = 8;
    /** Texels per GUI pixel of a sharp mask. */
    private static final float SHARP_DENSITY = 4;
    private static final int SUPERSAMPLE = 4;
    private static final int ARC_SAMPLES = 16;

    private static final int COVER = 0;
    private static final int RING = 1;
    private static final int OUTER = 2;
    private static final int INNER = 3;

    private final MaskAtlas atlas;
    private Map<DrawCommand, float[]> current = new IdentityHashMap<>();
    private Map<DrawCommand, float[]> previous = new IdentityHashMap<>();
    private int cachedGeneration;
    private float[] buffer = new float[STRIDE * 64];
    private int size;

    // Shapes of the command being built: {l, t, r, b, 8 radii, sigma}.
    private final float[] a = new float[13];
    private final float[] b = new float[13];
    private int kind;
    private final float[] xs = new float[24];
    private final float[] ys = new float[24];

    public ShapeTessellator(MaskAtlas atlas) {
        this.atlas = atlas;
        cachedGeneration = atlas.generation();
    }

    public MaskAtlas atlas() {
        return atlas;
    }

    /**
     * Quads (four vertices each, {@link #STRIDE} floats per vertex) drawing {@code c}, a fill, border
     * or shadow; texture coordinates are fractions of the atlas. The array is cached for the next
     * frame and must not be modified. May reset the atlas.
     */
    public float[] mesh(DrawCommand c) {
        if (atlas.generation() != cachedGeneration) {
            current.clear();
            previous.clear();
            cachedGeneration = atlas.generation();
        }
        float[] m = current.get(c);
        if (m == null) {
            m = previous.get(c);
            if (m == null) {
                m = build(c);
            }
            current.put(c, m);
        }
        return m;
    }

    /** Forgets the meshes of commands not drawn since the previous call. */
    public void endFrame() {
        Map<DrawCommand, float[]> t = previous;
        previous = current;
        current = t;
        current.clear();
    }

    private float[] build(DrawCommand c) {
        int generation = atlas.generation();
        tessellate(c);
        if (atlas.generation() != generation) {
            // The atlas was reset mid-way, so the first cells point at dropped masks; it has room now.
            tessellate(c);
        }
        cachedGeneration = atlas.generation();
        return Arrays.copyOf(buffer, size);
    }

    private void tessellate(DrawCommand c) {
        size = 0;
        if (c instanceof DrawCommand.Fill f) {
            fill(f);
        } else if (c instanceof DrawCommand.Border r) {
            border(r);
        } else if (c instanceof DrawCommand.Shadow s) {
            shadow(s);
        } else {
            throw new IllegalArgumentException("not a shape: " + c);
        }
    }

    private void fill(DrawCommand.Fill f) {
        kind = COVER;
        set(a, f.shape(), 0);
        RoundRect s = f.shape();
        float x0 = Math.max(s.x(), f.clipX());
        float y0 = Math.max(s.y(), f.clipY());
        float x1 = Math.min(s.x() + s.width(), f.clipX() + f.clipWidth());
        float y1 = Math.min(s.y() + s.height(), f.clipY() + f.clipHeight());
        cells(x0, y0, x1, y1, f.color(), f.gradient(), null);
    }

    private void border(DrawCommand.Border r) {
        kind = RING;
        RoundRect outer = r.outer();
        set(a, outer, 0);
        set(b, outer.inset(r.top(), r.right(), r.bottom(), r.left()), 0);
        int[] colors = {r.topColor(), r.rightColor(), r.bottomColor(), r.leftColor()};
        boolean same = colors[0] == colors[1] && colors[1] == colors[2] && colors[2] == colors[3];
        cells(outer.x(), outer.y(), outer.x() + outer.width(), outer.y() + outer.height(), colors[0], null,
                same ? null : sides(r, colors));
    }

    private void shadow(DrawCommand.Shadow s) {
        RoundRect shape = s.shape();
        set(a, shape, s.sigma());
        set(b, s.clip(), 0);
        if (s.inset()) {
            kind = INNER;
            RoundRect c = s.clip();
            cells(c.x(), c.y(), c.x() + c.width(), c.y() + c.height(), s.color(), null, null);
        } else {
            kind = OUTER;
            float e = 3 * s.sigma();
            cells(shape.x() - e, shape.y() - e, shape.x() + shape.width() + e, shape.y() + shape.height() + e,
                    s.color(), null, null);
        }
    }

    private static void set(float[] s, RoundRect r, float sigma) {
        RoundRect.Radii q = r.radii();
        s[0] = r.x();
        s[1] = r.y();
        s[2] = r.x() + r.width();
        s[3] = r.y() + r.height();
        s[4] = q.topLeftX();
        s[5] = q.topLeftY();
        s[6] = q.topRightX();
        s[7] = q.topRightY();
        s[8] = q.bottomRightX();
        s[9] = q.bottomRightY();
        s[10] = q.bottomLeftX();
        s[11] = q.bottomLeftY();
        s[12] = sigma;
    }

    private boolean usesB() {
        return kind != COVER;
    }

    // --- Cells ---

    private void cells(float x0, float y0, float x1, float y1, int color, Gradient gradient, float[][] sides) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        int nx = breaks(xs, x0, x1, true);
        int ny = breaks(ys, y0, y1, false);
        for (int j = 0; j + 1 < ny; j++) {
            float cy0 = ys[j];
            float cy1 = ys[j + 1];
            boolean flatY = flat(cy0, cy1, false);
            for (int i = 0; i + 1 < nx; i++) {
                float cx0 = xs[i];
                float cx1 = xs[i + 1];
                cell(cx0, cy0, cx1, cy1, flat(cx0, cx1, true), flatY, color, gradient, sides);
            }
        }
    }

    /** Sorted distinct cut positions within {@code [lo, hi]}, both ends included. */
    private int breaks(float[] out, float lo, float hi, boolean x) {
        int n = 0;
        out[n++] = lo;
        out[n++] = hi;
        n = zoneBreaks(out, n, a, lo, hi, x);
        if (usesB()) {
            n = zoneBreaks(out, n, b, lo, hi, x);
        }
        Arrays.sort(out, 0, n);
        int k = 1;
        for (int i = 1; i < n; i++) {
            if (out[i] - out[k - 1] > 1e-4f) {
                out[k++] = out[i];
            }
        }
        return k;
    }

    private static int zoneBreaks(float[] out, int n, float[] s, float lo, float hi, boolean x) {
        float e = reach(s);
        float[] z = zones(s, e, x);
        for (float v : z) {
            if (v > lo && v < hi) {
                out[n++] = v;
            }
        }
        return n;
    }

    private static float reach(float[] s) {
        return 3 * s[12];
    }

    /** The two intervals {@code [z0, z1]} and {@code [z2, z3]} where the shape depends on x (or y). */
    private static float[] zones(float[] s, float e, boolean x) {
        if (x) {
            return new float[]{s[0] - e, s[0] + Math.max(s[4], s[10]) + e, s[2] - Math.max(s[6], s[8]) - e, s[2] + e};
        }
        return new float[]{s[1] - e, s[1] + Math.max(s[5], s[7]) + e, s[3] - Math.max(s[9], s[11]) - e, s[3] + e};
    }

    private boolean flat(float lo, float hi, boolean x) {
        return flat(a, lo, hi, x) && (!usesB() || flat(b, lo, hi, x));
    }

    private static boolean flat(float[] s, float lo, float hi, boolean x) {
        float[] z = zones(s, reach(s), x);
        return !(lo < z[1] && hi > z[0]) && !(lo < z[3] && hi > z[2]);
    }

    private void cell(float x0, float y0, float x1, float y1, boolean flatX, boolean flatY, int color,
                      Gradient gradient, float[][] sides) {
        float w = x1 - x0;
        float h = y1 - y0;
        float u0;
        float v0;
        float du;
        float dv;
        float scale = 1;
        if (flatX && flatY) {
            scale = field(a, b, kind, (x0 + x1) / 2, (y0 + y1) / 2, 0, 0);
            if (scale < 1f / 512) {
                return;
            }
            u0 = MaskAtlas.OPAQUE;
            v0 = MaskAtlas.OPAQUE;
            du = 0;
            dv = 0;
        } else {
            float density = density();
            int n = flatX ? 1 : Math.max(1, Math.min(MaskAtlas.MAX_MASK, (int) Math.ceil(w * density)));
            int m = flatY ? 1 : Math.max(1, Math.min(MaskAtlas.MAX_MASK, (int) Math.ceil(h * density)));
            Mask key = mask(x0, y0, w, h, n, m, flatX, flatY);
            int[] r = atlas.region(key, n, m, key);
            u0 = flatX ? r[0] + 0.5f : r[0];
            v0 = flatY ? r[1] + 0.5f : r[1];
            du = flatX ? 0 : n / w;
            dv = flatY ? 0 : m / h;
        }
        float[] px = {x0, x1, x1, x0};
        float[] py = {y0, y0, y1, y1};
        Piece p = new Piece(x0, y0, u0, v0, du, dv, scale);
        if (sides != null) {
            for (int s = 0; s < 4; s++) {
                float[][] poly = {px, py};
                int k = 4;
                float[] side = sides[s];
                for (int c = 0; c + 2 < side.length && k > 0; c += 3) {
                    poly = clip(poly[0], poly[1], k, side[c], side[c + 1], side[c + 2]);
                    k = poly[0].length;
                }
                if (k >= 3) {
                    emit(poly[0], poly[1], k, p, sideColor(side), null);
                }
            }
        } else if (gradient != null) {
            gradientPieces(px, py, 4, p, gradient);
        } else {
            emit(px, py, 4, p, color, null);
        }
    }

    private float density() {
        float sigma = Math.max(a[12], usesB() ? b[12] : 0);
        return sigma <= 2 ? SHARP_DENSITY : Math.max(0.25f, 8 / sigma);
    }

    /** Texture mapping of one cell: {@code u = u0 + (x - x0) * du} in texels, {@code scale} multiplies alpha. */
    private record Piece(float x0, float y0, float u0, float v0, float du, float dv, float scale) {
    }

    private void gradientPieces(float[] px, float[] py, int n, Piece p, Gradient g) {
        float dx = g.x1() - g.x0();
        float dy = g.y1() - g.y0();
        float len2 = dx * dx + dy * dy;
        if (len2 == 0) {
            emit(px, py, n, p, g.colors()[g.colors().length - 1], null);
            return;
        }
        // t(x, y) = (x * dx + y * dy - base) / len2
        float base = g.x0() * dx + g.y0() * dy;
        float[] rx = px;
        float[] ry = py;
        int k = n;
        float[] offsets = g.offsets();
        for (int i = 0; i < offsets.length && k >= 3; i++) {
            // t <= o: dx * x + dy * y - (base + o * len2) <= 0
            float c = -(base + offsets[i] * len2);
            float[][] below = clip(rx, ry, k, dx, dy, c);
            if (below[0].length >= 3) {
                emit(below[0], below[1], below[0].length, p, 0, g);
            }
            float[][] above = clip(rx, ry, k, -dx, -dy, -c);
            rx = above[0];
            ry = above[1];
            k = rx.length;
        }
        if (k >= 3) {
            emit(rx, ry, k, p, 0, g);
        }
    }

    /**
     * Side regions of a multicolored border, each as half-planes {@code a x + b y + c <= 0} (three
     * floats each) followed by the side's color bits; top, right, bottom, left.
     */
    private static float[][] sides(DrawCommand.Border r, int[] colors) {
        RoundRect o = r.outer();
        float l = o.x();
        float t = o.y();
        float rr = l + o.width();
        float bb = t + o.height();
        float midX = (l + rr) / 2;
        float midY = (t + bb) / 2;
        float wt = Math.max(r.top(), 1e-3f);
        float wr = Math.max(r.right(), 1e-3f);
        float wb = Math.max(r.bottom(), 1e-3f);
        float wl = Math.max(r.left(), 1e-3f);
        float[] tl = line(l, t, wl, wt);
        float[] tr = line(rr, t, -wr, wt);
        float[] br = line(rr, bb, -wr, -wb);
        float[] bl = line(l, bb, wl, -wb);
        return new float[][]{
                side(new float[]{0, 1, -midY}, tl, neg(tr), colors[0]),
                side(new float[]{-1, 0, midX}, tr, neg(br), colors[1]),
                side(new float[]{0, -1, midY}, br, neg(bl), colors[2]),
                side(new float[]{1, 0, -midX}, bl, neg(tl), colors[3]),
        };
    }

    /** {@code cross(d, p - o) <= 0} as {@code a x + b y + c <= 0}. */
    private static float[] line(float ox, float oy, float dx, float dy) {
        return new float[]{-dy, dx, dy * ox - dx * oy};
    }

    private static float[] neg(float[] h) {
        return new float[]{-h[0], -h[1], -h[2]};
    }

    private static float[] side(float[] h0, float[] h1, float[] h2, int color) {
        return new float[]{h0[0], h0[1], h0[2], h1[0], h1[1], h1[2], h2[0], h2[1], h2[2], Float.intBitsToFloat(color)};
    }

    private static int sideColor(float[] side) {
        return Float.floatToRawIntBits(side[side.length - 1]);
    }

    /** {@code poly} clipped to {@code ha x + hb y + hc <= 0} (Sutherland–Hodgman). */
    private static float[][] clip(float[] px, float[] py, int n, float ha, float hb, float hc) {
        float[] ox = new float[n + 2];
        float[] oy = new float[n + 2];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float di = ha * px[i] + hb * py[i] + hc;
            float dj = ha * px[j] + hb * py[j] + hc;
            if (di <= 0) {
                ox[k] = px[i];
                oy[k++] = py[i];
            }
            if (di < 0 && dj > 0 || di > 0 && dj < 0) {
                float f = di / (di - dj);
                ox[k] = px[i] + (px[j] - px[i]) * f;
                oy[k++] = py[i] + (py[j] - py[i]) * f;
            }
        }
        return new float[][]{Arrays.copyOf(ox, k), Arrays.copyOf(oy, k)};
    }

    /** Emits a convex polygon as a fan of quads (the last one a triangle with a repeated vertex). */
    private void emit(float[] px, float[] py, int n, Piece p, int color, Gradient g) {
        for (int i = 1; i + 1 < n; i += 2) {
            int i2 = Math.min(i + 2, n - 1);
            vertex(px[0], py[0], p, color, g);
            vertex(px[i], py[i], p, color, g);
            vertex(px[i + 1], py[i + 1], p, color, g);
            vertex(px[i2], py[i2], p, color, g);
        }
    }

    private void vertex(float x, float y, Piece p, int color, Gradient g) {
        if (size + STRIDE > buffer.length) {
            buffer = Arrays.copyOf(buffer, buffer.length * 2);
        }
        int c = g == null ? color : g.color(g.at(x, y));
        if (p.scale < 1) {
            c = Math.round((c >>> 24) * p.scale) << 24 | c & 0xFFFFFF;
        }
        buffer[size++] = x;
        buffer[size++] = y;
        buffer[size++] = (p.u0 + (x - p.x0) * p.du) / MaskAtlas.SIZE;
        buffer[size++] = (p.v0 + (y - p.y0) * p.dv) / MaskAtlas.SIZE;
        buffer[size++] = Float.intBitsToFloat(c);
    }

    // --- Masks ---

    /**
     * A cell's mask: its kind, its size in pixels and texels, and its shapes relative to its top-left,
     * quantized, with edges beyond reach moved to a fixed distance so the key does not depend on them.
     */
    private static final class Mask implements MaskAtlas.Baker {
        final int kind;
        final float w;
        final float h;
        final int n;
        final int m;
        final boolean flatX;
        final boolean flatY;
        final float[] sa;
        final float[] sb;
        final int hash;

        Mask(int kind, float w, float h, int n, int m, boolean flatX, boolean flatY, float[] sa, float[] sb) {
            this.kind = kind;
            this.w = w;
            this.h = h;
            this.n = n;
            this.m = m;
            this.flatX = flatX;
            this.flatY = flatY;
            this.sa = sa;
            this.sb = sb;
            this.hash = ((((kind * 31 + Float.hashCode(w)) * 31 + Float.hashCode(h)) * 31 + n) * 31 + m) * 31
                    + Arrays.hashCode(sa) * 7 + Arrays.hashCode(sb) + (flatX ? 1 : 0) + (flatY ? 2 : 0);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Mask k && k.hash == hash && k.kind == kind && k.w == w && k.h == h && k.n == n && k.m == m
                    && k.flatX == flatX && k.flatY == flatY && Arrays.equals(k.sa, sa) && Arrays.equals(k.sb, sb);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public void bake(float[] out, int width, int height) {
            float tw = w / width;
            float th = h / height;
            for (int j = 0; j < height; j++) {
                float cy = flatY ? h / 2 : (j + 0.5f) * th;
                for (int i = 0; i < width; i++) {
                    float cx = flatX ? w / 2 : (i + 0.5f) * tw;
                    out[j * width + i] = field(sa, sb, kind, cx, cy, flatX ? 0 : tw, flatY ? 0 : th);
                }
            }
        }
    }

    private Mask mask(float x0, float y0, float w, float h, int n, int m, boolean flatX, boolean flatY) {
        float qw = q(w);
        float qh = q(h);
        return new Mask(kind, qw, qh, n, m, flatX, flatY, relative(a, x0, y0, qw, qh),
                usesB() ? relative(b, x0, y0, qw, qh) : null);
    }

    private static float[] relative(float[] s, float x0, float y0, float w, float h) {
        float[] r = new float[13];
        for (int i = 4; i < 13; i++) {
            r[i] = q(s[i]);
        }
        float e = 3 * r[12] + 1;
        r[0] = Math.max(q(s[0] - x0), -e - Math.max(r[4], r[10]));
        r[1] = Math.max(q(s[1] - y0), -e - Math.max(r[5], r[7]));
        r[2] = Math.min(q(s[2] - x0), w + e + Math.max(r[6], r[8]));
        r[3] = Math.min(q(s[3] - y0), h + e + Math.max(r[9], r[11]));
        return r;
    }

    private static float q(float v) {
        return Math.round(v * QUANTUM) / QUANTUM;
    }

    /**
     * The shape's coverage over the {@code tw} by {@code th} texel centered at {@code (x, y)}: blurred
     * terms at the center, sharp ones supersampled (a zero side is sampled once).
     */
    static float field(float[] sa, float[] sb, int kind, float x, float y, float tw, float th) {
        switch (kind) {
            case COVER:
                return coverage(sa, null, x, y, tw, th);
            case RING:
                return coverage(sa, sb, x, y, tw, th);
            case OUTER:
                return blur(sa, x, y) * (1 - coverage(sb, null, x, y, tw, th));
            default:
                return coverage(sb, null, x, y, tw, th) * (1 - blur(sa, x, y));
        }
    }

    /** The fraction of the texel inside {@code s} and, when given, outside {@code hole}. */
    private static float coverage(float[] s, float[] hole, float x, float y, float tw, float th) {
        int nx = tw > 0 ? SUPERSAMPLE : 1;
        int ny = th > 0 ? SUPERSAMPLE : 1;
        int in = 0;
        for (int j = 0; j < ny; j++) {
            float sy = ny == 1 ? y : y - th / 2 + (j + 0.5f) * th / ny;
            for (int i = 0; i < nx; i++) {
                float sx = nx == 1 ? x : x - tw / 2 + (i + 0.5f) * tw / nx;
                if (inside(s, sx, sy) && (hole == null || !inside(hole, sx, sy))) {
                    in++;
                }
            }
        }
        return in / (float) (nx * ny);
    }

    static boolean inside(float[] s, float x, float y) {
        if (x < s[0] || x >= s[2] || y < s[1] || y >= s[3]) {
            return false;
        }
        return corner(x, y, s[0] + s[4], s[1] + s[5], s[4], s[5], x < s[0] + s[4] && y < s[1] + s[5])
                && corner(x, y, s[2] - s[6], s[1] + s[7], s[6], s[7], x > s[2] - s[6] && y < s[1] + s[7])
                && corner(x, y, s[2] - s[8], s[3] - s[9], s[8], s[9], x > s[2] - s[8] && y > s[3] - s[9])
                && corner(x, y, s[0] + s[10], s[3] - s[11], s[10], s[11], x < s[0] + s[10] && y > s[3] - s[11]);
    }

    private static boolean corner(float x, float y, float cx, float cy, float rx, float ry, boolean in) {
        if (!in || rx <= 0 || ry <= 0) {
            return true;
        }
        float dx = (x - cx) / rx;
        float dy = (y - cy) / ry;
        return dx * dx + dy * dy <= 1;
    }

    /** The Gaussian blur of {@code s} (standard deviation {@code s[12]}) at {@code (x, y)}. */
    static float blur(float[] s, float x, float y) {
        float sigma = s[12];
        if (sigma < 1e-3f) {
            return inside(s, x, y) ? 1 : 0;
        }
        float t = s[1];
        float b = s[3];
        float straight0 = t + Math.max(s[5], s[7]);
        float straight1 = b - Math.max(s[9], s[11]);
        float lo = y - 4 * sigma;
        float hi = y + 4 * sigma;
        float sum = 0;
        if (straight1 > straight0) {
            sum += row(s, x, (straight0 + straight1) / 2) * (phi((straight1 - y) / sigma) - phi((straight0 - y) / sigma));
            sum += arc(s, x, y, Math.max(t, lo), Math.min(straight0, hi));
            sum += arc(s, x, y, Math.max(straight1, lo), Math.min(b, hi));
        } else {
            sum += arc(s, x, y, Math.max(t, lo), Math.min(b, hi));
        }
        return Math.max(0, Math.min(1, sum));
    }

    /** {@code ∫ row(x, y') N(y' - y) dy'} over {@code [y0, y1]} by the midpoint rule. */
    private static float arc(float[] s, float x, float y, float y0, float y1) {
        if (y1 <= y0) {
            return 0;
        }
        float sigma = s[12];
        float step = (y1 - y0) / ARC_SAMPLES;
        float sum = 0;
        for (int k = 0; k < ARC_SAMPLES; k++) {
            float yk = y0 + (k + 0.5f) * step;
            float z = (yk - y) / sigma;
            sum += (float) Math.exp(-0.5 * z * z) * row(s, x, yk);
        }
        return sum * step / (sigma * 2.5066283f);
    }

    /** The row of {@code s} at {@code y} blurred horizontally, at {@code x}. */
    private static float row(float[] s, float x, float y) {
        float sigma = s[12];
        float left = s[0] + Math.max(arcInset(s[4], s[5], y - s[1]), arcInset(s[10], s[11], s[3] - y));
        float right = s[2] - Math.max(arcInset(s[6], s[7], y - s[1]), arcInset(s[8], s[9], s[3] - y));
        if (right <= left) {
            return 0;
        }
        return phi((x - left) / sigma) - phi((x - right) / sigma);
    }

    /** How far a corner's curve stands in from the straight edge, {@code d} into the box along the other axis. */
    private static float arcInset(float rx, float ry, float d) {
        if (rx <= 0 || ry <= 0 || d >= ry) {
            return 0;
        }
        float k = (ry - Math.max(0, d)) / ry;
        return rx * (1 - (float) Math.sqrt(Math.max(0, 1 - k * k)));
    }

    /** Standard normal CDF, through Abramowitz and Stegun 7.1.26. */
    private static float phi(float z) {
        double x = Math.abs(z) / Math.sqrt(2);
        double t = 1 / (1 + 0.3275911 * x);
        double erf = 1 - ((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t
                * Math.exp(-x * x);
        return (float) (z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf));
    }
}
