package com.azane.ixchel.sample.client.index;

import com.azane.ixchel.mc.UiHud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * One entry of the sample index (H): a screen sample opens when clicked, a HUD sample is shown or
 * hidden. Its title and description are {@code ixchel_sample.index.<id>.title} and {@code .description}.
 *
 * @param hud the HUD layer, or {@code null} for a screen sample
 */
public record Sample(String id, ItemStack icon, UiHud hud, Runnable open) {
    public static Sample screen(String id, ItemStack icon, Runnable open) {
        return new Sample(id, icon, null, open);
    }

    public static Sample hud(String id, ItemStack icon, UiHud hud) {
        return new Sample(id, icon, hud, () -> hud.show(!hud.shown()));
    }

    public Component title() {
        return Component.translatable("ixchel_sample.index." + id + ".title");
    }

    public Component description() {
        return Component.translatable("ixchel_sample.index." + id + ".description");
    }
}
