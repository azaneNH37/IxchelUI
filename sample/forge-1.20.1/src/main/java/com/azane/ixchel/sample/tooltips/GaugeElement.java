package com.azane.ixchel.sample.tooltips;

import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.mc.paint.NativePainter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;

/**
 * {@code <sample-gauge bind:fraction="...">}: a host-made native element, declared in
 * {@code ui/elements.txt}. A bar filled from the left by the payload, 0 to 1, in a vanilla vertical
 * gradient, which CSS here has no way to draw. Its size comes from CSS.
 */
public final class GaugeElement implements NativePainter {
    public static final String TAG = "sample-gauge";
    private static final int TRACK = 0xFF202020;
    private static final int TOP = 0xFF80FFA0;
    private static final int BOTTOM = 0xFF108030;

    @Override
    public float paint(GuiGraphics g, DrawCommand.Custom c, float z, float pointerX, float pointerY) {
        int w = Math.round(c.width());
        int h = Math.round(c.height());
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(c.x(), c.y(), z);
        g.fill(0, 0, w, h, TRACK);
        g.fillGradient(0, 0, Math.round(w * (Float) c.payload()), h, TOP, BOTTOM);
        ps.popPose();
        return 1;
    }
}
