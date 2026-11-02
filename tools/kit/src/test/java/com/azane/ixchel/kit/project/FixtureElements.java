package com.azane.ixchel.kit.project;

import com.azane.ixchel.core.template.NativeElement;
import com.azane.ixchel.core.template.NativePayload;

/** Declarations standing in for the mc layer's, found under the test classes. */
final class FixtureElements {
    private FixtureElements() {
    }

    @NativeElement(tag = "mc-item", payload = "item")
    static final class Item implements NativePayload<Integer> {
    }

    @NativeElement(tag = "mc-slot", attributes = "index", containerOnly = true)
    static final class Slot {
    }
}
