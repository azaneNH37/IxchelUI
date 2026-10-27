package com.azane.ixchel.sample;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.perf.BenchWorld;
import com.azane.ixchel.sample.perf.HudBench;
import com.azane.ixchel.sample.probe.ProbeClient;
import com.azane.ixchel.sample.refit.RefitClient;
import com.azane.ixchel.sample.refit.RefitNetwork;
import com.azane.ixchel.sample.refit.RefitMenus;
import com.azane.ixchel.sample.pickup.PickupHud;
import com.azane.ixchel.sample.status.StatusHud;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

@Mod(IxchelSampleMod.MOD_ID)
public final class IxchelSampleMod {
    public static final String MOD_ID = "ixchelsample";

    public IxchelSampleMod(FMLJavaModLoadingContext context) {
        RefitNetwork.register();
        RefitMenus.register(context.getModEventBus());
        if (FMLEnvironment.dist.isClient()) {
            ProbeClient.init(context.getModEventBus());
            IxchelForge.registerHud(context.getModEventBus(), MOD_ID, "ammo_hud",
                    "ui/ammo_hud.html", List.of("ui/ammo_hud.css"), AmmoHudView::new);
            BenchWorld.init();
            HudBench.init(context.getModEventBus());
            RefitClient.init(context.getModEventBus());
            Samples.initClient(context.getModEventBus());
            StatusHud.init(context.getModEventBus());
            PickupHud.init(context.getModEventBus());
        }
    }
}
