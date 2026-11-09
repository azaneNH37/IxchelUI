package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.view.UiElement;
import com.azane.ixchel.mc.paint.ElementPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * {@code <mc-item-cycle bind:items="..." interval="1000">}: one of {@code items} at a time, drawn and
 * tooltipped like {@code <mc-item>}, stepping every {@code interval} ms on the GUI tick clock, so the
 * cycles of a screen stay in phase and stop while the game is paused. It holds its item while hovered.
 * An empty list draws nothing.
 */
@UiElement(tag = "mc-item-cycle", payload = "items", attributes = {"count", "decorations", "interval", "spin"})
final class ItemCycleElement implements ElementPainter<List<ItemStack>> {
    @Override
    public float paint(GuiGraphics g, DrawCommand.Custom c, List<ItemStack> items, float z, float pointerX, float pointerY) {
        return ItemDrawing.paint(g, c, current(c.element(), items), z);
    }

    @Override
    public boolean hasTooltip(Element e, List<ItemStack> items) {
        ItemStack stack = current(e, items);
        return stack != null && !stack.isEmpty();
    }

    @Override
    public void renderTooltip(GuiGraphics g, Font font, Element e, List<ItemStack> items, int mouseX, int mouseY) {
        g.renderTooltip(font, current(e, items), mouseX, mouseY);
    }

    /** The item {@code e} shows now, or {@code null} for an empty list. */
    private static ItemStack current(Element e, List<ItemStack> items) {
        if (items.isEmpty()) {
            return null;
        }
        ItemCycle cycle = e.hostState() instanceof ItemCycle s ? s : null;
        if (cycle == null) {
            cycle = new ItemCycle();
            e.setHostState(cycle);
        }
        int index = cycle.index(Minecraft.getInstance().gui.getGuiTicks(),
                ItemCycle.intervalTicks(e.attribute("interval")), items.size(), e.hasState(Element.HOVER));
        return items.get(index);
    }
}
