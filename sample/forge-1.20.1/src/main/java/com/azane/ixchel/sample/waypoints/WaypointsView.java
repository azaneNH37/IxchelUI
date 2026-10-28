package com.azane.ixchel.sample.waypoints;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.sample.waypoints.Waypoints.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The session's {@link Waypoints}: add the player's position, select a row (click, or Up/Down from
 * the template's key filters), remove the selected one. Distances follow the player every frame.
 */
final class WaypointsView extends ViewModel {
    record Row(int id, String name, String coords, String distance, boolean selected) {
    }

    final Value<List<Waypoint>> waypoints = source(Source.polled(Waypoints::all));
    final Value<Integer> selectedId = state(-1);
    final Value<BlockPos> playerPos = source(ClientSources.player(LocalPlayer::blockPosition, BlockPos.ZERO));
    final Derived<List<Row>> rows = derive(waypoints, selectedId, playerPos, WaypointsView::rows);
    final Derived<Boolean> empty = derive(waypoints, List::isEmpty);
    final Derived<Boolean> hasSelection = derive(selectedId, id -> id >= 0);
    final Derived<String> count = derive(waypoints, w -> w.size() + (w.size() == 1 ? " waypoint" : " waypoints"));

    final Action<Void> add = action(() -> {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) {
            selectedId.set(Waypoints.add(p.blockPosition()).id());
        }
    });
    final Action<Row> select = action(r -> selectedId.set(r.id()));
    final Action<Void> remove = action(() -> {
        int at = indexOf(selectedId.get());
        if (at < 0) {
            return;
        }
        Waypoints.remove(selectedId.get());
        List<Waypoint> left = Waypoints.all();
        selectedId.set(left.isEmpty() ? -1 : left.get(Math.min(at, left.size() - 1)).id());
    });
    final Action<Void> selectPrevious = action(() -> move(-1));
    final Action<Void> selectNext = action(() -> move(1));

    /** Moves the selection by {@code delta} rows, clamped; selects the first row when none is. */
    private void move(int delta) {
        List<Waypoint> all = Waypoints.all();
        if (all.isEmpty()) {
            return;
        }
        int at = indexOf(selectedId.get());
        int next = at < 0 ? 0 : Math.max(0, Math.min(all.size() - 1, at + delta));
        selectedId.set(all.get(next).id());
    }

    private static int indexOf(int id) {
        List<Waypoint> all = Waypoints.all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id() == id) {
                return i;
            }
        }
        return -1;
    }

    private static List<Row> rows(List<Waypoint> waypoints, int selectedId, BlockPos player) {
        List<Row> out = new ArrayList<>(waypoints.size());
        for (Waypoint w : waypoints) {
            String coords = w.pos().getX() + ", " + w.pos().getY() + ", " + w.pos().getZ();
            String distance = Math.round(Math.sqrt(w.pos().distSqr(player))) + " m";
            out.add(new Row(w.id(), w.name(), coords, distance, w.id() == selectedId));
        }
        return out;
    }
}
