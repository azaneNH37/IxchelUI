package com.azane.ixchel.mc.paint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrispSizeTest {
    // Fails if a size that is exactly a step rounds down a step through float error (6px at GUI scale 3
    // is 1.9999… screen pixels per glyph pixel), if rounding goes up past the size written, or if a
    // size below one screen pixel per glyph pixel vanishes instead of staying at one.
    @Test
    void textSizeStepsDownToWholeScreenPixelsPerGlyphPixelAndNeverBelowOne() {
        assertEquals(6f, McTextMeasurer.crispSize(6, 3), 1e-5f);
        assertEquals(9f, McTextMeasurer.crispSize(9, 3), 1e-5f);
        assertEquals(4.5f, McTextMeasurer.crispSize(7, 2), 1e-5f);
        assertEquals(6.75f, McTextMeasurer.crispSize(7, 4), 1e-5f);
        assertEquals(9f, McTextMeasurer.crispSize(6, 1), 1e-5f);
        assertEquals(18f, McTextMeasurer.crispSize(20, 1), 1e-5f);
    }
}
