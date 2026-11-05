package com.azane.ixchel.mc.element;

/** Which item an {@code <mc-item-cycle>} shows: its per-element state, holding the item while hovered. */
final class ItemCycle {
    static final int DEFAULT_INTERVAL_MS = 1000;
    static final int MS_PER_TICK = 50;

    private boolean held;
    private int heldIndex;

    /** {@code interval} in ms as GUI ticks, at least one; the default for a missing or malformed value. */
    static int intervalTicks(String interval) {
        int ms = DEFAULT_INTERVAL_MS;
        if (interval != null) {
            try {
                ms = (int) Double.parseDouble(interval.trim());
            } catch (NumberFormatException ignored) {
                // keeps the default
            }
        }
        return Math.max(1, ms / MS_PER_TICK);
    }

    /**
     * The index to show of {@code size > 0} items at {@code guiTicks}: {@code (guiTicks / intervalTicks) % size},
     * or while {@code hovered} the index shown when the hover began.
     */
    int index(int guiTicks, int intervalTicks, int size, boolean hovered) {
        if (!hovered) {
            held = false;
        } else if (!held) {
            held = true;
            heldIndex = Math.floorMod(guiTicks / intervalTicks, size);
        }
        return held ? heldIndex % size : Math.floorMod(guiTicks / intervalTicks, size);
    }
}
