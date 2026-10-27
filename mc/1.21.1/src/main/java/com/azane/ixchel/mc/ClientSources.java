package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.Source;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Sources for the local player. {@link ItemStack} has no {@code equals} and is modified in place, so
 * the item sources snapshot by copying and compare by item, tags and count. Without a player (menus,
 * loading) they read as empty.
 */
public final class ClientSources {
    private ClientSources() {
    }

    /**
     * A value read from the local player once per frame, or {@code absent} without a player. For
     * immutable values compared with {@code equals} (numbers, strings, records and lists of them);
     * stacks go through the item sources below.
     *
     * <pre>{@code
     * final Value<Float> health = source(ClientSources.player(LocalPlayer::getHealth, 0f));
     * }</pre>
     */
    public static <T> Source<T> player(Function<? super LocalPlayer, ? extends T> read, T absent) {
        return Source.polled(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p == null ? absent : read.apply(p);
        });
    }

    /** The stack in the main hand; follows the selected hotbar slot. */
    public static Source<ItemStack> mainHand() {
        return Source.polled(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p == null ? ItemStack.EMPTY : p.getMainHandItem();
        }, ItemStack::copy, ClientSources::sameStack);
    }

    /**
     * The stack in inventory slot {@code slot}, locked to that slot: switching the selected hotbar slot
     * does not change it. An editing screen opened on the held item uses this with the selected slot
     * at open; whether the stack is still the item being edited is the view model's call.
     */
    public static Source<ItemStack> inventorySlot(int slot) {
        return Source.polled(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p == null ? ItemStack.EMPTY : p.getInventory().getItem(slot);
        }, ItemStack::copy, ClientSources::sameStack);
    }

    /**
     * Every inventory slot in {@link Inventory} order (hotbar and main, armor, offhand); empty
     * without a player. The list is an unmodifiable snapshot.
     */
    public static Source<List<ItemStack>> inventory() {
        InventoryView view = new InventoryView();
        return Source.polled(view::live, ClientSources::copyAll, ClientSources::sameStacks);
    }

    private static List<ItemStack> copyAll(List<ItemStack> live) {
        List<ItemStack> out = new ArrayList<>(live.size());
        for (ItemStack s : live) {
            out.add(s.copy());
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean sameStacks(List<ItemStack> snapshot, List<ItemStack> live) {
        if (snapshot.size() != live.size()) {
            return false;
        }
        for (int i = 0, n = live.size(); i < n; i++) {
            if (!sameStack(snapshot.get(i), live.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
    }

    /** The live inventory without copying, reused every poll; only the comparison and the snapshot see it. */
    private static final class InventoryView extends AbstractList<ItemStack> {
        private Inventory inventory;

        List<ItemStack> live() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null) {
                return List.of();
            }
            inventory = p.getInventory();
            return this;
        }

        @Override
        public ItemStack get(int index) {
            return inventory.getItem(index);
        }

        @Override
        public int size() {
            return inventory.getContainerSize();
        }
    }
}
