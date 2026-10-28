package com.azane.ixchel.sample;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.env.EnvHud;
import com.azane.ixchel.sample.loot.LootClient;
import com.azane.ixchel.sample.loot.LootNetwork;
import com.azane.ixchel.sample.perf.BenchWorld;
import com.azane.ixchel.sample.perf.HudBench;
import com.azane.ixchel.sample.probe.ProbeClient;
import com.azane.ixchel.sample.refit.RefitClient;
import com.azane.ixchel.sample.refit.RefitMenus;
import com.azane.ixchel.sample.refit.RefitNetwork;
import com.azane.ixchel.sample.status.StatusHud;
import com.azane.ixchel.sample.waypoints.WaypointsClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

@Mod(IxchelSampleMod.MOD_ID)
public final class IxchelSampleMod {
    public static final String MOD_ID = "ixchelsample";

    public IxchelSampleMod(FMLJavaModLoadingContext context) {
        RefitNetwork.register();
        LootNetwork.register();
        RefitMenus.register(context.getModEventBus());
        if (FMLEnvironment.dist.isClient()) {
            ProbeClient.init(context.getModEventBus());
            IxchelForge.registerHud(context.getModEventBus(), MOD_ID, "ammo_hud",
                    "ui/ammo_hud.html", List.of("ui/ammo_hud.css"), AmmoHudView::new);
            BenchWorld.init();
            Checks.init();
            HudBench.init(context.getModEventBus());
            RefitClient.init(context.getModEventBus());
            Samples.initClient(context.getModEventBus());
            EnvHud.init(context.getModEventBus());
            StatusHud.init(context.getModEventBus());
            LootClient.init(context.getModEventBus());
            WaypointsClient.init(context.getModEventBus());
        }
    }
}
