package com.azane.ixchel.sample.loot;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pickup toasts, the radar HUD's log: one row per item type whose count in the inventory grew,
 * merged while it keeps growing, gone {@value #SHOW_MS} ms after the last change. {@link #read} runs
 * in a source: the list instance only changes when a toast is added, updated or expired, so
 * bindings stay idle in between.
 */
final class PickupToasts {
    static final long SHOW_MS = 3000;
    static final long FADE_MS = 600;

    record Toast(int id, ItemStack stack, int count, String text, boolean fading) {
        static Toast of(int id, ItemStack stack, int count, boolean fading) {
            return new Toast(id, stack, count, "+" + count + " " + stack.getHoverName().getString(), fading);
        }
    }

    private final Map<Item, Integer> counts = new HashMap<>();
    private final Map<Item, Integer> ids = new HashMap<>();
    private final List<Toast> live = new ArrayList<>();
    private final List<Long> shownAt = new ArrayList<>();
    private List<Toast> current = List.of();
    private int nextId;
    private boolean primed;

    List<Toast> read(LocalPlayer p) {
        long now = System.currentTimeMillis();
        boolean changed = diff(p.getInventory(), now);
        for (int i = live.size() - 1; i >= 0; i--) {
            long age = now - shownAt.get(i);
            Toast t = live.get(i);
            if (age >= SHOW_MS) {
                live.remove(i);
                shownAt.remove(i);
                ids.remove(t.stack().getItem());
                changed = true;
            } else if (!t.fading() && age >= SHOW_MS - FADE_MS) {
                live.set(i, Toast.of(t.id(), t.stack(), t.count(), true));
                changed = true;
            }
        }
        if (changed) {
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

    private void add(Item item, int gained, long now) {
        int total = gained;
        Integer id = ids.get(item);
        if (id != null) {
            for (int i = 0; i < live.size(); i++) {
                if (live.get(i).id() == id) {
                    total += live.get(i).count();
                    live.remove(i);
                    shownAt.remove(i);
                    break;
                }
            }
        } else {
            id = nextId++;
            ids.put(item, id);
        }
        live.add(Toast.of(id, new ItemStack(item), total, false));
        shownAt.add(now);
    }
}
