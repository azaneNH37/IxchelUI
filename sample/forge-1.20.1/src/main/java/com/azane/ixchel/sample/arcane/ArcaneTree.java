package com.azane.ixchel.sample.arcane;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;

/**
 * Arcane tree sample: the holo theme recolored violet by its variables, and its panel frame
 * restated, as a mod would re-skin a theme. Arts burn in with {@code Dissolve} as their parent is
 * learned. Client only.
 */
public final class ArcaneTree {
    private ArcaneTree() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new ArcaneModel()));
    }
}
