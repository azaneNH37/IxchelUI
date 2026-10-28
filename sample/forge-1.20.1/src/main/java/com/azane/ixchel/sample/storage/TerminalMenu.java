package com.azane.ixchel.sample.storage;

import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.registry.ModItems;
import com.azane.ixchel.sample.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The storage terminal's menu. Its slots are the player's inventory only (0-26 the main
 * inventory, 27-35 the hotbar); the storage is not slots but a list the server sends as a
 * {@link StorageSyncPacket}: everything at first, then only what changed. Ids are per menu and
 * name one kind of stack (item and tag) for as long as the menu is open.
 */
public final class TerminalMenu extends AbstractContainerMenu {
    static final int INVENTORY_START = 0;
    static final int HOTBAR_START = 27;
    static final int SLOT_COUNT = 36;
    /** Server ticks between scans. */
    private static final int SCAN_TICKS = 5;

    /** Client side: one kind of stack in the storage. */
    record Entry(int id, ItemStack stack, long count, String name, String mod) {
    }

    // Server side.
    private final ServerPlayer player;
    private final BlockPos center;
    /** The hand holding the terminal, or null when the sample index opened it. */
    private final InteractionHand hand;
    private final Map<Storage.Key, Integer> ids = new HashMap<>();
    private final Map<Integer, Storage.Key> keys = new HashMap<>();
    /** What the client has been told, per id. */
    private final Map<Integer, Long> sent = new HashMap<>();
    private int nextId;
    private int ticks;

    // Client side.
    private final Map<Integer, Entry> entries = new HashMap<>();
    private List<Entry> contents = List.of();

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

    /** Client side: the storage's contents, in no particular order; a new list after every change. */
    List<Entry> contents() {
        return contents;
    }

    /** Client side. */
    void apply(List<StorageSyncPacket.Change> changes) {
        for (StorageSyncPacket.Change c : changes) {
            if (c.count() == 0) {
                entries.remove(c.id());
            } else if (c.prototype() != null) {
                ItemStack s = c.prototype();
                entries.put(c.id(), new Entry(c.id(), s, c.count(), s.getHoverName().getString(),
                        BuiltInRegistries.ITEM.getKey(s.getItem()).getNamespace()));
            } else {
                Entry e = entries.get(c.id());
                if (e != null) {
                    entries.put(c.id(), new Entry(e.id(), e.stack(), c.count(), e.name(), e.mod()));
                }
            }
        }
        contents = List.copyOf(entries.values());
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player != null && ticks++ % SCAN_TICKS == 0) {
            sync();
        }
    }

    /** Sends what changed since the last sync; the first one sends everything. */
    private void sync() {
        List<StorageSyncPacket.Change> changes = new ArrayList<>();
        Map<Integer, Long> now = new HashMap<>();
        for (Map.Entry<Storage.Key, Long> e : storage().counts().entrySet()) {
            Storage.Key key = e.getKey();
            int id = ids.computeIfAbsent(key, k -> {
                keys.put(nextId, k);
                return nextId++;
            });
            now.put(id, e.getValue());
            Long before = sent.get(id);
            if (before == null) {
                changes.add(new StorageSyncPacket.Change(id, key.stack(1), e.getValue()));
            } else if (!before.equals(e.getValue())) {
                changes.add(new StorageSyncPacket.Change(id, null, e.getValue()));
            }
        }
        for (Integer id : sent.keySet()) {
            if (!now.containsKey(id)) {
                changes.add(new StorageSyncPacket.Change(id, null, 0));
            }
        }
        sent.clear();
        sent.putAll(now);
        if (!changes.isEmpty()) {
            Network.sendTo(player, new StorageSyncPacket(containerId, changes));
        }
    }

    private Storage storage() {
        return Storage.around(player.serverLevel(), center);
    }

    /** Server side: takes one stack, half of one or a stack into the inventory; see {@link TakePacket.Mode}. */
    void take(int id, TakePacket.Mode mode) {
        Storage.Key key = keys.get(id);
        Long count = sent.get(id);
        if (key == null || count == null) {
            return;
        }
        int max = key.stack(1).getMaxStackSize();
        Storage storage = storage();
        switch (mode) {
            case STACK, HALF -> {
                if (!getCarried().isEmpty()) {
                    return;
                }
                long want = mode == TakePacket.Mode.STACK ? max : (Math.min(max, count) + 1) / 2;
                setCarried(storage.extract(key, (int) Math.min(want, count)));
            }
            case TO_INVENTORY -> {
                ItemStack stack = storage.extract(key, max);
                player.getInventory().add(stack);
                if (!stack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(storage.insert(stack));
                }
            }
        }
        sync();
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
        sync();
    }

    /** A shift-click on an inventory slot stores the stack; only the server can, the client waits for its slot update. */
    @Override
    public ItemStack quickMoveStack(Player who, int index) {
        Slot slot = slots.get(index);
        if (player != null && slot.hasItem()) {
            slot.set(storage().insert(slot.getItem()));
            sync();
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
