package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.dom.Element;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * What the visible slots of a closed container screen showed: the menu is gone, so while its
 * document exits each copy replaces its {@code <mc-slot>}'s payload and {@link SlotElement} draws it
 * where the element paints. The copies paint with the rest of the document: moved and scaled by
 * transforms, clipped by {@code overflow}, faded by {@code opacity}.
 */
public final class SlotSnapshot {
    /** One visible slot's content, frozen: its item, or the icon an empty slot shows. */
    static final class Frozen extends Slot {
        private final Pair<ResourceLocation, ResourceLocation> icon;
        final int seed;

        Frozen(ItemStack stack, Pair<ResourceLocation, ResourceLocation> icon, int seed) {
            super(new SimpleContainer(stack), 0, 0, 0);
            this.icon = icon;
            this.seed = seed;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return icon;
        }
    }

    private final Map<Element, Frozen> items;

    SlotSnapshot(Map<Element, Frozen> items) {
        this.items = items;
    }

    /** Hands each copy to its element; call after the document's bindings closed, which would write the live slot back. */
    public void attach() {
        items.forEach(Element::setPayload);
    }
}
