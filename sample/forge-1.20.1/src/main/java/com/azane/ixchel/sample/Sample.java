package com.azane.ixchel.sample;

import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * One entry of the sample index (H). A screen sample opens when clicked; a HUD sample is shown or
 * hidden, and its view model reads {@link #shown} through a polled source.
 *
 * @param open opens the screen, or for a HUD flips whether it is shown
 */
public record Sample(String id, String title, String description, ItemStack icon, boolean hud, Runnable open) {
    private static final Set<String> SHOWN = new HashSet<>();

    public static Sample screen(String id, String title, String description, ItemStack icon, Runnable open) {
        return new Sample(id, title, description, icon, false, open);
    }

    public static Sample hud(String id, String title, String description, ItemStack icon) {
        return new Sample(id, title, description, icon, true, () -> show(id, !shown(id)));
    }

    /** Whether the HUD sample {@code id} is shown; all start hidden. Client thread only. */
    public static boolean shown(String id) {
        return SHOWN.contains(id);
    }

    public static void show(String id, boolean shown) {
        if (shown) {
            SHOWN.add(id);
        } else {
            SHOWN.remove(id);
        }
    }
}
