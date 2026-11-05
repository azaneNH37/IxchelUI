package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.view.UiElement;
import com.azane.ixchel.mc.paint.ElementPainter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * {@code <mc-item bind:item="...">}: an item drawn like a vanilla slot item ({@link ItemDrawing}), with
 * the vanilla item tooltip. Bind an {@link ItemStack} snapshot when bindings must see changes, or a
 * {@code Supplier<ItemStack>} handle read at paint time when only the picture changes, so a live stack
 * is drawn every frame without any comparison or copy; keep the handle instance stable. An empty stack
 * draws nothing and has no tooltip.
 */
@UiElement(tag = "mc-item", payload = "item", attributes = {"count", "decorations"})
final class ItemElement implements ElementPainter<ItemStack> {
    @Override
    public float paint(GuiGraphics g, DrawCommand.Custom c, ItemStack stack, float z, float pointerX, float pointerY) {
        return ItemDrawing.paint(g, c, stack, z);
    }

    @Override
    public boolean hasTooltip(ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public void renderTooltip(GuiGraphics g, Font font, ItemStack stack, int mouseX, int mouseY) {
        g.renderTooltip(font, stack, mouseX, mouseY);
    }
}
