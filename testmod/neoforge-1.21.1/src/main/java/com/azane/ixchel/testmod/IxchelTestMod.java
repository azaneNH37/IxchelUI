package com.azane.ixchel.testmod;

import com.azane.ixchel.neoforge.IxchelNeoForge;
import com.azane.ixchel.testmod.probe.ProbeClient;
import com.azane.ixchel.testmod.refit.RefitClient;
import com.azane.ixchel.testmod.refit.RefitNetwork;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(IxchelTestMod.MOD_ID)
public final class IxchelTestMod {
    public static final String MOD_ID = "ixcheltest";

    public IxchelTestMod(IEventBus modBus, Dist dist) {
        RefitNetwork.register(modBus);
        if (dist.isClient()) {
            ProbeClient.init(modBus);
            IxchelNeoForge.registerHud(modBus, ResourceLocation.fromNamespaceAndPath(MOD_ID, "ui/ammo_hud.html"),
                    AmmoHudView::new);
            RefitClient.init(modBus);
        }
    }
}
