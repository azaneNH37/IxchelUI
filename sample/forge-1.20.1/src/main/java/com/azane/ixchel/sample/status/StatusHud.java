package com.azane.ixchel.sample.status;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.Sample;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

/** Effects HUD sample: active effects with time left, left side (the vitals are in the environment HUD). */
public final class StatusHud {
    static final String ID = "status";
    private static IxchelForge.Hud hud;

    private StatusHud() {
    }

    public static void init(IEventBus modBus) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        hud = IxchelForge.registerHud(modBus, IxchelSampleMod.MOD_ID, ID, "ui/status.html", List.of("ui/status.css"),
                StatusView::new);
        hud.show(false);
        StatusCheck.init(hud);
    }

    public static Sample sample() {
        return Sample.hud(ID, "Effects HUD", "Active effects with their icon and time left; effects in their "
                + "last ten seconds turn red.",
                new ItemStack(Items.POTION), hud);
    }
}
