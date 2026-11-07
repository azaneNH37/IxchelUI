package com.azane.ixchel.sample.loot;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Dropped items around the local player, scanned on the client every frame. */
final class Drops {
    /** How far the radar looks, in blocks. */
    static final double RANGE = 24;

    record Drop(ItemEntity entity, double distance) {
    }

    private Drops() {
    }

    /** Drops within {@link #RANGE}, nearest first. */
    static List<Drop> scan(LocalPlayer p) {
        List<Drop> out = new ArrayList<>();
        for (ItemEntity e : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(RANGE))) {
            double d = e.position().distanceTo(p.position());
            if (d <= RANGE && !e.getItem().isEmpty()) {
                out.add(new Drop(e, d));
            }
        }
        out.sort(Comparator.comparingDouble(Drop::distance));
        return out;
    }

    /** Where {@code e} is from the player's facing, in whole degrees clockwise from ahead, 0 to 359. */
    static int heading(LocalPlayer p, ItemEntity e) {
        double dx = e.getX() - p.getX();
        double dz = e.getZ() - p.getZ();
        // Bearing clockwise from north (-z); the player's heading the same way from its yaw.
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        return Math.floorMod((int) Math.round(bearing - (p.getYRot() + 180)), 360);
    }

    /** The rarity's chat color as {@code #RRGGBB}. */
    static String color(Rarity rarity) {
        Integer rgb = rarity.color.getColor();
        return String.format("#%06X", rgb == null ? 0xFFFFFF : rgb);
    }
}
