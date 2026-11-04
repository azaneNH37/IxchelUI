package com.azane.ixchel.sample.loot;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.mc.source.WorldProjection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The radar HUD: a label over each drop in view (name, count, distance, in its rarity color), one
 * arrow with a count per screen edge for the drops out of view, and the pickup toasts. Labels follow
 * the drops every frame through {@link WorldProjection}.
 */
@UiView("ixchel_sample:ui/loot/hud.html")
final class LootModel extends ViewModel {
    /** Drops labeled at most, nearest first. */
    static final int MAX_LABELS = 24;
    static final int LABEL_WIDTH = 120;
    static final int EDGE_WIDTH = 30;

    /** A label centered over a drop in view; {@code left} and {@code top} in GUI pixels. */
    record Label(int id, String text, int left, int top, String color) {
    }

    /** Drops out of view in one of eight directions (the page turns the arrow by 45° a sector), shown at that edge of the screen. */
    record Edge(int sector, int count, int left, int top) {
    }

    private final PickupToasts log = new PickupToasts();

    final Cell<List<Label>> labels = source(ClientSources.player(LootModel::labels, List.of()));
    final Cell<List<Edge>> edges = source(ClientSources.player(LootModel::edges, List.of()));
    final Cell<List<PickupToasts.Toast>> toasts = source(ClientSources.player(log::read, List.of()));

    private static List<Label> labels(LocalPlayer p) {
        List<Label> out = new ArrayList<>();
        for (Drops.Drop d : Drops.scan(p)) {
            if (out.size() == MAX_LABELS) {
                break;
            }
            Optional<Vector2f> at = gui(d.entity());
            if (at.isPresent()) {
                ItemStack s = d.entity().getItem();
                String text = s.getHoverName().getString() + (s.getCount() > 1 ? " x" + s.getCount() : "")
                        + " " + Math.round(d.distance()) + " m";
                out.add(new Label(d.entity().getId(), text, Math.round(at.get().x) - LABEL_WIDTH / 2,
                        Math.round(at.get().y) - 10, Drops.color(s.getRarity())));
            }
        }
        return out;
    }

    private static List<Edge> edges(LocalPlayer p) {
        int[] counts = new int[8];
        boolean any = false;
        for (Drops.Drop d : Drops.scan(p)) {
            if (gui(d.entity()).isEmpty()) {
                counts[Drops.sector(p, d.entity())]++;
                any = true;
            }
        }
        if (!any) {
            return List.of();
        }
        int w = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int h = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        // Clockwise from ahead; the top row clears the compass, the bottom row the hotbar.
        int[][] at = {
                {w / 2 - EDGE_WIDTH / 2, 36}, {w - EDGE_WIDTH - 4, 36}, {w - EDGE_WIDTH - 4, h / 2 - 4},
                {w - EDGE_WIDTH - 4, h - 48}, {w / 2 - EDGE_WIDTH / 2, h - 58}, {4, h - 48},
                {4, h / 2 - 4}, {4, 36}};
        List<Edge> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            if (counts[i] > 0) {
                out.add(new Edge(i, counts[i], at[i][0], at[i][1]));
            }
        }
        return out;
    }

    /** Where a point just above the drop is on screen, if it is in view. */
    private static Optional<Vector2f> gui(ItemEntity e) {
        return WorldProjection.toGui(e.getPosition(WorldProjection.partialTick()).add(0, 0.75, 0));
    }
}
