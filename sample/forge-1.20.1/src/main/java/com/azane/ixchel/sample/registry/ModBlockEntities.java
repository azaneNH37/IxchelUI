package com.azane.ixchel.sample.registry;

import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.machine.MachineBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The sample's block entities. */
public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, IxchelSampleMod.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // The data fixer type is unused by mods.
    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> POWERED_STONECUTTER =
            BLOCK_ENTITIES.register("powered_stonecutter",
                    () -> BlockEntityType.Builder.of(MachineBlockEntity::new, ModBlocks.POWERED_STONECUTTER.get()).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
