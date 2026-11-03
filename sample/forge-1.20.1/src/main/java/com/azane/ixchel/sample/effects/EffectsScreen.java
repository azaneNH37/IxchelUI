package com.azane.ixchel.sample.effects;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;

/**
 * Visual effects sample: group {@code opacity} over overlapping boxes, items and text, side by side
 * with the same content faded element by element, and a card that fades as one.
 */
public final class EffectsScreen {
    private EffectsScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new EffectsView()));
    }
}
