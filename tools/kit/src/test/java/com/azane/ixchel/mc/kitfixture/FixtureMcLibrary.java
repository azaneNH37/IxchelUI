package com.azane.ixchel.mc.kitfixture;

import com.azane.ixchel.core.runtime.UiRegistry;

/** The registry of the kit tests' stand-in mc layer: {@code <mc-kit-gauge>}, read from {@code ui/} next to this class. */
public final class FixtureMcLibrary {
    public static final UiRegistry MC = UiRegistry.library()
            .root("kitfixture/", FixtureMcLibrary.class, "ui/")
            .component(FixtureGaugeModel.class, FixtureGaugeModel::new);

    private FixtureMcLibrary() {
    }
}
