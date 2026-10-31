package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.mc.paint.NativePainter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Supplier;

/**
 * {@code <mc-entity>}: a {@link LivingEntity} (or a {@code Supplier} of one, read at paint time)
 * standing on the bottom center of the box, scaled to fit, drawn as the vanilla inventory draws the
 * player: lit from the front, head and body turned towards the pointer, or facing forward without one.
 */
final class EntityElement implements NativePainter {
    static final String TAG = "mc-entity";
    static final EntityElement PAINTER = new EntityElement();
    /** Depth the entity model writes above its level, as an item's. */
    private static final float DEPTH = 160f;

    private EntityElement() {
    }

    @Override
    public float paint(GuiGraphics g, DrawCommand.Custom c, float z, float pointerX, float pointerY) {
        LivingEntity e = entityOf(c.payload());
        if (e == null) {
            return 0;
        }
        float fit = Math.min(c.height() / e.getBbHeight(), c.width() / (e.getBbWidth() * 2)) * 0.9f;
        int scale = Math.max(1, Math.round(fit));
        int feetX = Math.round(c.x() + c.width() / 2);
        int feetY = Math.round(c.y() + c.height());
        float eyeY = feetY - scale * e.getEyeHeight();
        // Vanilla passes the box's eye point minus the pointer; zero looks straight out of the screen.
        float lookX = Float.isNaN(pointerX) ? 0 : feetX - pointerX;
        float lookY = Float.isNaN(pointerY) ? 0 : eyeY - pointerY;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0, 0, z);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, feetX, feetY, scale, lookX, lookY, e);
        ps.popPose();
        return DEPTH;
    }

    /** The entity a payload stands for right now, or {@code null}. */
    private static LivingEntity entityOf(Object payload) {
        if (payload instanceof LivingEntity e) {
            return e;
        }
        if (payload instanceof Supplier<?> handle) {
            Object v = handle.get();
            if (v == null || v instanceof LivingEntity) {
                return (LivingEntity) v;
            }
            throw new IllegalStateException("<mc-entity> needs a LivingEntity or a Supplier of one, got a Supplier of " + v);
        }
        throw new IllegalStateException("<mc-entity> needs a LivingEntity or a Supplier of one, got "
                + payload.getClass().getName());
    }
}
