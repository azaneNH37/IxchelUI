package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.Keywords.CornerShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rasterizes the meshes point by point (bilinear texel filtering, as the hosts sample the atlas, nearest
 * for crisp commands) and checks the coverage they draw.
 */
class ShapeTessellatorTest {
    private final MaskAtlas atlas = new MaskAtlas(() -> {
    });
    private final ShapeTessellator tess = new ShapeTessellator(atlas, false);
    private final MaskAtlas crispAtlas = new MaskAtlas(() -> {
    });
    private final ShapeTessellator crispTess = new ShapeTessellator(crispAtlas, true);

    /** The drawn ARGB at {@code (x, y)}: vertex color times mask alpha, or 0 where no quad covers it. */
    private int at(DrawCommand c, float x, float y) {
        boolean crisp = c instanceof DrawCommand.Fill f && f.crisp() || c instanceof DrawCommand.Shadow sh && sh.crisp();
        float[] m = (crisp ? crispTess : tess).mesh(c);
        MaskAtlas source = crisp ? crispAtlas : atlas;
        int s = ShapeTessellator.STRIDE;
        for (int q = 0; q < m.length; q += 4 * s) {
            for (int t = 0; t < 2; t++) {
                int i0 = q;
                int i1 = q + (t == 0 ? 1 : 2) * s;
                int i2 = q + (t == 0 ? 2 : 3) * s;
                float x0 = m[i0], y0 = m[i0 + 1], x1 = m[i1], y1 = m[i1 + 1], x2 = m[i2], y2 = m[i2 + 1];
                float d = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
                if (Math.abs(d) < 1e-9) {
                    continue;
                }
                float w0 = ((y1 - y2) * (x - x2) + (x2 - x1) * (y - y2)) / d;
                float w1 = ((y2 - y0) * (x - x2) + (x0 - x2) * (y - y2)) / d;
                float w2 = 1 - w0 - w1;
                if (w0 < -1e-4 || w1 < -1e-4 || w2 < -1e-4) {
                    continue;
                }
                float u = w0 * m[i0 + 2] + w1 * m[i1 + 2] + w2 * m[i2 + 2];
                float v = w0 * m[i0 + 3] + w1 * m[i1 + 3] + w2 * m[i2 + 3];
                float mask = crisp ? texel(source, (int) Math.floor(u * MaskAtlas.SIZE), (int) Math.floor(v * MaskAtlas.SIZE)) / 255f
                        : sample(source, u * MaskAtlas.SIZE - 0.5f, v * MaskAtlas.SIZE - 0.5f);
                int c0 = Float.floatToRawIntBits(m[i0 + 4]);
                int c1 = Float.floatToRawIntBits(m[i1 + 4]);
                int c2 = Float.floatToRawIntBits(m[i2 + 4]);
                int out = 0;
                for (int shift = 24; shift >= 0; shift -= 8) {
                    float ch = w0 * (c0 >>> shift & 0xFF) + w1 * (c1 >>> shift & 0xFF) + w2 * (c2 >>> shift & 0xFF);
                    if (shift == 24) {
                        ch *= mask;
                    }
                    out |= Math.round(ch) << shift;
                }
                return out;
            }
        }
        return 0;
    }

    /** The mask at texel coordinates {@code (x, y)} between texel centers, filtered linearly. */
    private static float sample(MaskAtlas atlas, float x, float y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float top = texel(atlas, x0, y0) * (1 - fx) + texel(atlas, x0 + 1, y0) * fx;
        float bottom = texel(atlas, x0, y0 + 1) * (1 - fx) + texel(atlas, x0 + 1, y0 + 1) * fx;
        return (top * (1 - fy) + bottom * fy) / 255f;
    }

    private static int texel(MaskAtlas atlas, int x, int y) {
        int cx = Math.max(0, Math.min(MaskAtlas.SIZE - 1, x));
        int cy = Math.max(0, Math.min(MaskAtlas.SIZE - 1, y));
        return atlas.pixels()[cy * MaskAtlas.SIZE + cx] & 0xFF;
    }

