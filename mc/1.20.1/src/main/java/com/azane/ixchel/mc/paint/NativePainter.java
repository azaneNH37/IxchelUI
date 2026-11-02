package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.template.NativePayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A native element drawn through vanilla: its class carries the
 * {@link com.azane.ixchel.core.template.NativeElement} declaration and {@code T} is its payload's
 * type. {@link McPainter} flushes its own batch before each call.
 *
 * <pre>{@code
 * @NativeElement(tag = "sample-gauge", payload = "fraction")
 * final class GaugeElement implements NativePainter<Float> { ... }
 * }</pre>
 */
public interface NativePainter<T> extends NativePayload<T> {
    /**
     * Draws {@code c} with the payload's current value at depth {@code z} of the current pose of
     * {@code g}; not called while the value is {@code null}.
     *
     * @param pointerX the pointer in GUI pixels, or NaN in a HUD
     * @return how far above {@code z} the drawing wrote depth: later commands overlapping the border
     * box draw from there, so they still paint on top; {@code 0} when nothing was drawn. Only the
     * border box counts, so a drawing outside it can cover later commands there
     */
    float paint(GuiGraphics g, DrawCommand.Custom c, T value, float z, float pointerX, float pointerY);

    /**
     * Whether {@code value} has a vanilla tooltip of its own, shown at the pointer while the element
     * has no {@code <ix-tooltip>}; an element without one passes the pointer on to its ancestors.
     */
    default boolean hasTooltip(T value) {
        return false;
    }

    /** Draws the tooltip {@link #hasTooltip} promised, at the pointer in the current pose. */
    default void renderTooltip(GuiGraphics g, Font font, T value, int mouseX, int mouseY) {
    }
}
