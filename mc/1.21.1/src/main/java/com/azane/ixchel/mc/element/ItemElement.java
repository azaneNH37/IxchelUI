package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.template.NativeElement;
import com.azane.ixchel.mc.paint.NativePainter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * {@code <mc-item bind:item="...">}: an item drawn like a vanilla slot item, scaled from 16×16 to the
 * border box, with the vanilla item tooltip. Bind an {@link ItemStack} snapshot when bindings must
 * see changes, or a {@code Supplier<ItemStack>} handle read at paint time when only the picture
 * changes, so a live stack is drawn every frame without any comparison or copy; keep the handle
 * instance stable. An empty stack draws nothing and has no tooltip.
 */
@NativeElement(tag = "mc-item", payload = "item")
final class ItemElement implements NativePainter<ItemStack> {
    private static final float SIZE = 16f;
    /** Vanilla item rendering writes depth up to {@code z + 158}. */
    private static final float DEPTH = 160f;

    @Override
    public float paint(GuiGraphics g, DrawCommand.Custom c, ItemStack stack, float z, float pointerX, float pointerY) {
        if (stack.isEmpty()) {
            return 0;
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(c.x(), c.y(), z);
        ps.scale(c.width() / SIZE, c.height() / SIZE, 1);
        g.renderItem(stack, 0, 0);
        ps.popPose();
        return DEPTH;
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
