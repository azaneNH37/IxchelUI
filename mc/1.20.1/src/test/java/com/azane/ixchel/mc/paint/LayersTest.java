package com.azane.ixchel.mc.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayersTest {
    private static final float ITEM_DEPTH = 160;

    /** Places a command as McPainter does and returns the level it drew at. */
    private static float place(Layers layers, float x, float y, float w, float h, float depth) {
        float z = layers.level(x, y, x + w, y + h);
        layers.add(x, y, x + w, y + h, z + depth);
        return z;
    }

    // Fails if the depth grows with the number of cells: a large grid would pass the GUI far plane
    // and hide everything painted after it.
    @Test
    void aGridOfItemsWithCountsCostsOneItemsDepth() {
        Layers layers = new Layers();
        for (int i = 0; i < 1300; i++) {
            float x = i % 9 * 18;
            float y = i / 9 * 18;
            assertEquals(0, place(layers, x + 1, y + 1, 16, 16, ITEM_DEPTH), "item " + i);
            assertEquals(ITEM_DEPTH, place(layers, x + 9, y + 11, 8, 6, 0), "count " + i);
        }
        assertEquals(ITEM_DEPTH, layers.top());
        // What follows the grid, away from it, is not raised.
        assertEquals(0, place(layers, 0, 1300 / 9 * 18 + 40, 162, 54, 0));
    }

    // Fails if a command can draw below something earlier it overlaps (it would be hidden), including
    // through a chain of overlaps, or if a mere shared edge raises it.
    @Test
    void aCommandDrawsAboveEverythingEarlierItOverlaps() {
        Layers layers = new Layers();
        place(layers, 0, 0, 16, 16, ITEM_DEPTH);
        assertEquals(ITEM_DEPTH, place(layers, 10, 10, 20, 9, 0), "text over the item");
        assertEquals(ITEM_DEPTH, place(layers, 20, 15, 10, 10, 0), "rect over the text only");
        assertEquals(ITEM_DEPTH, place(layers, 25, 20, 16, 16, ITEM_DEPTH), "item over that rect");
        assertEquals(2 * ITEM_DEPTH, place(layers, -100, -100, 400, 400, 0), "overlay over all");
        assertEquals(0, new Layers().level(0, 0, 1, 1));
        Layers edges = new Layers();
        place(edges, 0, 0, 16, 16, ITEM_DEPTH);
        assertEquals(0, place(edges, 16, 0, 16, 16, 0), "right neighbour");
        assertEquals(0, place(edges, 0, 16, 16, 16, 0), "lower neighbour");
    }

    // Fails if boxes outside the bucket grid (hidden slots far off-screen, wide screens) are lost, or
    // if clear leaves the previous paint's levels behind.
    @Test
    void boxesBeyondTheGridAndClear() {
        Layers layers = new Layers();
        place(layers, -2000, -2000, 16, 16, ITEM_DEPTH);
        place(layers, 3000, 3000, 16, 16, ITEM_DEPTH);
        assertEquals(ITEM_DEPTH, layers.level(-1990, -1990, -1980, -1980));
        assertEquals(ITEM_DEPTH, layers.level(3010, 3010, 3020, 3020));
        assertEquals(0, layers.level(-1900, -1990, -1880, -1980));
        layers.clear();
        assertEquals(0, layers.level(-1990, -1990, -1980, -1980));
        assertEquals(0, layers.top());
    }
}
