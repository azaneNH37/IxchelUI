package com.azane.ixchel.core.paint;

import java.util.HashMap;
import java.util.Map;

/**
 * The coverage masks {@link ShapeTessellator} draws through, packed into one square alpha texture.
 * The host uploads {@link #pixels()} as a white texture with this alpha, filtered linearly, and
 * re-uploads the {@linkplain #dirty() dirty} rectangle before drawing with it. Each mask is surrounded
 * by a copy of its edge texels, so linear filtering never reads a neighbour. A 4×4 block at the
 * origin is opaque, for solid areas.
 *
 * <p>When a new mask does not fit, the atlas runs the reset hook (the host draws what it has queued
 * with the current contents), forgets every mask and starts over; {@link #generation()} then changes,
 * which invalidates every texture coordinate handed out before.
 */
public final class MaskAtlas {
    public static final int SIZE = 1024;
    /** Largest mask side in texels, padding excluded; callers stay below it. */
    public static final int MAX_MASK = 256;
    /** Texel coordinate (both axes) of an opaque texel's center. */
    public static final float OPAQUE = 2;

    /** Fills {@code out[j * width + i]} with the coverage of texel {@code (i, j)}, 0 to 1. */
    public interface Baker {
        void bake(float[] out, int width, int height);
    }

    private final byte[] alpha = new byte[SIZE * SIZE];
    private final Map<Object, int[]> regions = new HashMap<>();
    private final Runnable beforeReset;
    private float[] scratch = new float[64];
    private int shelfX;
    private int shelfY;
    private int shelfHeight;
    private int dirtyX0;
    private int dirtyY0;
    private int dirtyX1;
    private int dirtyY1;
    private int generation;

    /** @param beforeReset runs before the masks are dropped to make room, while their coordinates are still valid */
    public MaskAtlas(Runnable beforeReset) {
        this.beforeReset = beforeReset;
        reset();
    }

    /** Row-major texel alphas, {@link #SIZE} by {@link #SIZE}. */
    public byte[] pixels() {
        return alpha;
    }

    public int generation() {
        return generation;
    }

    /** Whether texels changed since {@link #clean()}; they lie in the dirty rectangle. */
    public boolean dirty() {
        return dirtyX1 > dirtyX0;
    }

    public int dirtyX() {
        return dirtyX0;
    }

    public int dirtyY() {
        return dirtyY0;
    }

    public int dirtyWidth() {
        return dirtyX1 - dirtyX0;
    }

    public int dirtyHeight() {
        return dirtyY1 - dirtyY0;
    }

    /** Marks the texels uploaded. */
    public void clean() {
        dirtyX0 = SIZE;
        dirtyY0 = SIZE;
        dirtyX1 = 0;
        dirtyY1 = 0;
    }

    /**
     * The top-left texel of the mask for {@code key}, baking it when new; {@code width} and
     * {@code height} are at most {@link #MAX_MASK}. May reset the atlas (see the class comment). Beside
     * the tessellator's shapes, the host bakes the glyphs of the fonts it rasterizes itself here.
     */
    public int[] region(Object key, int width, int height, Baker baker) {
        int[] r = regions.get(key);
        if (r != null) {
            return r;
        }
        r = allocate(width + 2, height + 2);
        if (r == null) {
            beforeReset.run();
            reset();
            r = allocate(width + 2, height + 2);
        }
        int n = width * height;
        if (scratch.length < n) {
            scratch = new float[n];
        }
        baker.bake(scratch, width, height);
        int x0 = r[0];
        int y0 = r[1];
        for (int j = -1; j <= height; j++) {
            int sj = Math.max(0, Math.min(height - 1, j));
            int row = (y0 + 1 + j) * SIZE + x0 + 1;
            for (int i = -1; i <= width; i++) {
                int si = Math.max(0, Math.min(width - 1, i));
                float v = scratch[sj * width + si];
                alpha[row + i] = (byte) Math.round(Math.max(0, Math.min(1, v)) * 255);
            }
        }
        markDirty(x0, y0, width + 2, height + 2);
        int[] inner = {x0 + 1, y0 + 1};
        regions.put(key, inner);
        return inner;
    }

    private int[] allocate(int w, int h) {
        if (shelfX + w > SIZE) {
            shelfY += shelfHeight;
            shelfX = 0;
            shelfHeight = 0;
        }
        if (shelfY + h > SIZE) {
            return null;
        }
        int[] r = {shelfX, shelfY};
        shelfX += w;
        shelfHeight = Math.max(shelfHeight, h);
        return r;
    }

    private void reset() {
        regions.clear();
        generation++;
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                alpha[j * SIZE + i] = (byte) 255;
            }
        }
        shelfX = 4;
        shelfY = 0;
        shelfHeight = 4;
        clean();
        markDirty(0, 0, 4, 4);
    }

    private void markDirty(int x, int y, int w, int h) {
        dirtyX0 = Math.min(dirtyX0, x);
        dirtyY0 = Math.min(dirtyY0, y);
        dirtyX1 = Math.max(dirtyX1, x + w);
        dirtyY1 = Math.max(dirtyY1, y + h);
    }
}
