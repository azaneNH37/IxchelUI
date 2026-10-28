package com.azane.ixchel.sample.registry;

import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The sample's blocks. */
public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, IxchelSampleMod.MOD_ID);

    public static final RegistryObject<Block> POWERED_STONECUTTER = BLOCKS.register("powered_stonecutter",
            () -> new MachineBlock(BlockBehaviour.Properties.copy(Blocks.STONECUTTER)));

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
