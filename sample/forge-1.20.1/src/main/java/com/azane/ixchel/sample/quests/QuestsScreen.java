package com.azane.ixchel.sample.quests;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;

/**
 * Quest tree sample (index only): chapters on the left, a canvas that pans and zooms, quests unlocked
 * by their prerequisites and claimed through the server.
 */
public final class QuestsScreen {
    private QuestsScreen() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new QuestsModel()));
    }
}
