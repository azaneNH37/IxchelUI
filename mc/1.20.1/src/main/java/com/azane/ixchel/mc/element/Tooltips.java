package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.view.ElementPayload;
import com.azane.ixchel.mc.paint.ElementPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The tooltip of the element under the pointer. An element has one when it has an {@code <ix-tooltip>}
 * child (the author's content, shown in the document's top layer), is a drawn element whose painter
 * {@linkplain ElementPainter#hasTooltip has one} for its value now (an {@code <mc-item>}'s item tooltip),
 * or has a {@code title} attribute (vanilla-style plain text). The deepest element that has one wins,
 * so a {@code title} on a row does not hide the item tooltip of an icon inside it; on one element, the
 * {@code <ix-tooltip>} wins, then the painter's. A screen picks the target before framing its document
 * and draws it after painting; a HUD gets no pointer and shows none.
 */
public final class Tooltips {
    /** Title lines longer than this many GUI pixels wrap, as vanilla's own wrapped tooltips do. */
    public static final int TITLE_WIDTH = 200;
    /** How far above the document vanilla raises its tooltips; the {@code <ix-tooltip>} goes as high. */
    public static final float Z = 400;

    private Tooltips() {
    }

    /**
     * {@code hovered} or its nearest ancestor that has a tooltip, or {@code null} when none has or the
     * nearest {@code title} is empty.
     *
     * @param hovered  the deepest element under the pointer ({@link
     *                 com.azane.ixchel.core.event.EventDispatcher#hitTest})
     * @param painters the painter of each drawn element, by tag
     */
    public static Element target(Element hovered, Map<String, ElementPainter<?>> painters) {
        for (Element e = hovered; e != null; e = e.parent()) {
            if (tooltipOf(e) != null || nativeTooltip(e, painters) != null) {
                return e;
            }
            String title = e.attribute("title");
            if (title != null) {
                return title.isEmpty() ? null : e;
            }
        }
        return null;
    }

    /** The {@code <ix-tooltip>} child of {@code e}, or {@code null}. */
    public static Element tooltipOf(Element e) {
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof Element c && c.type().facet(LayerFacet.KEY).pointer() == LayerFacet.Pointer.TOOLTIP) {
                return c;
            }
        }
        return null;
    }

    /**
     * Draws the vanilla tooltip of a {@link #target} without an {@code <ix-tooltip>}: its painter's,
     * resolved again now, or the title, broken at {@code \n} and wrapped at {@link #TITLE_WIDTH}.
     *
     * @param z depth in the current pose the tooltip is raised by, above everything the document drew
     */
    @SuppressWarnings("unchecked")
    public static void renderVanilla(GuiGraphics g, Font font, Element target, Map<String, ElementPainter<?>> painters,
                                     float z, int mouseX, int mouseY) {
        if (tooltipOf(target) != null) {
            return;
        }
        Object value = nativeTooltip(target, painters);
        if (value != null) {
            PoseStack ps = raise(g, z);
            ((ElementPainter<Object>) painters.get(target.tag())).renderTooltip(g, font, target, value, mouseX, mouseY);
            ps.popPose();
            return;
        }
        String title = target.attribute("title");
        if (title == null || title.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String line : title.split("\n", -1)) {
            lines.addAll(font.split(Component.literal(line), TITLE_WIDTH));
        }
        PoseStack ps = raise(g, z);
        g.renderTooltip(font, lines, mouseX, mouseY);
        ps.popPose();
    }

    /** The value of drawn element {@code e} when its painter has a tooltip for it now, else {@code null}. */
    @SuppressWarnings("unchecked")
    private static Object nativeTooltip(Element e, Map<String, ElementPainter<?>> painters) {
        if (e.payload() == null) {
            return null;
        }
        ElementPainter<Object> p = (ElementPainter<Object>) painters.get(e.tag());
        Object value = ElementPayload.valueOf(e.payload(), e.type());
        return p != null && value != null && p.hasTooltip(e, value) ? value : null;
    }

    private static PoseStack raise(GuiGraphics g, float z) {
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0, 0, z);
        return ps;
    }
}
