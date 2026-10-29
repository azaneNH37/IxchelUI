package com.azane.ixchel.sample.storage;

import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.network.Replication;
import com.azane.ixchel.sample.registry.ModItems;
import com.azane.ixchel.sample.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * The storage terminal's menu. Its slots are the player's inventory only (0-26 the main
 * inventory, 27-35 the hotbar); the storage is not slots but a collection replicated by the menu's
 * container id ({@link Network#STORAGE}), visible while this menu is open.
 */
public final class TerminalMenu extends AbstractContainerMenu {
    static final int INVENTORY_START = 0;
    static final int HOTBAR_START = 27;
    static final int SLOT_COUNT = 36;

    // Server side.
    private final ServerPlayer player;
    private final BlockPos center;
    /** The hand holding the terminal, or null when the sample index opened it. */
    private final InteractionHand hand;

    /** Client side, from the open packet; the menu type in {@code registry} creates it. */
    public TerminalMenu(int id, Inventory inventory) {
        this(id, inventory, null, null, null);
    }

    private TerminalMenu(int id, Inventory inventory, ServerPlayer player, BlockPos center, InteractionHand hand) {
        super(ModMenus.STORAGE_TERMINAL.get(), id);
        this.player = player;
        this.center = center;
        this.hand = hand;
        for (int i = 9; i < 36; i++) {
            addSlot(new Slot(inventory, i, 0, 0));
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(inventory, i, 0, 0));
        }
    }

    /** Server side: the storage around the player now; with a hand, valid while it holds a terminal. */
    static TerminalMenu server(int id, Inventory inventory, ServerPlayer player, InteractionHand hand) {
        return new TerminalMenu(id, inventory, player, player.blockPosition(), hand);
    }

    /** What {@code player} sees of the storage: how many of each item, while they have this terminal open. */
    public static Map<StoredItem, Long> view(ServerPlayer player, int containerId) {
        return player.containerMenu instanceof TerminalMenu menu && menu.containerId == containerId && menu.player != null
                ? menu.storage().counts() : null;
    }

    private Storage storage() {
        return Storage.around(player.serverLevel(), center);
    }

    /** The client sees the result with the slot updates it causes. */
    private void replicate() {
        Replication.refresh(player, Network.STORAGE, containerId);
    }

    /** Server side: takes one stack, half of one or a stack into the inventory; see {@link TakePacket.Mode}. */
    void take(StoredItem item, TakePacket.Mode mode) {
        int max = item.stack().getMaxStackSize();
        Storage storage = storage();
        switch (mode) {
            case STACK, HALF -> {
                if (!getCarried().isEmpty()) {
                    return;
                }
                int want = mode == TakePacket.Mode.STACK ? max
                        : (int) (Math.min(max, storage.counts().getOrDefault(item, 0L)) + 1) / 2;
                setCarried(storage.extract(item, want));
            }
            case TO_INVENTORY -> {
                ItemStack stack = storage.extract(item, max);
                player.getInventory().add(stack);
                if (!stack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(storage.insert(stack));
                }
            }
        }
        replicate();
    }

    /** Server side: puts the carried stack, or one of it, into the storage. */
    void store(boolean one) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        if (one) {
            ItemStack rest = storage().insert(carried.copyWithCount(1));
            if (rest.isEmpty()) {
                carried.shrink(1);
            }
        } else {
            setCarried(storage().insert(carried));
        }
        replicate();
    }

    /** A shift-click on an inventory slot stores the stack; only the server can, the client waits for its slot update. */
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (player != null && slot.hasItem()) {
            slot.set(storage().insert(slot.getItem()));
            replicate();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player who) {
        if (hand != null) {
            return who.getItemInHand(hand).is(ModItems.STORAGE_TERMINAL.get());
        }
        return center == null || who.blockPosition().distSqr(center) <= Storage.RADIUS * Storage.RADIUS;
    }
}
