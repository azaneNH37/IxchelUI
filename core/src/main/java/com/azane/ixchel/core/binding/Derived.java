package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

import java.util.Objects;
import java.util.function.Function;

/**
 * A value computed from up to three cells. Lazy: computed only when read, and recomputed only when an
 * input's version changed. A result equal to the previous one keeps the version, so downstream cells
 * and bindings treat it as unchanged.
 */
@HostApi
public final class Derived<T> extends Cell<T> {
    private final Cell<?>[] inputs;
    private final long[] seen;
    private final Function<Object[], T> compute;
    private final Object[] args;
    private boolean computed;
    private T value;
    private long version;

    Derived(Cell<?>[] inputs, Function<Object[], T> compute) {
        this.inputs = inputs;
        this.seen = new long[inputs.length];
        this.compute = compute;
        this.args = new Object[inputs.length];
    }

    @Override
    public T get() {
        refresh();
        return value;
    }

    @Override
    long version() {
        refresh();
        return version;
    }

    private void refresh() {
        boolean stale = !computed;
        for (int i = 0; i < inputs.length; i++) {
            long v = inputs[i].version();
            if (v != seen[i]) {
                seen[i] = v;
                stale = true;
            }
        }
        if (!stale) {
            return;
        }
        for (int i = 0; i < inputs.length; i++) {
            args[i] = inputs[i].get();
        }
        T next = compute.apply(args);
        if (!computed || !Objects.equals(value, next)) {
            value = next;
            version++;
        }
        computed = true;
    }
}
