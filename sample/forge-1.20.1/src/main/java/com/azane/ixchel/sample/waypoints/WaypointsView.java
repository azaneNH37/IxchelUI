package com.azane.ixchel.sample.waypoints;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.ClientSources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * A list of saved positions kept for the session: add the player's position, select a row (click,
 * or Up/Down from the screen), remove the selected one. Distances follow the player every frame.
 */
final class WaypointsView extends ViewModel {
    record Waypoint(int id, String name, BlockPos pos) {
    }

    record Row(int id, String name, String coords, String distance, boolean selected) {
    }

    /** Session state shared by every opening of the screen. */
    static final List<Waypoint> SAVED = new ArrayList<>();
    private static int nextId;

    final Value<List<Waypoint>> waypoints = state(List.copyOf(SAVED));
    final Value<Integer> selectedId = state(-1);
    final Value<BlockPos> playerPos = source(ClientSources.player(LocalPlayer::blockPosition, BlockPos.ZERO));
    final Derived<List<Row>> rows = derive(waypoints, selectedId, playerPos, WaypointsView::rows);
    final Derived<Boolean> empty = derive(waypoints, List::isEmpty);
    final Derived<Boolean> hasSelection = derive(selectedId, id -> id >= 0);
    final Derived<String> count = derive(waypoints, w -> w.size() + (w.size() == 1 ? " waypoint" : " waypoints"));

    final Action<Void> add = action(() -> {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        Waypoint w = new Waypoint(nextId++, "Waypoint " + (SAVED.size() + 1), p.blockPosition());
        SAVED.add(w);
        waypoints.set(List.copyOf(SAVED));
        selectedId.set(w.id());
    });
    final Action<Row> select = action(r -> selectedId.set(r.id()));
    final Action<Void> remove = action(() -> {
        int id = selectedId.get();
        int at = indexOf(id);
        if (at < 0) {
            return;
        }
        SAVED.remove(at);
        waypoints.set(List.copyOf(SAVED));
        selectedId.set(SAVED.isEmpty() ? -1 : SAVED.get(Math.min(at, SAVED.size() - 1)).id());
    });

    /** Moves the selection by {@code delta} rows, clamped; selects the first row when none is. */
    void move(int delta) {
        if (SAVED.isEmpty()) {
            return;
        }
        int at = indexOf(selectedId.get());
        int next = at < 0 ? 0 : Math.max(0, Math.min(SAVED.size() - 1, at + delta));
        selectedId.set(SAVED.get(next).id());
    }

    private static int indexOf(int id) {
        for (int i = 0; i < SAVED.size(); i++) {
            if (SAVED.get(i).id() == id) {
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
