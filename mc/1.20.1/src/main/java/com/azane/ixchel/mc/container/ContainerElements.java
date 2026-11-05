package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.runtime.UiRegistry;

/** The element and components of this package, as the mc layer registers them. */
public final class ContainerElements {
    private ContainerElements() {
    }

    /** Adds {@code <mc-slot bind:menuslot>} and {@code <mc-player-inventory bind:menu>} to {@code mc}, the mc layer's registry. */
    public static UiRegistry into(UiRegistry mc) {
        return mc.element(new SlotElement())
                .component(PlayerInventoryModel.class, PlayerInventoryModel::new);
    }
}
