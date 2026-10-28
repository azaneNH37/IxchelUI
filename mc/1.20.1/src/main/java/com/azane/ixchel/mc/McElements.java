package com.azane.ixchel.mc;

import com.azane.ixchel.core.template.TagRegistry;

/** Elements the mc layer adds to the core HTML subset. */
public final class McElements {
    /**
     * {@link TagRegistry#HTML} plus two native elements, sized in CSS:
     * <ul>
     * <li>{@code <item bind:item="...">}: an item drawn like a vanilla slot item, scaled from 16×16 to
     * the element's border box. Bind an {@code ItemStack} snapshot when bindings must see changes, or
     * a {@code Supplier<ItemStack>} handle that is read at paint time when only the picture changes;
     * keep the handle instance stable.</li>
     * <li>{@code <entity bind:entity="...">}: a {@code LivingEntity} (or a {@code Supplier} of one,
     * read at paint time) standing on the bottom center of the box, scaled to fit, drawn like the
     * player in the vanilla inventory; in a screen it looks at the pointer.</li>
     * </ul>
     */
    public static final TagRegistry TAGS = TagRegistry.HTML.extend()
            .nativeTag(McPainter.ITEM, McPainter.ITEM)
            .nativeTag(McPainter.ENTITY, McPainter.ENTITY)
            .build();

    /**
     * {@link #TAGS} plus {@code <item-slot slot="N">} for {@link UiContainerScreen}: the projection of
     * menu slot {@code N}. The slot's 16×16 item area is centered in the element's border box and
     * written back to {@code Slot.x/y}; vanilla keeps drawing the item and handling clicks, drags
     * and quick moves there. Use {@code bind:slot} for slots from an {@code each} row.
     */
    public static final TagRegistry CONTAINER_TAGS = TAGS.extend()
            .tag(UiContainerScreen.ITEM_SLOT, UiContainerScreen.SLOT_ATTRIBUTE)
            .build();

    private McElements() {
    }
}
