package com.azane.ixchel.mc.playtest;

/**
 * Where a take reads the window and how big its video is. Video sizes are even both ways, as
 * {@code yuv420p} needs.
 */
final class Framing {
    private Framing() {
    }

    /**
     * The window pixels a take reads, {@code {x, y, width, height}} from the top left: the whole
     * window for a {@code null} region, else the GUI rectangle {@code region} grown to whole pixels
     * and cut to the window; both sizes even (an odd last row or column is left out).
     *
     * @throws IllegalArgumentException when the region leaves less than 2×2 pixels inside the window
     */
    static int[] pixels(float[] region, double guiScale, int windowWidth, int windowHeight) {
        int x0 = 0;
        int y0 = 0;
        int x1 = windowWidth;
        int y1 = windowHeight;
        if (region != null) {
            x0 = Math.max(0, (int) Math.floor(region[0] * guiScale));
            y0 = Math.max(0, (int) Math.floor(region[1] * guiScale));
            x1 = Math.min(windowWidth, (int) Math.ceil((region[0] + region[2]) * guiScale));
            y1 = Math.min(windowHeight, (int) Math.ceil((region[1] + region[3]) * guiScale));
        }
        int width = Math.max(0, x1 - x0) & ~1;
        int height = Math.max(0, y1 - y0) & ~1;
        if (width < 2 || height < 2) {
            throw new IllegalArgumentException("the take's region leaves no picture inside the window");
        }
        return new int[]{x0, y0, width, height};
    }

    /**
     * The video's size for {@code width × height} window pixels drawn at {@code scale} (below 1:
     * a big window scaled down, supersampled), both even and at least 2.
     */
    static int[] output(int width, int height, double scale) {
        if (scale == 1) {
            return new int[]{width, height};
        }
        return new int[]{Math.max(2, (int) Math.round(width * scale) & ~1), Math.max(2, (int) Math.round(height * scale) & ~1)};
    }
}
