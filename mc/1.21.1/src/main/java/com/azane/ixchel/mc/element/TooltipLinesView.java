package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Param;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code <mc-tooltip-lines bind:item="…">}: the lines of the item's vanilla tooltip
 * ({@link Screen#getTooltipFromItem}, so lines other mods add come along), one element per line in its
 * colors and formatting. Polled each pass; in an {@code <ix-tooltip>}, only while it shows. Public for
 * {@link com.azane.ixchel.mc.McLibrary}, which declares it.
 */
@UiTemplate("@ixchel/mc/element/tooltip-lines.html")
public final class TooltipLinesView extends ViewModel {
    /** An empty line keeps its height. */
    private static final Component BLANK = Component.literal("\u00A0");

    /** A line and its position, which keys it: equal lines may repeat. */
    record Line(int index, Component text) {
    }

    final Param<ItemStack> item = param();
    final Cell<List<Line>> lines = source(Source.polled(() -> lines(item.get())));

    private static List<Line> lines(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        List<Component> tooltip = Screen.getTooltipFromItem(Minecraft.getInstance(), stack);
        List<Line> out = new ArrayList<>(tooltip.size());
        for (int i = 0; i < tooltip.size(); i++) {
            Component c = tooltip.get(i);
            out.add(new Line(i, c.getString().isBlank() ? BLANK : c));
        }
        return out;
    }
}
