package com.azane.ixchel.testmod;

import com.azane.ixchel.neoforge.IxchelNeoForge;
import com.azane.ixchel.testmod.probe.ProbeClient;
import com.azane.ixchel.testmod.refit.RefitClient;
import com.azane.ixchel.testmod.refit.RefitNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import java.util.List;

@Mod(IxchelTestMod.MOD_ID)
public final class IxchelTestMod {
    public static final String MOD_ID = "ixcheltest";

    public IxchelTestMod(IEventBus modBus, Dist dist) {
        RefitNetwork.register(modBus);
        if (dist.isClient()) {
            ProbeClient.init(modBus);
            IxchelNeoForge.registerHud(modBus, MOD_ID, "ammo_hud",
                    "ui/ammo_hud.html", List.of("ui/ammo_hud.css"), AmmoHudView::new);
            RefitClient.init(modBus);
        }
    }
}
