package com.azane.ixchel.sample.character;

import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Character panel sample (S1): the player's own inventory menu laid out by a document, with tabs for
 * equipment (paper doll, armor, offhand, crafting), attributes, effects and statistics. Opened with G,
 * next to vanilla's E inventory rather than replacing it (#009).
 */
public final class CharacterScreen {
    private static final ResourceLocation TEMPLATE = new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/character/panel.html");

    private CharacterScreen() {
    }

    /**
     * Opens the panel on the local player. Its inventory menu needs no open packet: like vanilla's
     * inventory screen, its container id is 0, which the server always has open.
     */
    public static void open() {
        LocalPlayer player = Minecraft.getInstance().player;
        Minecraft.getInstance().setScreen(new UiContainerScreen<>(player.inventoryMenu, player.getInventory(),
                player.getDisplayName(), TEMPLATE, new CharacterView()));
    }
}
