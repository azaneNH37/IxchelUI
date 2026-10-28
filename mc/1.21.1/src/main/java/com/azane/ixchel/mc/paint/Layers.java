package com.azane.ixchel.mc.paint;

import java.util.Arrays;

/**
 * The depth each command of one paint draws at. With the depth test on, a command paints over an
 * earlier one only from at least its depth, so a command draws at the highest depth reached by the
 * earlier commands it overlaps, and at 0 where it overlaps none. The depth then grows with how many
 * drawings stack at one spot, not with how many there are: a grid of items with a count over each
 * costs one item's depth, while raising everything after each item passed the far plane of the GUI
 * projection (about z 10000) after about 70 cells.
 *
 * <p>Only commands that reach above 0 are recorded, in a grid of buckets; coordinates beyond the grid
 * fall into its edge buckets. Overlap is exact and edges that only touch do not overlap, so
 * neighbouring cells never raise each other.
 */
final class Layers {
    private static final int BUCKET = 32;
    private static final int GRID = 64;
    private static final int FIELDS = 5;

    /** x0, y0, x1, y1 and the depth reached, per recorded command. */
    private float[] boxes = new float[FIELDS * 64];
    private int count;
    private final int[][] buckets = new int[GRID * GRID][];
    private final int[] sizes = new int[GRID * GRID];
    private float top;

    void clear() {
        if (count > 0) {
            Arrays.fill(sizes, 0);
            count = 0;
        }
        top = 0;
    }

    boolean isEmpty() {
        return count == 0;
    }

    /** The highest depth anything recorded reached. */
    float top() {
        return top;
    }

    /** The depth a command covering the box draws at: the highest reached by a recorded command it overlaps. */
    float level(float x0, float y0, float x1, float y1) {
        if (count == 0) {
            return 0;
        }
        float level = 0;
        int bx1 = bucket(x1);
        int by1 = bucket(y1);
        for (int by = bucket(y0); by <= by1; by++) {
            for (int bx = bucket(x0); bx <= bx1; bx++) {
                int b = by * GRID + bx;
                int[] ids = buckets[b];
                for (int i = 0, n = sizes[b]; i < n; i++) {
                    int o = ids[i] * FIELDS;
                    if (boxes[o + 4] > level && boxes[o] < x1 && x0 < boxes[o + 2] && boxes[o + 1] < y1 && y0 < boxes[o + 3]) {
                        level = boxes[o + 4];
                    }
                }
            }
        }
        return level;
    }

    /** Records a command drawn in the box whose depth reached {@code reached}; one at 0 needs no record. */
    void add(float x0, float y0, float x1, float y1, float reached) {
        if (reached <= 0) {
            return;
        }
        if (FIELDS * (count + 1) > boxes.length) {
            boxes = Arrays.copyOf(boxes, boxes.length * 2);
        }
        int o = count * FIELDS;
        boxes[o] = x0;
        boxes[o + 1] = y0;
        boxes[o + 2] = x1;
        boxes[o + 3] = y1;
        boxes[o + 4] = reached;
        int bx1 = bucket(x1);
        int by1 = bucket(y1);
        for (int by = bucket(y0); by <= by1; by++) {
            for (int bx = bucket(x0); bx <= bx1; bx++) {
                int b = by * GRID + bx;
                int[] ids = buckets[b];
                if (ids == null) {
                    ids = buckets[b] = new int[8];
                } else if (sizes[b] == ids.length) {
                    ids = buckets[b] = Arrays.copyOf(ids, ids.length * 2);
                }
                ids[sizes[b]++] = count;
            }
        }
        count++;
        top = Math.max(top, reached);
    }

    private static int bucket(float v) {
        return Math.max(0, Math.min(GRID - 1, (int) Math.floor(v / BUCKET)));
    }
}
