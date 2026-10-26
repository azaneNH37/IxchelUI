package com.azane.ixchel.testmod;

import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.ClientSources;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Stand-in for TACZ gun data: the main-hand stack is the "gun", its count the loaded ammo. */
final class AmmoHudView extends ViewModel {
    final Value<ItemStack> gun = source(ClientSources.mainHand());
    final Value<List<ItemStack>> inventory = source(ClientSources.inventory());
    final Derived<Integer> loaded = derive(gun, ItemStack::getCount);
    final Derived<String> loadedText = derive(loaded, n -> String.format("%03d", n));
    final Derived<Boolean> isEmpty = derive(loaded, n -> n == 0);
    final Derived<String> reserve = derive(gun, inventory, (g, inv) -> {
        int total = 0;
        for (ItemStack s : inv) {
            if (!g.isEmpty() && ItemStack.isSameItemSameTags(s, g)) {
                total += s.getCount();
            }
        }
        return String.format("%04d", total - g.getCount());
    });
    final Derived<Component> name = derive(gun, ItemStack::getHoverName);
}
