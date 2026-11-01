package com.azane.ixchel.sample.waypoints;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The session's waypoints, shared by the list screen and the environment HUD's compass. The list is
 * immutable and replaced on every change, so a source polling {@link #all} compares by identity.
 * Client thread only.
 */
public final class Waypoints {
    public record Waypoint(int id, String name, BlockPos pos) {
    }

    private static List<Waypoint> saved = List.of();
    private static int nextId;

    private Waypoints() {
    }

    public static List<Waypoint> all() {
        return saved;
    }

    /** Appends a waypoint named {@code Waypoint <n>} in the current language and returns it. */
    public static Waypoint add(BlockPos pos) {
        String name = Component.translatable("ixchel_sample.waypoints.default_name", saved.size() + 1).getString();
        Waypoint w = new Waypoint(nextId++, name, pos);
        List<Waypoint> next = new ArrayList<>(saved);
        next.add(w);
        saved = List.copyOf(next);
        return w;
    }

    static void remove(int id) {
        List<Waypoint> next = new ArrayList<>(saved);
        next.removeIf(w -> w.id() == id);
        saved = List.copyOf(next);
    }

    public static void clear() {
        saved = List.of();
    }
}
