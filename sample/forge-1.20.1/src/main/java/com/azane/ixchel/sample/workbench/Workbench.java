package com.azane.ixchel.sample.workbench;

import com.azane.ixchel.mc.UiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Holo workbench sample: a recipe browser over a fixed catalog with search and categories, the
 * chosen result turning in 3D on a stage, its materials against what the inventory holds, and a craft
 * button whose request the server checks and carries out ({@link CraftPacket}).
 */
public final class Workbench {
    /** One catalog entry: the result to look up a crafting recipe for, and its category. */
    record Entry(Item result, String category) {
    }

    /** The catalog, in list order; each result's first crafting recipe is the one shown. */
    static final List<Entry> CATALOG = List.of(
            new Entry(Items.DIAMOND_PICKAXE, "tools"),
            new Entry(Items.DIAMOND_SWORD, "tools"),
            new Entry(Items.CROSSBOW, "tools"),
            new Entry(Items.SHIELD, "tools"),
            new Entry(Items.SPYGLASS, "tools"),
            new Entry(Items.CLOCK, "tools"),
            new Entry(Items.PISTON, "devices"),
            new Entry(Items.OBSERVER, "devices"),
            new Entry(Items.HOPPER, "devices"),
            new Entry(Items.ANVIL, "devices"),
            new Entry(Items.BREWING_STAND, "devices"),
            new Entry(Items.LANTERN, "devices"),
            new Entry(Items.ENCHANTING_TABLE, "wonders"),
            new Entry(Items.BEACON, "wonders"),
            new Entry(Items.END_CRYSTAL, "wonders"),
            new Entry(Items.CONDUIT, "wonders"),
            new Entry(Items.ENDER_CHEST, "wonders"),
            new Entry(Items.RESPAWN_ANCHOR, "wonders"));

    private Workbench() {
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new UiScreen(new WorkbenchModel()));
    }
}
