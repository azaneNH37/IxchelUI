package com.azane.ixchel.core.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rasterizes the meshes point by point (bilinear texel filtering, as the hosts sample the atlas) and checks the coverage they draw. */
class ShapeTessellatorTest {
    private final MaskAtlas atlas = new MaskAtlas(() -> {
    });
    private final ShapeTessellator tess = new ShapeTessellator(atlas);

    /** The drawn ARGB at {@code (x, y)}: vertex color times mask alpha, or 0 where no quad covers it. */
    private int at(DrawCommand c, float x, float y) {
        float[] m = tess.mesh(c);
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
                float mask = sample(u * MaskAtlas.SIZE - 0.5f, v * MaskAtlas.SIZE - 0.5f);
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
    private float sample(float x, float y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        float fx = x - x0;
        float fy = y - y0;
        float top = texel(x0, y0) * (1 - fx) + texel(x0 + 1, y0) * fx;
        float bottom = texel(x0, y0 + 1) * (1 - fx) + texel(x0 + 1, y0 + 1) * fx;
        return (top * (1 - fy) + bottom * fy) / 255f;
    }

    private int texel(int x, int y) {
        int cx = Math.max(0, Math.min(MaskAtlas.SIZE - 1, x));
        int cy = Math.max(0, Math.min(MaskAtlas.SIZE - 1, y));
        return atlas.pixels()[cy * MaskAtlas.SIZE + cx] & 0xFF;
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    @Test
    void roundedFillCoversTheInsideOfTheCurveOnly() {
        RoundRect.Radii r = new RoundRect.Radii(10, 10, 10, 10, 10, 10, 10, 10);
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 40, 20, r), 0, 0, 40, 20, 0xFFFFFFFF, null);
        assertEquals(0, alpha(at(f, 1, 1)), "outside the top-left curve");
        assertEquals(255, alpha(at(f, 20, 10)), "the middle");
        assertEquals(255, alpha(at(f, 20, 0.2f)), "the straight top edge");
        assertEquals(255, alpha(at(f, 4, 4)), "inside the curve");
        assertEquals(0, alpha(at(f, 39, 19)), "outside the bottom-right curve");
    }

    @Test
    void gradientFillInterpolatesAcrossItsStops() {
        Gradient g = new Gradient(0, 0, 10, 0, new float[]{0, 0.5f, 1}, new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF});
        DrawCommand.Fill f = new DrawCommand.Fill(new RoundRect(0, 0, 10, 4, RoundRect.Radii.ZERO), 0, 0, 10, 4, 0, g);
        assertEquals(0xFFFF0000, at(f, 0, 2));
        assertEquals(0xFF808000, at(f, 2.5f, 2), "halfway to the middle stop");
        assertEquals(0xFF00FF00, at(f, 5, 2));
        assertEquals(0xFF0000FF, at(f, 10, 2));
    }

    @Test
    void outerShadowFadesAcrossTheEdgeAndLeavesTheBoxAlone() {
        RoundRect box = new RoundRect(20, 20, 40, 40, RoundRect.Radii.ZERO);
        DrawCommand.Shadow s = new DrawCommand.Shadow(box, 2, 0xFF000000, false, box);
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
        RoundRect outer = new RoundRect(0, 0, 20, 20, new RoundRect.Radii(6, 6, 6, 6, 6, 6, 6, 6));
        DrawCommand.Border b = new DrawCommand.Border(outer, 4, 4, 4, 4, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00);
        assertEquals(0xFFFF0000, at(b, 10, 1), "top");
        assertEquals(0xFFFFFF00, at(b, 1, 10), "left");
        assertEquals(0xFFFF0000, at(b, 4, 2.2f), "top-left corner above the diagonal");
        assertEquals(0xFFFFFF00, at(b, 2.2f, 4), "top-left corner below the diagonal");
        assertEquals(0, alpha(at(b, 10, 10)), "the hole");
    }
}
