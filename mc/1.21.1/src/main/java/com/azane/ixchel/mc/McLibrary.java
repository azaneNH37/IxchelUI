package com.azane.ixchel.mc;

import com.azane.ixchel.core.library.Library;
import com.azane.ixchel.mc.element.TooltipLinesView;

/**
 * The library every document of this layer gets: {@link Library#CORE} plus the files under
 * {@code @ixchel/mc/}, read relative to this package, and {@code <mc-tooltip-lines>}. Its
 * initialization touches no game state, so tools can read it.
 */
public final class McLibrary {
    public static final Library LIBRARY = Library.CORE.extend()
            .root("mc/", McLibrary.class)
            .component("mc-tooltip-lines", TooltipLinesView.class, TooltipLinesView::new)
            .build();

    private McLibrary() {
    }
}
