package com.azane.ixchel.sample.loot;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.sample.network.Network;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The radar list: every drop in range, nearest first, keyed by entity id so a row keeps its nodes
 * while the order changes; a rarity filter; a pick-up button, disabled out of
 * {@link PickUpDropPacket#PICK_RANGE} (the server checks again).
 */
final class LootListView extends ViewModel {
    /**
     * A drop's row. {@code icon} is a copy: the entity's stack shrinks in place as it is picked up,
     * and {@link ClientSources#player} compares stacks by item, count and tag.
     */
    record Row(int id, ItemStack icon, String name, String count, String distance, String arrow, String color,
               int rarity, boolean inRange) {
    }

    /** A filter button: shows drops of rarity {@code level} (vanilla's order) and above. */
    record Filter(int level, String label, boolean selected) {
    }

    private static final String[] FILTERS = {"All", "Uncommon+", "Rare+", "Epic"};

    final Value<Integer> minRarity = state(0);
    final Value<List<Row>> drops = source(ClientSources.player(LootListView::rows, List.of()));
    final Derived<List<Row>> rows = derive(drops, minRarity,
            (all, min) -> all.stream().filter(r -> r.rarity() >= min).toList());
    final Derived<List<Filter>> filters = derive(minRarity, LootListView::filters);
    final Derived<Boolean> empty = derive(rows, List::isEmpty);
    final Derived<String> count = derive(rows, drops, (shown, all) -> shown.size() + " of " + all.size() + " drops");

    final Action<Filter> filter = action(f -> minRarity.set(f.level()));
    final Action<Row> pickUp = action(r -> Network.sendToServer(new PickUpDropPacket(r.id())));

    private static List<Row> rows(LocalPlayer p) {
        List<Row> out = new ArrayList<>();
        for (Drops.Drop d : Drops.scan(p)) {
            ItemStack s = d.entity().getItem();
            out.add(new Row(d.entity().getId(), s.copy(), s.getHoverName().getString(),
                    s.getCount() > 1 ? "x" + s.getCount() : "", Math.round(d.distance()) + " m",
                    Drops.arrow(Drops.sector(p, d.entity())), Drops.color(s.getRarity()), s.getRarity().ordinal(),
                    d.distance() <= PickUpDropPacket.PICK_RANGE));
        }
        return out;
    }

    private static List<Filter> filters(int selected) {
        List<Filter> out = new ArrayList<>(FILTERS.length);
        for (int i = 0; i < FILTERS.length; i++) {
            out.add(new Filter(i, FILTERS[i], i == selected));
        }
        return out;
    }
}
