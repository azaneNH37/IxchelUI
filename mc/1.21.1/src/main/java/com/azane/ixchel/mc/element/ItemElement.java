package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.mc.paint.NativePainter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * {@code <mc-item>}: an item drawn like a vanilla slot item, scaled from 16×16 to the border box. The
 * payload is an {@link ItemStack} snapshot from a value cell, or a {@code Supplier<ItemStack>} handle
 * read at paint time, so a live stack is drawn every frame without any comparison or copy.
 */
final class ItemElement implements NativePainter {
    static final String TAG = "mc-item";
    static final ItemElement PAINTER = new ItemElement();
    private static final float SIZE = 16f;
    /** Vanilla item rendering writes depth up to {@code z + 158}. */
    private static final float DEPTH = 160f;

    private ItemElement() {
    }

    @Override
    public float paint(GuiGraphics g, DrawCommand.Custom c, float z, float pointerX, float pointerY) {
        ItemStack stack = stackOf(c.payload());
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

    /** The stack a payload stands for right now. */
    static ItemStack stackOf(Object payload) {
        if (payload instanceof ItemStack stack) {
            return stack;
        }
        if (payload instanceof Supplier<?> handle && handle.get() instanceof ItemStack stack) {
            return stack;
        }
        throw new IllegalStateException("<mc-item> needs an ItemStack or a Supplier<ItemStack>, got "
                + (payload instanceof Supplier<?> h ? "a Supplier of " + h.get() : payload.getClass().getName()));
    }
}
