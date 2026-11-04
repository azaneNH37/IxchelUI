package com.azane.ixchel.core.binding;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * What the template compiler needs from the model beyond its public API: cells the framework holds,
 * writes that do not flag the model, wiring a component's ports, and the per-frame poll. Host code
 * cannot use it: {@link #claim()} hands out the one instance once, and the template compiler takes
 * it when it loads.
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

    /** The current value of {@code member}'s field in {@code model}, whose class has that shape. */
    Object read(ViewModel model, ModelShape.Member member);

    /** An action of {@code owner} that does nothing, standing in for a member when no model instance exists. */
    <A> Action<A> idle(ViewModel owner);

    /** Makes {@code port} read {@code cell}. */
    <T> void wire(Param<T> port, Cell<? extends T> cell);

    /** Makes {@code port} read {@code cell} and write through {@code writer}. */
    <T> void wire(TwoWay<T> port, Cell<T> cell, Consumer<T> writer);

    /** Makes {@code port} emit to {@code listener}. */
    <T> void wire(Event<T> port, Consumer<? super T> listener);

    /** How many of the ports {@code model} made are not wired yet. */
    int unwiredPorts(ViewModel model);

    /** Starts a pass: the next poll of each {@link Feed} instance reads it again. */
    void nextPass();

    /**
     * Runs posted work, polls every source and feed once and notifies subscribers; returns whether
     * any source, feed or local state changed since the last call.
     */
    boolean poll(ViewModel model);
}
