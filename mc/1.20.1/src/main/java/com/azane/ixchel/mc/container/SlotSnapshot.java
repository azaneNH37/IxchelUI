package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.EventDispatcher;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What the visible slots of a closed container screen showed: the menu is gone, so these copies are
 * drawn while its document exits, each following where its {@code <mc-slot>} paints. Only the
 * position follows: transforms do not scale these items and {@code opacity} does not fade them.
 */
public final class SlotSnapshot {
    /** One visible slot: its item area and clip in document coordinates when the screen closed. */
    record Entry(Element element, ItemStack stack, Pair<ResourceLocation, ResourceLocation> icon,
                 int x, int y, int[] clip, float[] center, int seed) {
    }

    private final List<Entry> entries;

    SlotSnapshot(List<Entry> entries) {
        this.entries = entries;
    }

    /** Draws the items over the exiting document. */
    public void draw(GuiGraphics g, Font font) {
        for (Entry e : entries) {
            float[] now = EventDispatcher.paintedCenter(e.element);
            int dx = Math.round(now[0] - e.center[0]);
            int dy = Math.round(now[1] - e.center[1]);
            int x = e.x + dx;
            int y = e.y + dy;
            g.enableScissor(e.clip[0] + dx, e.clip[1] + dy, e.clip[2] + dx, e.clip[3] + dy);
            g.pose().pushPose();
            g.pose().translate(0, 0, ClippedSlotItems.SLOT_ITEM_Z);
            if (e.stack.isEmpty()) {
                if (e.icon != null) {
                    TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(e.icon.getFirst()).apply(e.icon.getSecond());
                    g.blit(x, y, 0, ContainerLayout.ITEM_SIZE, ContainerLayout.ITEM_SIZE, sprite);
                }
            } else {
                g.renderItem(e.stack, x, y, e.seed);
                g.renderItemDecorations(font, e.stack, x, y);
            }
            g.pose().popPose();
            g.disableScissor();
        }
    }
}
