package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.mc.paint.NativePainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * What the visible slots of a closed container screen showed: the menu is gone, so while its
 * document exits each copy becomes its {@code <mc-slot>}'s payload, drawn by {@link #PAINTER} where
 * the element paints. The copies paint with the rest of the document: moved and scaled by
 * transforms, clipped by {@code overflow}, faded by {@code opacity}.
 */
public final class SlotSnapshot {
    /** Draws a slot's copy centered in its element; a container screen's document has it for {@code <mc-slot>}. */
    public static final NativePainter<Item> PAINTER = new NativePainter<>() {
        /** The count over an item is drawn 200 above it. */
        private static final float DEPTH = 210f;

        @Override
        public float paint(GuiGraphics g, DrawCommand.Custom c, Item item, float z, float pointerX, float pointerY) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(c.x() + (c.width() - ContainerLayout.ITEM_SIZE) / 2, c.y() + (c.height() - ContainerLayout.ITEM_SIZE) / 2, z);
            if (item.stack.isEmpty()) {
                if (item.icon != null) {
                    TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(item.icon.getFirst()).apply(item.icon.getSecond());
                    g.blit(0, 0, 0, ContainerLayout.ITEM_SIZE, ContainerLayout.ITEM_SIZE, sprite);
                }
            } else {
                g.renderItem(item.stack, 0, 0, item.seed);
                g.renderItemDecorations(Minecraft.getInstance().font, item.stack, 0, 0);
            }
            ps.popPose();
            return DEPTH;
        }
    };

    /** One visible slot's content: its item, or the icon an empty slot shows. */
    record Item(ItemStack stack, Pair<ResourceLocation, ResourceLocation> icon, int seed) {
    }

    private final Map<Element, Item> items;

    SlotSnapshot(Map<Element, Item> items) {
        this.items = items;
    }

    /** Hands each copy to its element; the document must have {@link #PAINTER} for {@code <mc-slot>}. */
    public void attach() {
        items.forEach(Element::setPayload);
    }
}
