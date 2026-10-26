package com.azane.ixchel.mc;

import com.azane.ixchel.core.template.TagRegistry;

/** Elements the mc layer adds to the core HTML subset. */
public final class McElements {
    /**
     * {@link TagRegistry#HTML} plus {@code <item bind:item="...">}: an {@code ItemStack} drawn like a
     * vanilla slot item, scaled from 16×16 to the element's border box. Size it in CSS.
     */
    public static final TagRegistry TAGS = TagRegistry.HTML.extend()
            .nativeTag(McPainter.ITEM, McPainter.ITEM)
            .build();

    private McElements() {
    }
}
