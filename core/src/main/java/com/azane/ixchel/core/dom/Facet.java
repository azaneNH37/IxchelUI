package com.azane.ixchel.core.dom;

/** Identity key for a pipeline contract. Defaults are shared, stateless implementations. */
public final class Facet<F> {
    private final F fallback;

    public Facet(F fallback) {
        this.fallback = fallback;
    }

    F fallback() {
        return fallback;
    }
}
