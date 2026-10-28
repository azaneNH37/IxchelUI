package com.azane.ixchel.sample.loot;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.UiScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Loot radar sample (S4): a HUD labeling the drops around the player, with edge arrows for those out
 * of view and the pickup toasts, and a list screen (U) sorted by distance with a rarity filter and a
 * pick-up button. The HUD is hidden until toggled from the sample index.
 */
public final class LootScreens {
    private static final ResourceLocation LIST = new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/loot/list.html");
    private static UiHud hud;

    private LootScreens() {
    }

    /** Client only; from client setup. */
    public static void register(IEventBus modBus) {
        hud = IxchelForge.registerHud(modBus, new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/loot/hud.html"), LootView::new)
                .show(false);
    }

    public static UiHud hud() {
        return hud;
    }

    public static void openList() {
        Minecraft.getInstance().setScreen(new UiScreen(LIST, new LootListView()));
    }
}
