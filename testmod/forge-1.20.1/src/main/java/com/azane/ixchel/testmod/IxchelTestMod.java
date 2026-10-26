package com.azane.ixchel.testmod;

import com.azane.ixchel.testmod.probe.ProbeClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(IxchelTestMod.MOD_ID)
public final class IxchelTestMod {
    public static final String MOD_ID = "ixcheltest";

    public IxchelTestMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist.isClient()) {
            ProbeClient.init(context.getModEventBus());
        }
    }
}
