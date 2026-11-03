package com.azane.ixchel.mc.container;

import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Slot items drawn by the screen instead of vanilla's slot loop, each clipped by its {@code overflow}
 * ancestors, so slots in a scroll list are cut at its edge. Vanilla's loop is hidden by a zero-area
 * scissor, which also hides what other mods draw in that loop. Not drawn: the preview of a
 * right-click split and of a drag across slots (vanilla keeps their state private); the stacks
 * themselves are correct.
 */
public final class ClippedSlotItems {
    /** Vanilla's slot item depth in {@code renderSlot}. */
    static final float SLOT_ITEM_Z = 100;

    private final Renderable hideVanillaSlots = (g, mx, my, pt) -> g.enableScissor(0, 0, 0, 0);

    /** Before vanilla renders {@code screen}: renderables run right before the slot loop, and other mods may append theirs after init. */
    public void hideVanillaLoop(Screen screen) {
        List<Renderable> renderables = Renderables.of(screen);
        renderables.remove(hideVanillaSlots);
        renderables.add(hideVanillaSlots);
    }

    /** From {@code renderLabels}, in the screen's GUI-area pose: draws every visible slot item and the highlight of the hovered one. */
    public void draw(GuiGraphics g, Font font, AbstractContainerMenu menu, ContainerLayout layout, Slot hovered, int imageWidth) {
        g.disableScissor();
        for (Slot slot : menu.slots) {
            int[] clip = layout.clip(slot);
            if (clip == null || !slot.isActive()) {
                continue;
            }
            g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
            drawItem(g, font, slot, imageWidth);
            if (slot == hovered && slot.isHighlightable()) {
                AbstractContainerScreen.renderSlotHighlight(g, slot.x, slot.y, 0);
            }
            g.disableScissor();
        }
    }

    /** Vanilla's {@code renderSlot} without the private split and drag previews. */
    private static void drawItem(GuiGraphics g, Font font, Slot slot, int imageWidth) {
        ItemStack stack = slot.getItem();
        g.pose().pushPose();
        g.pose().translate(0, 0, SLOT_ITEM_Z);
        if (stack.isEmpty()) {
            Pair<ResourceLocation, ResourceLocation> icon = slot.getNoItemIcon();
            if (icon != null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
                g.blit(slot.x, slot.y, 0, ContainerLayout.ITEM_SIZE, ContainerLayout.ITEM_SIZE, sprite);
            }
        } else {
            g.renderItem(stack, slot.x, slot.y, slot.x + slot.y * imageWidth);
            g.renderItemDecorations(font, stack, slot.x, slot.y);
        }
        g.pose().popPose();
    }

    /**
     * {@code Screen.renderables}: private in vanilla, public only through Forge's access transformer,
     * so read by reflection like {@link SlotPositions} (development name, then SRG). Loaded on the
     * first frame that hides the vanilla loop.
     */
    private static final class Renderables {
        private static final MethodHandle GET = getter("renderables", "f_169369_");

        @SuppressWarnings("unchecked")
        static List<Renderable> of(Screen screen) {
            try {
                return (List<Renderable>) GET.invokeExact(screen);
            } catch (Throwable t) {
                throw new IllegalStateException("reading Screen.renderables failed", t);
            }
        }

        private static MethodHandle getter(String... names) {
            for (String name : names) {
                try {
                    Field f = Screen.class.getDeclaredField(name);
                    f.setAccessible(true);
                    return MethodHandles.lookup().unreflectGetter(f)
                            .asType(MethodType.methodType(List.class, Screen.class));
                } catch (NoSuchFieldException ignored) {
                    // try the next candidate name
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Screen." + name + " is not readable", e);
                }
            }
            throw new IllegalStateException("Screen field not found under " + Arrays.toString(names));
        }
    }
}
