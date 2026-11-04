package com.azane.ixchel.mc.kitfixture;

import com.azane.ixchel.core.library.Library;

/** The library of the kit tests' stand-in mc layer: {@code <mc-kit-gauge>}, read next to this class. */
public final class FixtureMcLibrary {
    public static final Library LIBRARY = Library.CORE.extend()
            .root("kitfixture/", FixtureMcLibrary.class)
            .component("mc-kit-gauge", FixtureGaugeView.class, FixtureGaugeView::new)
            .build();

    private FixtureMcLibrary() {
    }
}
