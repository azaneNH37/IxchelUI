package com.azane.ixchel.sample.effects;

import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.stream.IntStream;

/**
 * A few stacks to draw, whether the card is faded, the tab shown and what the filter and blend tabs
 * show: one tile per filter function, the first unfiltered, and one square per blend mode; on the
 * shader tab, whether the burning card is burnt and how far the stepped card has dissolved.
 */
@UiView("ixchel_sample:ui/effects/page.html")
final class EffectsModel extends ViewModel {
    final Value<ItemStack> sword = state(new ItemStack(Items.DIAMOND_SWORD));
    final Value<ItemStack> apple = state(new ItemStack(Items.GOLDEN_APPLE, 16));
    final Value<ItemStack> grass = state(new ItemStack(Items.GRASS_BLOCK));
    final Value<Boolean> faded = state(false);
    final Value<String> tab = state("opacity");
    final Value<List<String>> filters = state(List.of("none", "grayscale(1)", "sepia(1)", "saturate(3)",
            "hue-rotate(90deg)", "invert(1)", "brightness(1.6)", "contrast(2)", "opacity(0.4)", "blur(2px)",
            "drop-shadow(3px 3px 1px #C0C0C0)"));
    final Value<List<String>> blends = state(List.of("normal", "multiply", "screen", "plus-lighter", "darken", "lighten"));
    final Value<List<Integer>> stripes = state(IntStream.range(0, 48).boxed().toList());
    final Value<Boolean> burnt = state(false);
    final Value<Double> progress = state(0.0);
}
