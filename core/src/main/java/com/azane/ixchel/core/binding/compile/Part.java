package com.azane.ixchel.core.binding.compile;

import java.util.ArrayList;
import java.util.List;

/** Something refreshed once per changed frame, in document order. */
interface Part {
    /** Returns whether the DOM changed. */
    boolean update();

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
    }
}
