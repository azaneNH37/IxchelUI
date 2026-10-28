package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.paint.NativePainter;

import java.util.Map;

/** The elements the mc layer adds to the core HTML subset, and how they are drawn. */
public final class McElements {
    /**
     * {@link TagRegistry#HTML} plus two native elements, sized in CSS:
     * <ul>
     * <li>{@code <item bind:item="...">}: an item drawn like a vanilla slot item. Bind an
     * {@code ItemStack} snapshot when bindings must see changes, or a {@code Supplier<ItemStack>}
     * handle read at paint time when only the picture changes; keep the handle instance stable.</li>
     * <li>{@code <entity bind:entity="...">}: a {@code LivingEntity} (or a {@code Supplier} of one)
     * standing in the box; in a screen it looks at the pointer.</li>
     * </ul>
     */
    public static final TagRegistry TAGS = TagRegistry.HTML.extend()
            .nativeTag(ItemElement.TAG, ItemElement.TAG)
            .nativeTag(EntityElement.TAG, EntityElement.TAG)
            .build();

    /** The painter of each native element of {@link #TAGS}. */
    public static final Map<String, NativePainter> PAINTERS = Map.of(
            ItemElement.TAG, ItemElement.PAINTER,
            EntityElement.TAG, EntityElement.PAINTER);

    private McElements() {
    }
}
