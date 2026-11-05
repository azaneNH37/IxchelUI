package com.azane.ixchel.sample.showcase;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The showcase's controls and the theme shown; picking another theme opens its page. */
@UiView("ixchel_sample:ui/showcase/themes/*.html")
final class ShowcaseModel extends ViewModel {
    final Value<String> theme;
    final Value<String> field = state("");
    final Value<Boolean> checked = state(true);
    final Value<String> level = state("4");
    final Value<String> choice = state("a");
    final Value<Boolean> dialog = state(false);
    /** The cycle button's options. */
    final Value<List<String>> speeds = state(List.of("1x", "2x", "4x"));
    /** The item whose count text the showcase replaces. */
    final Value<ItemStack> stack = state(new ItemStack(Items.REDSTONE, 64));
    /** The items the cycling slot steps through: a worn tool shows the durability bar. */
    final Value<List<ItemStack>> stacks = state(List.of(wornSword(), new ItemStack(Items.GOLDEN_APPLE, 16),
            new ItemStack(Items.ENDER_PEARL, 8)));
    final Action<String> pick;

    ShowcaseModel(String theme) {
        this.theme = state(theme);
        pick = action((String t) -> ShowcaseScreen.open(t));
    }

    private static ItemStack wornSword() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.setDamageValue(sword.getMaxDamage() * 2 / 3);
        return sword;
    }
}
