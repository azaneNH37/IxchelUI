package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.paint.DrawCommand;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * How {@code <mc-item>} and {@code <mc-item-cycle>} draw one stack: the vanilla slot item scaled from
 * 16×16 to the border box, then its vanilla decorations (count, durability bar, cooldown) in the same
 * scale unless {@code decorations="none"}; a {@code count} attribute replaces the count text, empty
 * for none.
 */
final class ItemDrawing {
    private static final float SIZE = 16f;
    /** Vanilla item rendering writes depth up to {@code z + 158}. */
    private static final float ITEM_DEPTH = 160f;
    /** Vanilla decorations draw at {@code z + 200}. */
    private static final float DECORATED_DEPTH = 210f;

    private ItemDrawing() {
    }

    /** Draws {@code stack} for {@code c}; returns the depth written, {@code 0} for an empty stack. */
    static float paint(GuiGraphics g, DrawCommand.Custom c, ItemStack stack, float z) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Element e = c.element();
        boolean decorated = !"none".equals(e.attribute("decorations"));
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(c.x(), c.y(), z);
        ps.scale(c.width() / SIZE, c.height() / SIZE, 1);
        g.renderItem(stack, 0, 0);
        if (decorated) {
            g.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0, e.attribute("count"));
        }
        ps.popPose();
        return decorated ? DECORATED_DEPTH : ITEM_DEPTH;
    }
}
