package com.azane.ixchel.sample.pickup;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.Sample;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

/** Pickup toasts sample: items gained since the last frame, bottom-left, gone after three seconds. */
public final class PickupHud {
    static final String ID = "pickup";
    private static IxchelForge.Hud hud;

    private PickupHud() {
    }

    public static void init(IEventBus modBus) {
        if (!FMLEnvironment.dist.isClient()) {
            return;
        }
        hud = IxchelForge.registerHud(modBus, IxchelSampleMod.MOD_ID, ID, "ui/pickup.html", List.of("ui/pickup.css"),
                PickupView::new);
        hud.show(false);
        PickupCheck.init(hud);
    }

    public static Sample sample() {
        return Sample.hud(ID, "Pickup toasts", "A toast per item type gained, merged while it keeps coming, "
                + "fading out after three seconds. The diff runs inside the source read.",
                new ItemStack(Items.HOPPER), hud);
    }
}
