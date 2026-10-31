package com.azane.ixchel.sample.loot;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Loot radar sample (S4): a HUD labeling the drops around the player, with edge arrows for those out
 * of view and the pickup toasts, and a list screen (U) sorted by distance with a rarity filter and a
 * pick-up button. The HUD is hidden until toggled from the sample index.
 */
public final class LootScreens {
    private static UiHud hud;

    private LootScreens() {
    }

    /** Client only; from client setup. */
    public static void register(IEventBus modBus) {
        hud = IxchelForge.registerHud(modBus, LootView.class, LootView::new)
                .show(false);
    }

    public static UiHud hud() {
        return hud;
    }

    public static void openList() {
        Minecraft.getInstance().setScreen(new UiScreen(new LootListView()));
    }
}
