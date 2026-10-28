package com.azane.ixchel.sample.env;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Environment HUD sample (S5): compass with waypoints, crosshair target, surroundings and vitals,
 * read per frame. Hidden until toggled from the sample index.
 */
public final class EnvHud {
    /** The layer id Forge knows the HUD by: the template path without {@code .html}. */
    public static final ResourceLocation LAYER = new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/env/hud");
    private static UiHud hud;

    private EnvHud() {
    }

    /** Client only; from client setup. */
    public static void register(IEventBus modBus) {
        hud = IxchelForge.registerHud(modBus, new ResourceLocation(IxchelSampleMod.MOD_ID, "ui/env/hud.html"), EnvView::new)
                .show(false);
    }

    public static UiHud hud() {
        return hud;
    }
}
