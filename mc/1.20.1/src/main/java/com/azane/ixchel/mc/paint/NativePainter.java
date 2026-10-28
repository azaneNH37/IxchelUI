package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.paint.DrawCommand;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the {@link DrawCommand.Custom} commands of one native element kind through vanilla.
 * {@link McPainter} flushes its own batch before the call.
 */
public interface NativePainter {
    /**
     * Draws {@code c} at depth {@code z} of the current pose of {@code g}.
     *
     * @param pointerX the pointer in GUI pixels, or NaN in a HUD
     * @return how far above {@code z} the drawing wrote depth: later commands overlapping the border
     * box draw from there, so they still paint on top; {@code 0} when nothing was drawn. Only the
     * border box counts, so a drawing outside it can cover later commands there
     */
    float paint(GuiGraphics g, DrawCommand.Custom c, float z, float pointerX, float pointerY);
}
