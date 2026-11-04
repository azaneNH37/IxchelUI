package com.azane.ixchel.sample.loot;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.sample.network.Network;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The radar list: every drop in range, nearest first, keyed by entity id so a row keeps its nodes
 * while the order changes; a rarity filter; a pick-up button, disabled out of
 * {@link PickUpDropPacket#PICK_RANGE} (the server checks again).
 */
@UiView("ixchel_sample:ui/loot/list.html")
final class LootListModel extends ViewModel {
    /**
     * A drop's row. {@code icon} is a copy: the entity's stack shrinks in place as it is picked up,
     * and {@link ClientSources#player} compares stacks by item, count and tag. {@code heading}: degrees
     * clockwise from the player's facing, which the page turns the arrow by.
     */
    record Row(int id, ItemStack icon, String name, String count, String distance, int heading, String color,
               int rarity, boolean inRange) {
    }

    /** A filter button: shows drops of rarity {@code level} (vanilla's order) and above. */
    record Filter(int level, Component label) {
    }

    /** Key suffixes of the filters, in rarity order. */
    private static final String[] FILTERS = {"all", "uncommon", "rare", "epic"};

    final Value<Integer> minRarity = state(0);
    final Cell<List<Row>> drops = source(ClientSources.player(LootListModel::rows, List.of()));
    final Derived<List<Row>> rows = derive(drops, minRarity,
            (all, min) -> all.stream().filter(r -> r.rarity() >= min).toList());
    final Value<List<Filter>> filters = state(filters());

    final Action<Row> pickUp = action(r -> Network.sendToServer(new PickUpDropPacket(r.id())));

    private static List<Row> rows(LocalPlayer p) {
        List<Row> out = new ArrayList<>();
        for (Drops.Drop d : Drops.scan(p)) {
            ItemStack s = d.entity().getItem();
            out.add(new Row(d.entity().getId(), s.copy(), s.getHoverName().getString(),
                    s.getCount() > 1 ? "x" + s.getCount() : "", Math.round(d.distance()) + " m",
                    Drops.heading(p, d.entity()), Drops.color(s.getRarity()), s.getRarity().ordinal(),
                    d.distance() <= PickUpDropPacket.PICK_RANGE));
        }
        return out;
    }

    private static List<Filter> filters() {
        List<Filter> out = new ArrayList<>(FILTERS.length);
        for (int i = 0; i < FILTERS.length; i++) {
            out.add(new Filter(i, Component.translatable("ixchel_sample.loot.filter." + FILTERS[i])));
        }
        return out;
    }
}
