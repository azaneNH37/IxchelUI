package com.azane.ixchel.mc;

import com.azane.ixchel.core.dom.Element;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla tooltips for the element under the pointer: the item tooltip of an {@code <item>}, or the
 * text of a {@code title} attribute. The deepest element that has either wins, so a {@code title} on
 * a row does not hide the item tooltip of an icon inside it. A screen draws it after painting its
 * document; a HUD gets no pointer and shows none.
 *
 * <pre>{@code
 * painter.paint(g, document.frame(width, height));
 * Tooltips.render(g, font, document.events().hitTest(mouseX, mouseY), painter.topZ(), mouseX, mouseY);
 * }</pre>
 */
public final class Tooltips {
    /** Title lines longer than this many GUI pixels wrap, as vanilla's own wrapped tooltips do. */
    public static final int TITLE_WIDTH = 200;

    private Tooltips() {
    }

    /**
     * Draws the tooltip of {@code hovered} or its nearest ancestor that has one, at the pointer. An
     * {@code <item>} counts while its stack is not empty right now; the stack is resolved as
     * {@link McPainter} draws it, so a {@code Supplier<ItemStack>} handle is read here. A title
     * breaks at {@code \n} and wraps at {@link #TITLE_WIDTH}; an empty title hides the tooltip.
     *
     * @param hovered the deepest element under the pointer ({@link
     *                com.azane.ixchel.core.event.EventDispatcher#hitTest})
     * @param z       depth in the current pose the tooltip is raised by; {@link McPainter#topZ()} of the
     *                document's painter keeps it above every item and rect the document drew
     */
    public static void render(GuiGraphics g, Font font, Element hovered, float z, int mouseX, int mouseY) {
        for (Element e = hovered; e != null; e = e.parent()) {
            if (e.tag().equals(McPainter.ITEM) && e.payload() != null) {
                ItemStack stack = McPainter.itemOf(e.payload());
                if (!stack.isEmpty()) {
                    PoseStack ps = raise(g, z);
                    g.renderTooltip(font, stack, mouseX, mouseY);
                    ps.popPose();
                    return;
                }
            }
            String title = e.attribute("title");
            if (title != null) {
                if (title.isEmpty()) {
                    return;
                }
                List<FormattedCharSequence> lines = new ArrayList<>();
                for (String line : title.split("\n", -1)) {
                    lines.addAll(font.split(Component.literal(line), TITLE_WIDTH));
                }
                PoseStack ps = raise(g, z);
                g.renderTooltip(font, lines, mouseX, mouseY);
                ps.popPose();
                return;
            }
        }
    }

    private static PoseStack raise(GuiGraphics g, float z) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0, 0, z);
        return ps;
    }
}
