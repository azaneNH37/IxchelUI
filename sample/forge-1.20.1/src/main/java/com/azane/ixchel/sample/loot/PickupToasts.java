package com.azane.ixchel.sample.loot;

import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pickup toasts, the loot HUD's log: one row per item type whose count in the inventory grew, merged
 * while it keeps growing, gone {@value #SHOW_MS} ms after the last change; at most {@value #MAX}, the
 * oldest going first. {@link #read} runs in a source: the list instance only changes when a toast is
 * added, merged or expired, so bindings stay idle in between. Time is {@link Util#getMillis()}, which
 * a recording steps frame by frame.
 */
final class PickupToasts {
    static final long SHOW_MS = 3000;
    static final int MAX = 5;

    /**
     * @param counts the merged count, alone: the page keys the count text on it, so a merge re-creates
     *               it and its bump animation plays again
     * @param shown  when the toast was last added to or merged, alone: keys the lifetime sweep the same way
     */
    record Toast(int id, ItemStack stack, String name, List<Integer> counts, List<Long> shown) {
        int count() {
            return counts.get(0);
        }
    }

    private final Map<Item, Integer> counts = new HashMap<>();
    private final Map<Item, Integer> ids = new HashMap<>();
    private final List<Toast> live = new ArrayList<>();
    private List<Toast> current = List.of();
    private int nextId;
    private boolean primed;

    List<Toast> read(LocalPlayer p) {
        long now = Util.getMillis();
        boolean changed = diff(p.getInventory(), now);
        changed |= live.removeIf(t -> now - t.shown().get(0) >= SHOW_MS);
        while (live.size() > MAX) {
            live.remove(0);
            changed = true;
        }
        if (changed) {
            ids.keySet().retainAll(live.stream().map(t -> t.stack().getItem()).toList());
            current = List.copyOf(live);
        }
        return current;
    }

    /** Updates {@link #counts}; the first read only records, so a join does not toast the whole bag. */
    private boolean diff(Inventory inv, long now) {
        Map<Item, Integer> seen = new HashMap<>();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                seen.merge(s.getItem(), s.getCount(), Integer::sum);
            }
        }
        boolean changed = false;
        if (primed) {
            for (Map.Entry<Item, Integer> e : seen.entrySet()) {
                int gained = e.getValue() - counts.getOrDefault(e.getKey(), 0);
                if (gained > 0) {
                    add(e.getKey(), gained, now);
                    changed = true;
                }
            }
        }
        primed = true;
        counts.clear();
        counts.putAll(seen);
        return changed;
    }

    /** A merge moves the toast to the newest end, where the eye already is. */
    private void add(Item item, int gained, long now) {
        int total = gained;
        Integer id = ids.get(item);
        if (id != null) {
            for (int i = 0; i < live.size(); i++) {
                if (live.get(i).id() == id) {
                    total += live.remove(i).count();
                    break;
                }
            }
        } else {
            id = nextId++;
            ids.put(item, id);
        }
        ItemStack stack = new ItemStack(item);
        live.add(new Toast(id, stack, stack.getHoverName().getString(), List.of(total), List.of(now)));
    }
}
