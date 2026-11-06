package com.azane.ixchel.mc.playtest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FramingTest {
    // Fails if a whole-window take of an odd-sized window gives an odd size, which yuv420p refuses.
    @Test
    void wholeWindowIsCutToEvenSizes() {
        assertArrayEquals(new int[]{0, 0, 1280, 720}, Framing.pixels(null, 2, 1280, 720));
        assertArrayEquals(new int[]{0, 0, 1280, 720}, Framing.pixels(null, 2, 1281, 721));
    }

    // Fails if a region is scaled by anything but the GUI scale, or cut inward through its edge pixels.
    @Test
    void regionGrowsToWholeWindowPixels() {
        assertArrayEquals(new int[]{20, 40, 200, 100}, Framing.pixels(new float[]{10, 20, 100, 50}, 2, 1280, 720));
        // 10.4 to 110.6 at scale 2: pixels 20 to 222.
        assertArrayEquals(new int[]{20, 41, 202, 102}, Framing.pixels(new float[]{10.4f, 20.6f, 100.2f, 50.8f}, 2, 1280, 720));
    }

    // Fails if a region hanging over the window's edge is not cut to the window.
    @Test
    void regionIsCutToTheWindow() {
        assertArrayEquals(new int[]{0, 0, 100, 60}, Framing.pixels(new float[]{-10, -20, 60, 50}, 2, 1280, 720));
        assertArrayEquals(new int[]{1200, 660, 80, 60}, Framing.pixels(new float[]{600, 330, 100, 100}, 2, 1280, 720));
    }

    // Fails if a region off the window goes on to a zero-sized video instead of failing the take.
    @Test
    void regionOutsideTheWindowIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Framing.pixels(new float[]{2000, 2000, 10, 10}, 2, 1280, 720));
    }

    // Fails if supersampling's output size is not half the window, or comes out odd.
    @Test
    void outputIsScaledAndEven() {
        assertArrayEquals(new int[]{1280, 720}, Framing.output(1280, 720, 1));
        assertArrayEquals(new int[]{1280, 720}, Framing.output(2560, 1440, 0.5));
        assertArrayEquals(new int[]{100, 50}, Framing.output(202, 102, 0.5));
    }
}
