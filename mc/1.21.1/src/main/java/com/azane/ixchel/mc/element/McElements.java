package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.paint.NativePainter;

import java.util.Map;

/** The elements the mc layer adds to the core HTML subset, and how they are drawn. */
public final class McElements {
    /**
     * {@link TagRegistry#HTML} plus {@code <mc-item bind:item="...">}: an item drawn like a vanilla slot
     * item, sized in CSS. Bind an {@code ItemStack} snapshot when bindings must see changes, or a
     * {@code Supplier<ItemStack>} handle read at paint time when only the picture changes; keep the
     * handle instance stable.
     */
    public static final TagRegistry TAGS = TagRegistry.HTML.extend()
            .nativeTag(ItemElement.TAG, ItemElement.PAYLOAD)
            .build();

    /** The painter of each native element of {@link #TAGS}. */
    public static final Map<String, NativePainter> PAINTERS = Map.of(ItemElement.TAG, ItemElement.PAINTER);

    private McElements() {
    }
}
