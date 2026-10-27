package com.azane.ixchel.mc;

import com.azane.ixchel.core.dom.Element;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * The vanilla item tooltip for {@code <item>} elements. A screen draws it after painting its
 * document; a HUD gets no pointer and shows none.
 *
 * <pre>{@code
 * painter.paint(g, document.frame(width, height));
 * ItemTooltips.render(g, font, document.events().hitTest(mouseX, mouseY), painter.topZ(), mouseX, mouseY);
 * }</pre>
 */
public final class ItemTooltips {
    private ItemTooltips() {
    }

    /**
     * Draws the tooltip of the stack {@code hovered} shows, at the pointer, when {@code hovered} is an
     * {@code <item>} whose stack is not empty right now. The stack is resolved as {@link McPainter}
     * draws it, so a {@code Supplier<ItemStack>} handle is read here.
     *
     * @param hovered the deepest element under the pointer ({@link
     *                com.azane.ixchel.core.event.EventDispatcher#hitTest})
     * @param z       depth in the current pose the tooltip is raised by; {@link McPainter#topZ()} of the
     *                document's painter keeps it above every item and rect the document drew
     */
    public static void render(GuiGraphics g, Font font, Element hovered, float z, int mouseX, int mouseY) {
        if (!hovered.tag().equals(McPainter.ITEM) || hovered.payload() == null) {
            return;
        }
        ItemStack stack = McPainter.itemOf(hovered.payload());
        if (stack.isEmpty()) {
            return;
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0, 0, z);
        g.renderTooltip(font, stack, mouseX, mouseY);
        ps.popPose();
    }
}
