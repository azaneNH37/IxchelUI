package com.azane.ixchel.sample;

import com.azane.ixchel.neoforge.IxchelNeoForge;
import com.azane.ixchel.sample.refit.RefitClient;
import com.azane.ixchel.sample.refit.RefitNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(IxchelSampleMod.MOD_ID)
public final class IxchelSampleMod {
    public static final String MOD_ID = "ixchel_sample";

    public IxchelSampleMod(IEventBus modBus, Dist dist) {
        RefitNetwork.register(modBus);
        if (dist.isClient()) {
            IxchelNeoForge.registerHud(modBus, AmmoHudModel.class, AmmoHudModel::new);
            RefitClient.init(modBus);
        }
    }
}
