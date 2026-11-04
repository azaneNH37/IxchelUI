package com.azane.ixchel.sample.storage;

import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Storage terminal sample (S3): a {@link UiContainerScreen} over {@link TerminalMenu}, whose slots
 * are only the player's inventory. The storage is a virtual grid over the list the server syncs;
 * take and store go back as intent packets. The inventory scrolls, so its slot items are drawn by
 * Ixchel to be clipped. Item and menu are registered in {@code registry}.
 */
public final class TerminalScreen {
    private TerminalScreen() {
    }

    /** Client only; from client setup, after registries are ready. */
    public static void registerScreen() {
        MenuScreens.register(ModMenus.STORAGE_TERMINAL.get(), (TerminalMenu menu, Inventory inv, Component title) ->
                new UiContainerScreen<>(menu, inv, title, new TerminalModel(menu), UiContainerScreen.SlotItemRendering.IXCHEL));
    }

    /** The sample index's entry: opens the terminal on the storage around the player ({@link OpenTerminalPacket}). */
    public static void openNearby() {
        Network.sendToServer(new OpenTerminalPacket());
    }
}
