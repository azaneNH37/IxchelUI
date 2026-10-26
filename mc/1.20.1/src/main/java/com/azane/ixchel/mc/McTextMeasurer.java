package com.azane.ixchel.mc;

import com.azane.ixchel.core.text.TextMeasurer;
import net.minecraft.client.gui.Font;

/** Vanilla font metrics: CSS {@code 9px} is the font at scale 1 (line height 9). */
public final class McTextMeasurer implements TextMeasurer {
    private static final float VANILLA_SIZE = 9f;

    private final Font font;

    public McTextMeasurer(Font font) {
        this.font = font;
    }

    @Override
    public float width(String text, float size) {
        return font.width(text) * size / VANILLA_SIZE;
    }

    @Override
    public float lineHeight(float size) {
        return font.lineHeight * size / VANILLA_SIZE;
    }
}
