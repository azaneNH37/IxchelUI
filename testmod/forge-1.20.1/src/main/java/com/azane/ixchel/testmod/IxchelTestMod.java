package com.azane.ixchel.testmod;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.testmod.perf.BenchWorld;
import com.azane.ixchel.testmod.perf.HudBench;
import com.azane.ixchel.testmod.probe.ProbeClient;
import com.azane.ixchel.testmod.refit.RefitClient;
import com.azane.ixchel.testmod.refit.RefitNetwork;
import com.azane.ixchel.testmod.refit.RefitMenus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.List;

@Mod(IxchelTestMod.MOD_ID)
public final class IxchelTestMod {
    public static final String MOD_ID = "ixcheltest";

    public IxchelTestMod(FMLJavaModLoadingContext context) {
        RefitNetwork.register();
        RefitMenus.register(context.getModEventBus());
        if (FMLEnvironment.dist.isClient()) {
            ProbeClient.init(context.getModEventBus());
            IxchelForge.registerHud(context.getModEventBus(), MOD_ID, "ammo_hud",
                    "ui/ammo_hud.html", List.of("ui/ammo_hud.css"), AmmoHudView::new);
            BenchWorld.init();
            HudBench.init(context.getModEventBus());
            RefitClient.init(context.getModEventBus());
        }
    }
}
