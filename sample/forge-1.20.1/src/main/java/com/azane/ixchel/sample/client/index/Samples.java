package com.azane.ixchel.sample.client.index;

import com.azane.ixchel.sample.character.CharacterScreen;
import com.azane.ixchel.sample.config.ConfigScreen;
import com.azane.ixchel.sample.effects.EffectsScreen;
import com.azane.ixchel.sample.env.EnvHud;
import com.azane.ixchel.sample.guide.Guide;
import com.azane.ixchel.sample.loot.LootScreens;
import com.azane.ixchel.sample.spell.SpellWheel;
import com.azane.ixchel.sample.machine.MachineScreen;
import com.azane.ixchel.sample.registry.ModItems;
import com.azane.ixchel.sample.showcase.ShowcaseScreen;
import com.azane.ixchel.sample.quests.QuestsScreen;
import com.azane.ixchel.sample.storage.TerminalScreen;
import com.azane.ixchel.sample.tooltips.TooltipsScreen;
import com.azane.ixchel.sample.waypoints.WaypointsScreen;
import com.azane.ixchel.sample.workbench.Workbench;
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
                Sample.hud("env", new ItemStack(Items.SPYGLASS), EnvHud.hud()),
                Sample.screen("character", new ItemStack(Items.IRON_CHESTPLATE), CharacterScreen::open),
                Sample.screen("machine", new ItemStack(ModItems.POWERED_STONECUTTER.get()), MachineScreen::openNearby),
                Sample.screen("storage", new ItemStack(ModItems.STORAGE_TERMINAL.get()), TerminalScreen::openNearby),
                Sample.hud("loot", new ItemStack(Items.HOPPER), LootScreens.hud()),
                Sample.screen("loot-list", new ItemStack(Items.RECOVERY_COMPASS), LootScreens::openList),
                Sample.screen("waypoints", new ItemStack(Items.COMPASS), WaypointsScreen::open),
                Sample.screen("config", new ItemStack(Items.COMPARATOR), ConfigScreen::open),
                Sample.screen("quests", new ItemStack(Items.WRITABLE_BOOK), QuestsScreen::open),
                Sample.screen("guide", new ItemStack(Items.BOOK), Guide::open),
                Sample.screen("tooltips", new ItemStack(Items.NAME_TAG), TooltipsScreen::open),
                Sample.screen("effects", new ItemStack(Items.AMETHYST_SHARD), EffectsScreen::open),
                Sample.screen("themes", new ItemStack(Items.PAINTING), ShowcaseScreen::open),
                Sample.screen("workbench", new ItemStack(Items.CRAFTING_TABLE), Workbench::open),
                Sample.screen("spell", new ItemStack(Items.ENCHANTED_BOOK), SpellWheel::open));
    }
}
