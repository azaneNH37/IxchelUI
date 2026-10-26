package com.azane.ixchel.testmod;

import com.azane.ixchel.testmod.probe.ProbeClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(IxchelTestMod.MOD_ID)
public final class IxchelTestMod {
    public static final String MOD_ID = "ixcheltest";

    public IxchelTestMod(IEventBus modBus, Dist dist) {
        if (dist.isClient()) {
            ProbeClient.init(modBus);
        }
    }
}
