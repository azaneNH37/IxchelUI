package com.azane.ixchel.sample.machine;

import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers the powered stonecutter: block, item, block entity and menu. */
public final class Machine {
    static final String NAME = "powered_stonecutter";

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, IxchelSampleMod.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, IxchelSampleMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, IxchelSampleMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, IxchelSampleMod.MOD_ID);

    public static final RegistryObject<Block> BLOCK = BLOCKS.register(NAME,
            () -> new MachineBlock(BlockBehaviour.Properties.copy(Blocks.STONECUTTER)));
    public static final RegistryObject<Item> ITEM = ITEMS.register(NAME, () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    @SuppressWarnings("DataFlowIssue") // The data fixer type is unused by mods.
    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register(NAME,
            () -> BlockEntityType.Builder.of(MachineBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<MenuType<MachineMenu>> MENU = MENUS.register(NAME,
            () -> IForgeMenuType.create((id, inv, buf) -> new MachineMenu(id, inv, buf.readBlockPos())));

    private Machine() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                e.accept(ITEM);
            }
        });
    }

    /** Distance squared within which the menu stays open, as vanilla containers. */
    static boolean inReach(BlockPos pos, double x, double y, double z) {
        return pos.distToCenterSqr(x, y, z) <= 64;
    }
}
