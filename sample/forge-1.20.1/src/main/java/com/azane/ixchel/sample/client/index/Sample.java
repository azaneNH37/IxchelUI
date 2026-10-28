package com.azane.ixchel.sample.client.index;

import com.azane.ixchel.mc.UiHud;
import net.minecraft.world.item.ItemStack;

/**
 * One entry of the sample index (H): a screen sample opens when clicked, a HUD sample is shown or
 * hidden.
 *
 * @param hud the HUD layer, or {@code null} for a screen sample
 */
public record Sample(String id, String title, String description, ItemStack icon, UiHud hud, Runnable open) {
    public static Sample screen(String id, String title, String description, ItemStack icon, Runnable open) {
        return new Sample(id, title, description, icon, null, open);
    }

    public static Sample hud(String id, String title, String description, ItemStack icon, UiHud hud) {
        return new Sample(id, title, description, icon, hud, () -> hud.show(!hud.shown()));
    }
}
