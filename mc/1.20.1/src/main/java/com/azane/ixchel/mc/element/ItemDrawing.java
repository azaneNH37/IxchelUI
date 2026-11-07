package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.paint.DrawCommand;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * How {@code <mc-item>} and {@code <mc-item-cycle>} draw one stack: the vanilla slot item scaled from
 * 16×16 to the border box, then its vanilla decorations (count, durability bar, cooldown) in the same
 * scale unless {@code decorations="none"}; a {@code count} attribute replaces the count text, empty
 * for none.
 *
 * <p>{@code spin="<degrees per second>"} draws the item's model instead, as an item frame holds it,
 * tipped towards the viewer and turning about the vertical axis at that speed, filling the smaller side
 * of the box; no decorations. The angle follows the game's time, so a recording turns it frame by frame.
 */
final class ItemDrawing {
    private static final float SIZE = 16f;
    /** Vanilla item rendering writes depth up to {@code z + 158}. */
    private static final float ITEM_DEPTH = 160f;
    /** Vanilla decorations draw at {@code z + 200}. */
    private static final float DECORATED_DEPTH = 210f;
    /** How far a spinning model leans towards the viewer, in degrees. */
    private static final float TILT = 20f;
    /**
     * The pose's scale per pixel of the box: the item frame's display shrinks a model to half, and a
     * cube turned corner-on is √2 wider, so this keeps it inside the box.
     */
    private static final float SPIN_SCALE = 1.35f;

    private ItemDrawing() {
    }

    /** Draws {@code stack} for {@code c}; returns the depth written, {@code 0} for an empty stack. */
    static float paint(GuiGraphics g, DrawCommand.Custom c, ItemStack stack, float z) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Element e = c.element();
        String spin = e.attribute("spin");
        if (spin != null) {
            return paintSpinning(g, c, stack, z, parse(spin));
        }
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

    private static float paintSpinning(GuiGraphics g, DrawCommand.Custom c, ItemStack stack, float z, float degreesPerSecond) {
        Minecraft mc = Minecraft.getInstance();
        float size = Math.min(c.width(), c.height());
        double angle = (Util.getMillis() % 3_600_000L) / 1000.0 * degreesPerSecond % 360;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(c.x() + c.width() / 2, c.y() + c.height() / 2, z + size);
        ps.scale(size * SPIN_SCALE, -size * SPIN_SCALE, size * SPIN_SCALE);
        ps.mulPose(Axis.XP.rotationDegrees(TILT));
        ps.mulPose(Axis.YP.rotationDegrees((float) angle));
        Lighting.setupFor3DItems();
        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, ps, g.bufferSource(), mc.level, 0);
        g.flush();
        ps.popPose();
        return 2 * size;
    }

    /** {@code spin}'s speed; {@code 0} when it is not a number. */
    private static float parse(String spin) {
        try {
            return Float.parseFloat(spin.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
