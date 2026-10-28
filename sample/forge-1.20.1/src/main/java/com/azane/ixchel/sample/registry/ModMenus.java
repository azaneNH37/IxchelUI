package com.azane.ixchel.sample.registry;

import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.machine.MachineMenu;
import com.azane.ixchel.sample.storage.TerminalMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The sample's menus. */
public final class ModMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, IxchelSampleMod.MOD_ID);

    /** The client reads the machine's block position from the open packet. */
    public static final RegistryObject<MenuType<MachineMenu>> POWERED_STONECUTTER = MENUS.register("powered_stonecutter",
            () -> IForgeMenuType.create((id, inv, buf) -> new MachineMenu(id, inv, buf.readBlockPos())));
    /** The storage is synced by the menu itself; the open packet carries nothing. */
    public static final RegistryObject<MenuType<TerminalMenu>> STORAGE_TERMINAL = MENUS.register("storage_terminal",
            () -> IForgeMenuType.create((id, inv, buf) -> new TerminalMenu(id, inv)));

    private ModMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
