package com.azane.ixchel.core.binding.compile;

import java.util.ArrayList;
import java.util.List;

/** Something refreshed once per changed frame, in document order, until the copy it belongs to is removed. */
interface Part {
    /** Returns whether the DOM changed. */
    boolean update();

    /** The copy holding this part was really removed: releases what it owns. Idempotent. */
    default void close() {
    }

    /** The parts of one compiled subtree. */
    final class Group implements Part {
        final List<Part> parts = new ArrayList<>();

        @Override
        public boolean update() {
            boolean wrote = false;
            for (int i = 0, n = parts.size(); i < n; i++) {
                wrote |= parts.get(i).update();
            }
            return wrote;
        }

        @Override
        public void close() {
            for (int i = parts.size() - 1; i >= 0; i--) {
                parts.get(i).close();
            }
        }
    }
}