    private static RoundRect.Radii radii(float r, RoundRect.Corners corners) {
        return new RoundRect.Radii(r, r, r, r, r, r, r, r, corners);
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    @Test
    void roundedFillCoversTheInsideOfTheCurveOnly() {
        RoundRect.Radii r = radii(10, RoundRect.Corners.ROUND);
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 40, 20, r), 0, 0, 40, 20, 0xFFFFFFFF, null, false);
        assertEquals(0, alpha(at(f, 1, 1)), "outside the top-left curve");
        assertEquals(255, alpha(at(f, 20, 10)), "the middle");
        assertEquals(255, alpha(at(f, 20, 0.2f)), "the straight top edge");
        assertEquals(255, alpha(at(f, 4, 4)), "inside the curve");
        assertEquals(0, alpha(at(f, 39, 19)), "outside the bottom-right curve");
    }

    @Test
    void gradientFillInterpolatesAcrossItsStops() {
        Gradient g = Gradient.linear(0, 0, 10, 0, new float[]{0, 0.5f, 1}, new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF});
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 10, 4, RoundRect.Radii.ZERO), 0, 0, 10, 4, 0, g, false);
        assertEquals(0xFFFF0000, at(f, 0, 2));
        assertEquals(0xFF808000, at(f, 2.5f, 2), "halfway to the middle stop");
        assertEquals(0xFF00FF00, at(f, 5, 2));
        assertEquals(0xFF0000FF, at(f, 10, 2));
    }

    /** Two-color stripes 4px wide, hard stops between them, repeated across 16px. */
    private static final float[] STRIPE_OFFSETS = {0, 0.25f, 0.25f, 0.5f, 0.5f, 0.75f, 0.75f, 1};
    private static final int[] STRIPE_COLORS = {0xFFFF0000, 0xFFFF0000, 0xFF0000FF, 0xFF0000FF,
            0xFFFF0000, 0xFFFF0000, 0xFF0000FF, 0xFF0000FF};

    @Test
    void hardStopsKeepEachBandItsOwnColor() {
        Gradient g = Gradient.linear(0, 0, 16, 0, STRIPE_OFFSETS, STRIPE_COLORS);
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 16, 4, RoundRect.Radii.ZERO), 0, 0, 16, 4, 0, g, false);
        for (float x = 0.5f; x < 16; x += 1) {
            assertEquals(((int) x / 4) % 2 == 0 ? 0xFFFF0000 : 0xFF0000FF, at(f, x, 2), "at x = " + x);
        }
    }

    @Test
    void radialHardStopsDrawSharpRings() {
        Gradient g = new Gradient(Gradient.Kind.RADIAL, 20, 20, 16, 16, STRIPE_OFFSETS, STRIPE_COLORS);
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 40, 40, RoundRect.Radii.ZERO), 0, 0, 40, 40, 0, g, false);
        for (int a = 0; a < 360; a += 7) {
            for (float r = 0.5f; r < 16; r += 1) {
                if (Math.abs(r % 4 - 2) > 1.6f) {
                    continue;
                }
                float x = 20 + r * (float) Math.cos(Math.toRadians(a));
                float y = 20 + r * (float) Math.sin(Math.toRadians(a));
                assertEquals(((int) r / 4) % 2 == 0 ? 0xFFFF0000 : 0xFF0000FF, at(f, x, y), "at radius " + r + ", " + a + "°");
            }
        }
    }

    @Test
    void conicHardStopsDrawSharpSectors() {
        Gradient g = new Gradient(Gradient.Kind.CONIC, 20, 20, 0, 0, STRIPE_OFFSETS, STRIPE_COLORS);
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 40, 40, RoundRect.Radii.ZERO), 0, 0, 40, 40, 0, g, false);
        for (int a = 2; a < 360; a += 4) {
            if (a % 90 < 2 || a % 90 > 88) {
                continue;
            }
            double t = Math.toRadians(a);
            float x = 20 + 10 * (float) Math.sin(t);
            float y = 20 - 10 * (float) Math.cos(t);
            assertEquals((a / 90) % 2 == 0 ? 0xFFFF0000 : 0xFF0000FF, at(f, x, y), "at " + a + "°");
        }
    }

    @Test
    void outerShadowFadesAcrossTheEdgeAndLeavesTheBoxAlone() {
        RoundRect box = new RoundRect(20, 20, 40, 40, RoundRect.Radii.ZERO);
        DrawCommand.Shadow s = new DrawCommand.Shadow(box, 2, 0xFF000000, false, box, false);
        assertEquals(0, alpha(at(s, 40, 40)), "not under the box");
        // Sharp-edged masks have 1/4 px texels: the last one outside the box is centered 1/8 px out, Φ(-1/16).
        int edge = alpha(at(s, 19.95f, 40));
        assertTrue(Math.abs(edge - 128) <= 10, "half at the edge: " + edge);
        int near = alpha(at(s, 18, 40));
        assertTrue(near > 10 && near < edge, "fading out: " + near);
        assertEquals(0, alpha(at(s, 12, 40)), "beyond three standard deviations");
    }

    @Test
    void multicoloredBorderSplitsCornersAlongTheDiagonal() {
        RoundRect outer = new RoundRect(0, 0, 20, 20, radii(6, RoundRect.Corners.ROUND));
        DrawCommand.Border b = new DrawCommand.Border(outer, 4, 4, 4, 4, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00, false);
        assertEquals(0xFFFF0000, at(b, 10, 1), "top");
        assertEquals(0xFFFFFF00, at(b, 1, 10), "left");
        assertEquals(0xFFFF0000, at(b, 4, 2.2f), "top-left corner above the diagonal");
        assertEquals(0xFFFFFF00, at(b, 2.2f, 4), "top-left corner below the diagonal");
        assertEquals(0, alpha(at(b, 10, 10)), "the hole");
    }

    @Test
    void eachCornerShapeCutsItsOwnRadiusBox() {
        RoundRect.Corners corners = new RoundRect.Corners(CornerShape.BEVEL, CornerShape.NOTCH, CornerShape.SCOOP, CornerShape.SQUARE);
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 40, 40, radii(10, corners)), 0, 0, 40, 40, 0xFFFFFFFF, null, false);
        assertEquals(0, alpha(at(f, 5, 3)), "bevel: beyond the cut, inside a round curve");
        assertEquals(255, alpha(at(f, 7, 7)), "bevel: before the cut");
        assertEquals(0, alpha(at(f, 35, 5)), "notch: the radius box is cut away");
        assertEquals(255, alpha(at(f, 35, 12)), "notch: below the radius box");
        assertEquals(0, alpha(at(f, 39, 39)), "scoop: the box corner is bitten out");
        assertEquals(255, alpha(at(f, 32, 32)), "scoop: near the inner point");
        assertEquals(255, alpha(at(f, 0.5f, 39.5f)), "square: the corner stays");
    }

    @Test
    void crispMasksStepByWholeGuiPixelsWithoutAntialiasing() {
        RoundRect.Corners notch = new RoundRect.Corners(CornerShape.NOTCH, CornerShape.NOTCH, CornerShape.NOTCH, CornerShape.NOTCH);
        DrawCommand.Fill vanilla = new DrawCommand.Fill(new RoundRect(0, 0, 20, 10, radii(1, notch)), 0, 0, 20, 10, 0xFFFFFFFF, null, true);
        assertEquals(0, alpha(at(vanilla, 0.5f, 0.5f)), "notch 1px: the corner pixel is missing");
        assertEquals(255, alpha(at(vanilla, 1.5f, 0.5f)), "notch 1px: its neighbour is drawn");

        DrawCommand.Fill round = new DrawCommand.Fill(new RoundRect(0, 0, 20, 20, radii(6, RoundRect.Corners.ROUND)), 0, 0, 20, 20,
                0xFFFFFFFF, null, true);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                int a = alpha(at(round, x + 0.25f, y + 0.25f));
                assertTrue(a == 0 || a == 255, "no partial coverage at " + x + "," + y + ": " + a);
                assertEquals(a, alpha(at(round, x + 0.75f, y + 0.75f)), "one value across pixel " + x + "," + y);
            }
        }

        RoundRect box = new RoundRect(20, 20, 40, 40, RoundRect.Radii.ZERO);
        DrawCommand.Shadow s = new DrawCommand.Shadow(box, 2, 0xFF000000, false, box, true);
        int cell = alpha(at(s, 18.2f, 40));
        assertTrue(cell > 0 && cell < 255, "the halo is drawn: " + cell);
        assertEquals(cell, alpha(at(s, 18.8f, 40)), "one value across a halo pixel");
        assertTrue(alpha(at(s, 17.5f, 40)) < cell, "fading out pixel by pixel");
    }
}
