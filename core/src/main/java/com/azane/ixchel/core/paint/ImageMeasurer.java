package com.azane.ixchel.core.paint;

/**
 * Image metrics supplied by the host (the mc layer reads texture headers). The painter asks only for
 * a {@code background-image} whose {@code background-size} leaves a side {@code auto}.
 */
public interface ImageMeasurer {
    /**
     * The natural size of {@code src} in GUI pixels, or {@code null} if it cannot be loaded.
     *
     * @param src a resource location as written in CSS, e.g. {@code minecraft:textures/gui/icons.png}
     */
    Size naturalSize(String src);

    record Size(float width, float height) {
    }
}
