package com.azane.ixchel.mc.source;

import com.azane.ixchel.core.binding.Source;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Sources for the local player. {@link ItemStack} has no {@code equals} and is modified in place, so
 * the item sources snapshot by copying and compare by item, tags and count; {@link #same} extends
 * that comparison to records and lists holding stacks. Without a player (menus, loading) they read
 * as empty.
 */
public final class ClientSources {
    private ClientSources() {
    }

    /**
     * Content equality that sees through {@link ItemStack}: stacks compare by item, tags and count;
     * records compare component by component, lists element by element, both with this method;
     * anything else with {@link Objects#equals}. Used by {@link #polled} and {@link #player} to
     * decide whether a polled value changed.
     */
    public static boolean same(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof ItemStack s) {
            return b instanceof ItemStack t && sameStack(s, t);
        }
        if (a instanceof Record) {
            if (a.getClass() != b.getClass()) {
                return false;
            }
            for (Method get : COMPONENTS.get(a.getClass())) {
                if (!same(component(get, a), component(get, b))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List<?> x) {
            if (!(b instanceof List<?> y) || x.size() != y.size()) {
                return false;
            }
            for (int i = 0, n = x.size(); i < n; i++) {
                if (!same(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    /**
     * A value polled once per frame and compared with {@link #same}, for records and lists that
     * hold stacks. Values are not copied: a value whose stack is mutated in place (the live stack of
     * a slot) looks unchanged, so store {@code stack.copy()} in it.
     *
     * <pre>{@code
     * record Row(ItemStack icon, int count) {}
     * final Value<List<Row>> rows = source(ClientSources.polled(() -> rowsOf(container)));
     * }</pre>
     */
    public static <T> Source<T> polled(Supplier<T> live) {
        return Source.polled(live, v -> v, ClientSources::same);
    }

    /**
     * A value read from the local player once per frame, or {@code absent} without a player.
     * Compared with {@link #same}; like {@link #polled}, values are not copied, so a record holding
     * a live stack must store {@code stack.copy()}.
     *
     * <pre>{@code
     * final Value<Float> health = source(ClientSources.player(LocalPlayer::getHealth, 0f));
     * }</pre>
     */
    public static <T> Source<T> player(Function<? super LocalPlayer, ? extends T> read, T absent) {
        return Source.polled(() -> {
            LocalPlayer p = Minecraft.getInstance().player;
            return p == null ? absent : read.apply(p);
        }, v -> v, ClientSources::same);
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
        return Source.polled(view::live, ClientSources::copyAll, ClientSources::same);
    }

    private static List<ItemStack> copyAll(List<ItemStack> live) {
        List<ItemStack> out = new ArrayList<>(live.size());
        for (ItemStack s : live) {
            out.add(s.copy());
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean sameStack(ItemStack a, ItemStack b) {
        return a.getCount() == b.getCount() && ItemStack.isSameItemSameTags(a, b);
    }

    /** Record component accessors per record class, made accessible once. */
    private static final ClassValue<Method[]> COMPONENTS = new ClassValue<>() {
        @Override
        protected Method[] computeValue(Class<?> type) {
            RecordComponent[] components = type.getRecordComponents();
            Method[] out = new Method[components.length];
            for (int i = 0; i < out.length; i++) {
                out[i] = components[i].getAccessor();
                out[i].setAccessible(true);
            }
            return out;
        }
    };

    private static Object component(Method get, Object record) {
        try {
            return get.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("reading " + get + " failed", e);
        }
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
