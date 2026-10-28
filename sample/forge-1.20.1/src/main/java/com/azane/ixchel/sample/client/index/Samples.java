package com.azane.ixchel.sample.client.index;

import com.azane.ixchel.sample.character.CharacterScreen;
import com.azane.ixchel.sample.env.EnvHud;
import com.azane.ixchel.sample.loot.LootScreens;
import com.azane.ixchel.sample.machine.MachineScreen;
import com.azane.ixchel.sample.registry.ModItems;
import com.azane.ixchel.sample.storage.TerminalScreen;
import com.azane.ixchel.sample.waypoints.WaypointsScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The explicit list of samples the index shows, in index order. */
public final class Samples {
    private Samples() {
    }

    /** Every sample. Built when the index opens: icons need the item registry. */
    public static List<Sample> all() {
        return List.of(
                Sample.hud("env", "Environment HUD", "A compass bar with the waypoints (J), what the crosshair is on "
                        + "(block with tool and states, or an entity's health), biome, time, weather, light, position; "
                        + "hearts, armor, food, experience.", new ItemStack(Items.SPYGLASS), EnvHud.hud()),
                Sample.screen("character", "Character panel (G)", "Your inventory with tabs: a paper doll that looks "
                        + "at the pointer, attributes with tooltips, effects that blink as they end, and statistics from "
                        + "the server.", new ItemStack(Items.IRON_CHESTPLATE), CharacterScreen::open),
                Sample.screen("machine", "Machine panel", "A powered stonecutter (Functional Blocks tab): fuel makes "
                        + "FE, a water bucket fills the tank, pick a recipe; redstone mode and side configuration. "
                        + "Opening it here places one next to you.", new ItemStack(ModItems.POWERED_STONECUTTER.get()),
                        MachineScreen::openNearby),
                Sample.screen("storage", "Storage terminal", "Every chest and item handler within 8 blocks as "
                        + "one virtual grid: search (@mod filters by mod), sort by name, count or mod; click takes a "
                        + "stack, right-click half, shift-click into the inventory; click with a stack to store it. "
                        + "The terminal item (Tools tab) opens it too.", new ItemStack(ModItems.STORAGE_TERMINAL.get()),
                        TerminalScreen::openNearby),
                Sample.hud("loot", "Loot radar HUD", "Labels over the drops in view, an arrow with a count at the "
                        + "screen edge for the rest, and toasts for what you pick up.", new ItemStack(Items.HOPPER),
                        LootScreens.hud()),
                Sample.screen("loot-list", "Loot radar list (U)", "Drops nearest first with their direction, a "
                        + "rarity filter, and a pick-up button the server only honors within 8 blocks.",
                        new ItemStack(Items.RECOVERY_COMPASS), LootScreens::openList),
                Sample.screen("waypoints", "Waypoints (J)", "Session list with keyboard selection, a scrolling list "
                        + "and distances that follow the player; Up, Down and Delete are template key filters.",
                        new ItemStack(Items.COMPASS), WaypointsScreen::open));
    }
}
