package com.azane.ixchel.testmod.refit;

import com.azane.ixchel.testmod.IxchelTestMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registers {@link BackpackRefitMenu} and opens it on the server. */
public final class RefitMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, IxchelTestMod.MOD_ID);

    public static final RegistryObject<MenuType<BackpackRefitMenu>> BACKPACK = MENUS.register("backpack_refit",
            () -> IForgeMenuType.create((id, inv, buf) -> new BackpackRefitMenu(RefitMenus.BACKPACK.get(), id, inv, buf.readVarInt())));

    private RefitMenus() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }

    /** Opens the backpack refit on the crossbow in the player's selected hotbar slot. */
    static void open(ServerPlayer player) {
        int slot = player.getInventory().selected;
        if (!Attachments.isGun(player.getInventory().getItem(slot))) {
            return;
        }
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inv, p) -> new BackpackRefitMenu(BACKPACK.get(), id, inv, slot),
                Component.literal("Backpack refit")), buf -> buf.writeVarInt(slot));
    }
}
