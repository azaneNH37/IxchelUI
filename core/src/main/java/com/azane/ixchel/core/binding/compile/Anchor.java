package com.azane.ixchel.core.binding.compile;

import com.azane.ixchel.core.dom.Node;

/** A point among a parent's children: the last mounted node at or before it, or {@code null}. */
interface Anchor {
    Anchor START = () -> null;

    Node last();
}
