package com.azane.ixchel.sample.refit;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Backpack-style refit: one slot per attachment type, backed by the gun's tag, plus the player
 * inventory. The gun is the stack in inventory slot {@link #gunSlot} when the menu opened; its slot
 * cannot be picked up from, and the server closes the menu once that exact stack leaves it.
 *
 * <p>Slot indices: {@code 0..2} attachments in {@link Attachments#TYPES} order, {@code 3..29} the
 * main inventory, {@code 30..38} the hotbar.
 */
public final class BackpackRefitMenu extends AbstractContainerMenu {
    public static final int ATTACHMENTS = Attachments.TYPES.size();
    public static final int INVENTORY_START = ATTACHMENTS;
    public static final int HOTBAR_START = INVENTORY_START + 27;
    public static final int SLOT_COUNT = HOTBAR_START + 9;

    final int gunSlot;
    private final Inventory inventory;
    private final ItemStack openedOn;

    /** Client side: positions come from the layout, so every slot starts at 0,0. */
    public BackpackRefitMenu(MenuType<?> type, int id, Inventory inventory, int gunSlot) {
        super(type, id);
        this.inventory = inventory;
        this.gunSlot = gunSlot;
        this.openedOn = inventory.getItem(gunSlot);
        Supplier<ItemStack> gun = () -> inventory.getItem(gunSlot);
        Container attachments = new AttachmentContainer(gun);
        for (int i = 0; i < ATTACHMENTS; i++) {
            Attachments.Type t = Attachments.TYPES.get(i);
            addSlot(new Slot(attachments, i, 0, 0) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Attachments.isGun(gun.get()) && optionOf(t, stack) != null;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int i = 9; i < 36; i++) {
            addSlot(inventorySlot(i));
        }
        for (int i = 0; i < 9; i++) {
            addSlot(inventorySlot(i));
        }
    }

    private Slot inventorySlot(int index) {
        if (index != gunSlot) {
            return new Slot(inventory, index, 0, 0);
        }
        return new Slot(inventory, index, 0, 0) {
            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        };
    }

    public int gunSlot() {
        return gunSlot;
    }

    /** The exact stack the menu opened on is still in its slot (server side this is identity). */
    @Override
    public boolean stillValid(Player player) {
        return inventory.getItem(gunSlot) == openedOn && Attachments.isGun(openedOn);
    }

    /** Attachments go to their type's slot, everything else between inventory and hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack before = stack.copy();
        boolean moved;
        if (index < ATTACHMENTS) {
            moved = moveItemStackTo(stack, INVENTORY_START, SLOT_COUNT, true);
        } else {
            int target = attachmentSlotFor(stack);
            if (target >= 0 && !slots.get(target).hasItem()) {
                moved = moveItemStackTo(stack, target, target + 1, false);
            } else if (index < HOTBAR_START) {
                moved = moveItemStackTo(stack, HOTBAR_START, SLOT_COUNT, false);
            } else {
                moved = moveItemStackTo(stack, INVENTORY_START, HOTBAR_START, false);
            }
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        return before;
    }

    private static int attachmentSlotFor(ItemStack stack) {
        for (int i = 0; i < ATTACHMENTS; i++) {
            if (optionOf(Attachments.TYPES.get(i), stack) != null) {
                return i;
            }
        }
        return -1;
    }

    static Attachments.Option optionOf(Attachments.Type type, ItemStack stack) {
        for (Attachments.Option o : type.options()) {
            if (stack.is(o.item())) {
                return o;
            }
        }
        return null;
    }

    /**
     * The gun's attachments as a container. Reads and writes go to the live gun stack, so both the
     * client's prediction and the server's authoritative change land in the gun's tag.
     */
    private static final class AttachmentContainer extends SimpleContainer {
        private final Supplier<ItemStack> gun;

        AttachmentContainer(Supplier<ItemStack> gun) {
            super(ATTACHMENTS);
            this.gun = gun;
        }

        @Override
        public ItemStack getItem(int index) {
            ItemStack g = gun.get();
            if (!Attachments.isGun(g)) {
                return ItemStack.EMPTY;
            }
            Attachments.Type t = Attachments.TYPES.get(index);
            Attachments.Option o = Attachments.option(t.options(), Attachments.installed(g, t.id()));
            return o == null ? ItemStack.EMPTY : new ItemStack(o.item());
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            ItemStack g = gun.get();
            if (!Attachments.isGun(g)) {
                return;
            }
            Attachments.Type t = Attachments.TYPES.get(index);
            Attachments.Option o = stack.isEmpty() ? null : optionOf(t, stack);
            Attachments.setInstalled(g, t.id(), o == null ? "" : o.id());
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = getItem(index);
            if (current.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }
            setItem(index, ItemStack.EMPTY);
            return current;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            return removeItem(index, 1);
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < ATTACHMENTS; i++) {
                if (!getItem(i).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
