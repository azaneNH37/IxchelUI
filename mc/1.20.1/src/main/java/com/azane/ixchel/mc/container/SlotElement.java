package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.view.UiElement;
import com.azane.ixchel.mc.paint.ElementPainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * {@code <mc-slot bind:menuslot="...">}: the place of a menu {@link Slot} in a page. The element knows
 * nothing of the screen: a {@link ContainerLayout} reads where it was laid out and writes that back to
 * the slot, whose item area is centered in the border box, and vanilla keeps drawing the item and
 * handling clicks, drags and quick moves there. Outside a container screen nothing reads it. It draws
 * only a {@link SlotSnapshot}'s copy, while the document of a closed screen exits.
 */
@UiElement(tag = SlotElement.TAG, payload = "menuslot")
final class SlotElement implements ElementPainter<Slot> {
    static final String TAG = "mc-slot";

    /** The count over an item is drawn 200 above it. */
    private static final float DEPTH = 210f;

    @Override
    public float paint(GuiGraphics g, DrawCommand.Custom c, Slot slot, float z, float pointerX, float pointerY) {
        if (!(slot instanceof SlotSnapshot.Frozen frozen)) {
            return 0;
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(c.x() + (c.width() - ContainerLayout.ITEM_SIZE) / 2, c.y() + (c.height() - ContainerLayout.ITEM_SIZE) / 2, z);
        ItemStack stack = frozen.getItem();
        if (stack.isEmpty()) {
            Pair<ResourceLocation, ResourceLocation> icon = frozen.getNoItemIcon();
            if (icon != null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
                g.blit(0, 0, 0, ContainerLayout.ITEM_SIZE, ContainerLayout.ITEM_SIZE, sprite);
            }
        } else {
            g.renderItem(stack, 0, 0, frozen.seed);
            g.renderItemDecorations(Minecraft.getInstance().font, stack, 0, 0);
        }
        ps.popPose();
        return DEPTH;
    }
}
