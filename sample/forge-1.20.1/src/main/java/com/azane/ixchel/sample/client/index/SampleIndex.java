package com.azane.ixchel.sample.client.index;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;

/** The sample index screen (H): one row per entry of {@link Samples#all()}. */
public final class SampleIndex {
    private SampleIndex() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new SampleIndexView(Samples.all())));
    }
}
