package com.azane.ixchel.mc.kitfixture;

import com.azane.ixchel.core.binding.Param;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;

/** A library component standing in for the mc layer's, found by the kit tests' stand-in mc layer. */
@UiView("@ixchel/kitfixture/kit-gauge.html")
public final class FixtureGaugeModel extends ViewModel {
    final Param<Integer> level = param();
}
