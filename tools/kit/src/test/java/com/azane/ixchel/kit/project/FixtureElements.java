package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.view.UiElement;
import com.azane.ixchel.core.view.ElementPayload;

/** Declarations standing in for the mc layer's, found under the test classes. */
final class FixtureElements {
    private FixtureElements() {
    }

    @UiElement(tag = "mc-item", payload = "item")
    static final class Item implements ElementPayload<Integer> {
    }

    @UiElement(tag = "mc-slot", attributes = "index", containerOnly = true)
    static final class Slot {
    }
}
