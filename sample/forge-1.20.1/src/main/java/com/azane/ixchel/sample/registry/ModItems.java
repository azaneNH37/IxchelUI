package com.azane.ixchel.sample.registry;

import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The sample's items, and where they go in the creative tabs. */
public final class ModItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, IxchelSampleMod.MOD_ID);

    public static final RegistryObject<Item> POWERED_STONECUTTER = ITEMS.register("powered_stonecutter",
            () -> new BlockItem(ModBlocks.POWERED_STONECUTTER.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                e.accept(POWERED_STONECUTTER);
            }
        });
    }
}
