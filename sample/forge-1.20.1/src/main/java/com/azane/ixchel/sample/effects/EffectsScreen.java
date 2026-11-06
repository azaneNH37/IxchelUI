package com.azane.ixchel.sample.effects;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;

/**
 * Visual effects sample: group {@code opacity} over overlapping boxes, items and text, side by side
 * with the same content faded element by element, and a card that fades as one; filter functions,
 * blend modes and backdrop filters; shader effects from {@code .ixeffect} files, two of the library's
 * and one of the sample's own; one cell per newer CSS feature and a ported CodePen.
 */
public final class EffectsScreen {
    private EffectsScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new EffectsModel()));
    }
}
