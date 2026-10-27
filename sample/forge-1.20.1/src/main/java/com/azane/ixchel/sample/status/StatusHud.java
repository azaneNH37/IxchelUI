package com.azane.ixchel.sample.status;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.Sample;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

/** Status HUD sample: health, armor, food, experience and effects, top-left. */
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
        return Sample.hud(ID, "Status HUD", "Hearts, armor and food from vanilla's icon sheet, the experience bar, "
                + "and active effects with time left; effects in their last ten seconds turn red.",
                new ItemStack(Items.GOLDEN_APPLE), hud);
    }
}
