package com.azane.ixchel.sample.storage;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.network.Replica;
import com.azane.ixchel.sample.network.Replication;
import com.azane.ixchel.sample.network.Replication.Entry;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The terminal screen. The grid is virtual: of the filtered and sorted list only the rows in view
 * (and {@link #SPARE_ROWS} more) become elements; a content box as tall as all rows keeps the scroll
 * range, and its top padding puts the built rows where they belong.
 */
@UiTemplate("ixchelsample:ui/storage/terminal.html")
final class TerminalView extends ViewModel {
    static final int COLUMNS = 9;
    static final int VISIBLE_ROWS = 6;
    static final int CELL = 18;
    /** Built beyond the view: the rows move one frame after the scroll offset, by up to a wheel step or two. */
    static final int SPARE_ROWS = 2;

    enum SortKey {
        NAME("Name"), COUNT("Count"), MOD("Mod");

        final String label;

        SortKey(String label) {
            this.label = label;
        }
    }

    record Tile(int id, StoredItem item, ItemStack stack, String count) {
    }

    private final TerminalMenu menu;

    final Value<String> query = state("");
    final Value<SortKey> sortKey = state(SortKey.NAME);
    final Value<Boolean> descending = state(false);
    final Value<Integer> scroll = state(0);
    final Cell<Boolean> carrying;
    final Cell<List<Entry<StoredItem, Long>>> contents;

    final Derived<List<Entry<StoredItem, Long>>> shown;
    final Derived<List<Tile>> visibleCells;
    final Derived<Integer> contentHeight;
    final Derived<Integer> padTop;
    final Derived<String> sortLabel;
    final Value<List<Integer>> inventory = state(range(TerminalMenu.INVENTORY_START, TerminalMenu.SLOT_COUNT));

    final Action<Void> cycleSort = action(() -> sortKey.set(SortKey.values()[(sortKey.get().ordinal() + 1) % SortKey.values().length]));
    final Action<Tile> take = action((Tile c) -> take(c, TakePacket.Mode.STACK));
    final Action<Tile> takeHalf = action((Tile c) -> take(c, TakePacket.Mode.HALF));
    final Action<Tile> toInventory = action((Tile c) -> take(c, TakePacket.Mode.TO_INVENTORY));
    final Action<Void> store = action(() -> store(false));
    final Action<Void> storeOne = action(() -> store(true));

    TerminalView(TerminalMenu menu) {
        this.menu = menu;
        Replica<List<Entry<StoredItem, Long>>> replica = Replication.watch(Network.STORAGE, menu.containerId);
        scope().own(replica);
        contents = source(Source.polled(() -> replica.get() == null ? List.of() : replica.get()));
        carrying = source(Source.polled(() -> !menu.getCarried().isEmpty()));
        shown = derive(contents, query, derive(sortKey, descending, TerminalView::order), TerminalView::filter);
        visibleCells = derive(shown, scroll, TerminalView::visible);
        contentHeight = derive(shown, s -> rows(s.size()) * CELL);
        padTop = derive(scroll, s -> s / CELL * CELL);
        sortLabel = derive(sortKey, k -> k.label);
        // A new search starts at the top.
        subscribe(query, () -> scroll.set(0));
    }

    private void take(Tile c, TakePacket.Mode mode) {
        // With a stack on the cursor the click stores it instead; see store.
        if (!carrying.get()) {
            Network.sendToServer(new TakePacket(menu.containerId, c.item(), mode));
        }
    }

    private void store(boolean one) {
        if (carrying.get()) {
            Network.sendToServer(new StorePacket(menu.containerId, one));
        }
    }

    private static Comparator<Entry<StoredItem, Long>> order(SortKey key, boolean descending) {
        Comparator<Entry<StoredItem, Long>> byName = Comparator.comparing(e -> e.identity().name().toLowerCase(Locale.ROOT));
        Comparator<Entry<StoredItem, Long>> c = switch (key) {
            case NAME -> byName;
            case COUNT -> Comparator.<Entry<StoredItem, Long>>comparingLong(Entry::value).thenComparing(byName);
            case MOD -> Comparator.<Entry<StoredItem, Long>, String>comparing(e -> e.identity().mod()).thenComparing(byName);
        };
        return descending ? c.reversed() : c;
    }

    /** Words match the name anywhere; an {@code @word} matches the start of the mod id. */
    static List<Entry<StoredItem, Long>> filter(List<Entry<StoredItem, Long>> all, String query,
                                                Comparator<Entry<StoredItem, Long>> order) {
        List<String> words = new ArrayList<>();
        List<String> mods = new ArrayList<>();
        for (String w : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (w.startsWith("@")) {
                mods.add(w.substring(1));
            } else if (!w.isEmpty()) {
                words.add(w);
            }
        }
        List<Entry<StoredItem, Long>> out = new ArrayList<>();
        for (Entry<StoredItem, Long> e : all) {
            String name = e.identity().name().toLowerCase(Locale.ROOT);
            if (words.stream().allMatch(name::contains) && mods.stream().allMatch(e.identity().mod()::startsWith)) {
                out.add(e);
            }
        }
        out.sort(order);
        return out;
    }

    private static List<Tile> visible(List<Entry<StoredItem, Long>> shown, int scroll) {
        int from = Math.min(shown.size(), scroll / CELL * COLUMNS);
        int to = Math.min(shown.size(), from + (VISIBLE_ROWS + SPARE_ROWS) * COLUMNS);
        List<Tile> out = new ArrayList<>(to - from);
        for (Entry<StoredItem, Long> e : shown.subList(from, to)) {
            out.add(new Tile(e.id(), e.identity(), e.identity().stack(), abbreviate(e.value())));
        }
        return out;
    }

    private static int rows(int count) {
        return (count + COLUMNS - 1) / COLUMNS;
    }

    private static final String[] UNITS = {"k", "M", "G", "T", "P", "E"};

    /** 999, 1.2k, 12k, 3.4M: at most three digits, the tenth only below 10, never rounded up. */
    static String abbreviate(long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        int unit = -1;
        long scale = 1;
        while (n / scale >= 1000) {
            scale *= 1000;
            unit++;
        }
        long tenths = n / (scale / 10);
        if (n / scale >= 10 || tenths % 10 == 0) {
            return n / scale + UNITS[unit];
        }
        return tenths / 10 + "." + tenths % 10 + UNITS[unit];
    }

    private static List<Integer> range(int from, int to) {
        List<Integer> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            out.add(i);
        }
        return out;
    }
}
