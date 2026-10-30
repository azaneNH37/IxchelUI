package com.azane.ixchel.core.binding;

import java.util.function.Function;

/**
 * What the template compiler needs from the model beyond its public API: cells the framework holds,
 * writes that do not flag the model, and the per-frame poll. Host code cannot use it: {@link #claim()}
 * hands out the one instance once, and the template compiler takes it when it loads.
 */
public interface ModelAccess {
    /** The one instance; throws on every later call. */
    static ModelAccess claim() {
        return Bridge.claim();
    }

    /** A cell templates can read but not write; its value changes only through {@link #replace}. */
    <T> Cell<T> held(T initial);

    /** A local state of {@code owner}, starting at {@code null}. */
    <T> Value<T> state(ViewModel owner);

    /** Sets a state or held cell without flagging its model: the caller already runs in a changed frame or a handler. */
    <T> void replace(Cell<T> cell, T value);

    /** A cell computed from {@code inputs}, recomputed when one of them changed. */
    <T> Cell<T> derived(Cell<?>[] inputs, Function<Object[], T> compute);

    /** The value of the cell {@code outer} currently holds. */
    <T> Cell<T> flat(Cell<? extends Cell<T>> outer);

    /** The inner cell a {@link #flat} cell currently reads. */
    <T> Cell<T> current(Cell<T> flat);

    /**
     * Runs posted work, polls every source once and notifies subscribers; returns whether any source
     * or local state changed since the last call.
     */
    boolean poll(ViewModel model);
}
