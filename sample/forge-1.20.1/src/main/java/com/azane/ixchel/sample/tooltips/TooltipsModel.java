package com.azane.ixchel.sample.tooltips;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;
import java.util.stream.IntStream;

/** The page's data: a few stacks, two form values, a list long enough to scroll and a cooldown that counts down. */
@UiView("ixchel_sample:ui/tooltips/page.html")
final class TooltipsModel extends ViewModel {
    /** The cooldown restarts every this many seconds. */
    static final int COOLDOWN_SECONDS = 10;

    final Value<ItemStack> sword = state(sword());
    final Value<ItemStack> apple = state(new ItemStack(Items.GOLDEN_APPLE));
    final Value<ItemStack> pickaxe = state(new ItemStack(Items.DIAMOND_PICKAXE));
    final Value<String> name = state("");
    final Value<String> mode = state("smooth");
    final Value<List<Integer>> entries = state(IntStream.rangeClosed(1, 24).boxed().toList());
    final Value<Boolean> dialogOpen = state(false);
    /** Seconds until the spell is ready again, from {@code COOLDOWN_SECONDS - 1} down to 0, then again. */
    final Cell<Integer> cooldown = source(Source.polled(() ->
            (int) (COOLDOWN_SECONDS - 1 - System.currentTimeMillis() / 1000 % COOLDOWN_SECONDS)));
    /** How much of the cooldown is left, for the host-made {@code <sample-gauge>}. */
    final Derived<Float> cooldownLeft = derive(cooldown, c -> (float) c / (COOLDOWN_SECONDS - 1));

    private static ItemStack sword() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        stack.enchant(Enchantments.SHARPNESS, 3);
        stack.enchant(Enchantments.UNBREAKING, 2);
        return stack;
    }
}
