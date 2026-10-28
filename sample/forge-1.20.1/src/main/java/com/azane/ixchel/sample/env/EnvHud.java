package com.azane.ixchel.sample.env;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.Sample;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

/** Environment HUD sample (S5): compass with waypoints, crosshair target, surroundings and vitals, read per frame. */
public final class EnvHud {
    static final String ID = "env";
    private static IxchelForge.Hud hud;

    private EnvHud() {
    }

    public static void init(IEventBus modBus) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        hud = IxchelForge.registerHud(modBus, IxchelSampleMod.MOD_ID, ID, "ui/env.html", List.of("ui/env.css"),
                EnvView::new);
        hud.show(false);
        EnvCheck.init(hud);
    }

    public static Sample sample() {
        return Sample.hud(ID, "Environment HUD", "A compass bar with the waypoints (J), what the crosshair is on "
                + "(block with tool and states, or an entity's health), biome, time, weather, light, position; "
                + "hearts, armor, food, experience.",
                new ItemStack(Items.SPYGLASS), hud);
    }
}
