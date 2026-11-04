package com.azane.ixchel.sample.env;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.mc.UiHud;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Environment HUD sample (S5): compass with waypoints, crosshair target, surroundings and vitals,
 * read per frame. Hidden until toggled from the sample index.
 */
public final class EnvHud {
    private static UiHud hud;

    private EnvHud() {
    }

    /** Client only; from client setup. */
    public static void register(IEventBus modBus) {
        hud = IxchelForge.registerHud(modBus, EnvModel.class, EnvModel::new)
                .show(false);
    }

    public static UiHud hud() {
        return hud;
    }
}
