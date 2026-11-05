package com.azane.ixchel.mc.element;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemCycleTest {
    // Fails if the interval is not converted at 50 ms per tick, can drop to zero ticks, or ignores the default.
    @Test
    void intervalConvertsAtFiftyMsPerTickAndNeverBelowOne() {
        assertEquals(20, ItemCycle.intervalTicks(null));
        assertEquals(20, ItemCycle.intervalTicks("bad"));
        assertEquals(10, ItemCycle.intervalTicks("500"));
        assertEquals(1, ItemCycle.intervalTicks("10"));
        assertEquals(1, ItemCycle.intervalTicks("0"));
    }

    // Fails if the index does not step once per interval and wrap at the list size.
    @Test
    void indexStepsPerIntervalAndWraps() {
        ItemCycle c = new ItemCycle();
        assertEquals(0, c.index(19, 20, 3, false));
        assertEquals(1, c.index(20, 20, 3, false));
        assertEquals(0, c.index(60, 20, 3, false));
    }

    // Fails if a hovered cycle keeps stepping, or stays held once the hover ends.
    @Test
    void hoverHoldsTheItemShownWhenItBegan() {
        ItemCycle c = new ItemCycle();
        assertEquals(1, c.index(20, 20, 3, true));
        assertEquals(1, c.index(45, 20, 3, true));
        assertEquals(2, c.index(45, 20, 3, false));
    }
}
