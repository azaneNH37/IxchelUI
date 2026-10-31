package com.azane.ixchel.sample.tooltips;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;

/**
 * Element tooltips sample: one page where every kind of element carries an {@code <ix-tooltip>}, items
 * in the three tooltip modes, and the edges: a scrolled list, nesting, a component, a live value and
 * a dialog.
 */
public final class TooltipsScreen {
    private TooltipsScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new TooltipsView()));
    }
}
