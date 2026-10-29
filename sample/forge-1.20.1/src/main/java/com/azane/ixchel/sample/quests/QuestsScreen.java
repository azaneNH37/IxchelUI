package com.azane.ixchel.sample.quests;

import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Quest tree sample (index only): chapters on the left, a canvas that pans and zooms, quests unlocked
 * by their prerequisites and claimed through the server.
 */
public final class QuestsScreen {
    private static final ResourceLocation TEMPLATE = new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/quests/tree.html");

    private QuestsScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(TEMPLATE, new QuestsView()));
    }
}
