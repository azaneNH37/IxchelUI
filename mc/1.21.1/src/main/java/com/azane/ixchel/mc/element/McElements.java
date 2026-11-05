package com.azane.ixchel.mc.element;

import com.azane.ixchel.core.runtime.UiRegistry;

/** The elements drawn through vanilla and the components of this package, as the mc layer registers them. */
public final class McElements {
    private McElements() {
    }

    /**
     * Adds {@code <mc-item bind:item>}, {@code <mc-item-cycle bind:items>} and {@code <mc-tooltip-lines>} to
     * {@code mc}, the mc layer's registry.
     */
    public static UiRegistry into(UiRegistry mc) {
        return mc.element(new ItemElement())
                .element(new ItemCycleElement())
                .component(TooltipLinesModel.class, TooltipLinesModel::new);
    }
}
