package com.azane.ixchel.testmod;

import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.ClientSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Stand-in for TACZ gun data: the main-hand stack is the "gun", its count the loaded ammo. The icon
 * is a paint-time handle; the stack is a value only because the counts and the name derive from it.
 */
public final class AmmoHudView extends ViewModel {
    final Value<Supplier<ItemStack>> gunIcon = state(AmmoHudView::mainHand);
    final Value<ItemStack> gun = source(ClientSources.mainHand());
    final Value<List<ItemStack>> inventory = source(ClientSources.inventory());
    final Derived<Integer> loaded = derive(gun, ItemStack::getCount);
    final Derived<String> loadedText = derive(loaded, n -> String.format("%03d", n));
    final Derived<Boolean> isEmpty = derive(loaded, n -> n == 0);
    final Derived<String> reserve = derive(gun, inventory, (g, inv) -> String.format("%04d", reserve(g, inv)));
    final Derived<Component> name = derive(gun, ItemStack::getHoverName);

    /** Matching stacks in {@code inventory}, not counting the gun itself. */
    public static int reserve(ItemStack gun, List<ItemStack> inventory) {
        if (gun.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack s : inventory) {
            if (ItemStack.isSameItemSameComponents(s, gun)) {
                total += s.getCount();
            }
        }
        return total - gun.getCount();
    }

    private static ItemStack mainHand() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getMainHandItem();
    }
}
