package com.azane.ixchel.sample.client.index;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** The sample index screen (H): one row per entry of {@link Samples#all()}. */
public final class SampleIndex {
    private SampleIndex() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/index/index.html"),
                new SampleIndexView(Samples.all())));
    }
}
