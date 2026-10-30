package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.text.TextMeasurer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Vanilla font metrics: CSS {@code 9px} is the font at scale 1 (line height 9, baseline 7). Bold glyphs
 * are one pixel wider.
 */
public final class McTextMeasurer implements TextMeasurer {
    private static final float VANILLA_SIZE = 9f;
    private static final float VANILLA_BASELINE = 7f;
    private static final Style BOLD = Style.EMPTY.withBold(true);

    private final Font font;

    public McTextMeasurer(Font font) {
        this.font = font;
    }

    @Override
    public float width(String text, float size, boolean bold) {
        int w = bold ? font.width(FormattedCharSequence.forward(text, BOLD)) : font.width(text);
        return w * size / VANILLA_SIZE;
    }

    @Override
    public float lineHeight(float size) {
        return font.lineHeight * size / VANILLA_SIZE;
    }

    @Override
    public float baseline(float size) {
        return VANILLA_BASELINE * size / VANILLA_SIZE;
    }
}
