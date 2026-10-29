package com.azane.ixchel.sample.storage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * One kind of stack in a storage: items that stack together, same item and same tag. The identity of
 * a storage entry on both sides; on the client one instance lives as long as the terminal's watch,
 * so its stack, name and mod id are made once.
 */
public final class StoredItem {
    private final Item item;
    private final CompoundTag tag;
    private ItemStack stack;
    private String name;
    private String mod;

    private StoredItem(Item item, CompoundTag tag) {
        this.item = item;
        this.tag = tag;
    }

    static StoredItem of(ItemStack stack) {
        return new StoredItem(stack.getItem(), stack.getTag());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(stack());
    }

    public static StoredItem decode(FriendlyByteBuf buf) {
        ItemStack s = buf.readItem();
        StoredItem item = of(s);
        item.stack = s;
        return item;
    }

    boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == item && Objects.equals(stack.getTag(), tag);
    }

    /** A new stack of {@code count}. */
    ItemStack stack(int count) {
        ItemStack s = new ItemStack(item, count);
        s.setTag(tag == null ? null : tag.copy());
        return s;
    }

    /** One of it, shared: never mutate. */
    ItemStack stack() {
        if (stack == null) {
            stack = stack(1);
        }
        return stack;
    }

    /** Client side: the display name. */
    String name() {
        if (name == null) {
            name = stack().getHoverName().getString();
        }
        return name;
    }

    String mod() {
        if (mod == null) {
            mod = BuiltInRegistries.ITEM.getKey(item).getNamespace();
        }
        return mod;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StoredItem s && s.item == item && Objects.equals(s.tag, tag);
    }

    @Override
    public int hashCode() {
        return 31 * item.hashCode() + Objects.hashCode(tag);
    }
}
