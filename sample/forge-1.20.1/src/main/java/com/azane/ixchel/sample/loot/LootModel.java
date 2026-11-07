package com.azane.ixchel.sample.loot;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.mc.source.WorldProjection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The loot HUD, in amber: reticle brackets over each drop in view, sized by distance, with a tag (name
 * and count, in its rarity color); a list of the nearest drops at the right edge, each with its
 * distance and an arrow turned towards it; and the pickup toasts. Brackets follow the drops every
 * frame through {@link WorldProjection}.
 */
@UiView("ixchel_sample:ui/loot/hud.html")
final class LootModel extends ViewModel {
    /** Drops bracketed at most, nearest first. */
    static final int MAX_MARKS = 12;
    /** Rows in the nearby list. */
    static final int MAX_NEAR = 6;

    /** Brackets centered over a drop in view, {@code size} pixels square; {@code left}/{@code top} in GUI pixels. */
    record Mark(int id, String tag, String color, int left, int top, int size) {
    }

    /** A row of the nearby list; {@code heading} in degrees clockwise from the player's facing. */
    record Near(int id, ItemStack stack, String name, String distance, int heading, String color) {
    }

    private final PickupToasts log = new PickupToasts();

    final Cell<List<Mark>> marks = source(ClientSources.player(LootModel::marks, List.of()));
    final Cell<List<Near>> near = source(ClientSources.player(LootModel::near, List.of()));
    final Derived<Boolean> nothingNear = derive(near, List::isEmpty);
    final Cell<List<PickupToasts.Toast>> toasts = source(ClientSources.player(log::read, List.of()));

    private static List<Mark> marks(LocalPlayer p) {
        List<Mark> out = new ArrayList<>();
        for (Drops.Drop d : Drops.scan(p)) {
            if (out.size() == MAX_MARKS) {
                break;
            }
            Optional<Vector2f> at = gui(d.entity());
            if (at.isPresent()) {
                ItemStack s = d.entity().getItem();
                int size = Math.max(12, Math.min(48, Math.round(96 / (float) Math.max(d.distance(), 1))));
                out.add(new Mark(d.entity().getId(), tag(s), Drops.color(s.getRarity()),
                        Math.round(at.get().x) - size / 2, Math.round(at.get().y) - size / 2, size));
            }
        }
        return out;
    }

    private static List<Near> near(LocalPlayer p) {
        List<Near> out = new ArrayList<>();
        for (Drops.Drop d : Drops.scan(p)) {
            if (out.size() == MAX_NEAR) {
                break;
            }
            ItemStack s = d.entity().getItem();
            out.add(new Near(d.entity().getId(), s, tag(s), String.format(Locale.ROOT, "%.1f m", d.distance()),
                    Drops.heading(p, d.entity()), Drops.color(s.getRarity())));
        }
        return out;
    }

    private static String tag(ItemStack s) {
        return s.getHoverName().getString() + (s.getCount() > 1 ? " x" + s.getCount() : "");
    }

    /** Where the middle of the drop is on screen, if it is in view. */
    private static Optional<Vector2f> gui(ItemEntity e) {
        return WorldProjection.toGui(e.getPosition(WorldProjection.partialTick()).add(0, 0.25, 0));
    }
}
