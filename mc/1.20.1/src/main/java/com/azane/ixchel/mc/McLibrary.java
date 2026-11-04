package com.azane.ixchel.mc;

import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.mc.container.PlayerInventoryView;
import com.azane.ixchel.mc.element.TooltipLinesView;

/**
 * The library every document of this layer gets: {@link Library#CORE} plus the files under
 * {@code @ixchel/mc/}, read relative to this package, {@code <mc-tooltip-lines>} and
 * {@code <mc-player-inventory>}, which places {@code <mc-slot>}s and so works in container screens only.
 * Its initialization touches no game state, so tools can read it.
 */
public final class McLibrary {
    public static final Library LIBRARY = Library.CORE.extend()
            .root("mc/", McLibrary.class)
            .component("mc-tooltip-lines", TooltipLinesView.class, TooltipLinesView::new)
            .component("mc-player-inventory", PlayerInventoryView.class, PlayerInventoryView::new)
            .build();

    private McLibrary() {
    }
}
