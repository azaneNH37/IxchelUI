package com.azane.ixchel.sample;

import com.azane.ixchel.sample.client.ClientSetup;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.registry.ModBlockEntities;
import com.azane.ixchel.sample.registry.ModBlocks;
import com.azane.ixchel.sample.registry.ModItems;
import com.azane.ixchel.sample.registry.ModMenus;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Sample and development host mod for Ixchel UI on Forge 1.20.1. A normal mod layout: registries
 * ({@code registry}), the network channel ({@code network}), client wiring ({@code client}: keys,
 * screens, HUDs and the sample index on H) and one package per sample ({@code character},
 * {@code env}, {@code loot}, {@code machine}, {@code waypoints}), each with a single public entry
 * class. The unattended checks are a second mod in the {@code check} source set.
 */
@Mod(IxchelSampleMod.MOD_ID)
public final class IxchelSampleMod {
    public static final String MOD_ID = "ixchelsample";

    public IxchelSampleMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        Network.register();
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.init(modBus);
        }
    }
}
