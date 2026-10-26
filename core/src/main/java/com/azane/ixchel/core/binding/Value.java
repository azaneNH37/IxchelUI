package com.azane.ixchel.core.binding;

import java.util.Objects;

/** A stored value: either polled from a {@link Source} or local state set by host code. */
public final class Value<T> extends Cell<T> {
    private final ViewModel owner;
    private final Source<T> source;
    private T value;
    private long version;

    Value(ViewModel owner, Source<T> source, T initial) {
        this.owner = owner;
        this.source = source;
        this.value = initial;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    long version() {
        return version;
    }

    /**
     * Sets local state; bindings see it on the next frame. Setting an equal value does nothing.
     *
     * @throws IllegalStateException on a value backed by a source
     */
    public void set(T v) {
        if (source != null) {
            throw new IllegalStateException("a source-backed value cannot be set");
        }
        if (!Objects.equals(value, v)) {
            value = v;
            version++;
            owner.changed();
        }
    }

    /** Sets a row value without flagging the model: the block writing it already runs in a changed frame. */
    void replace(T v) {
        if (version == 0 || !Objects.equals(value, v)) {
            value = v;
            version++;
        }
    }

    /** Reads the source; returns whether the value changed. */
    boolean poll() {
        T live = source.live.get();
        if (version > 0 && source.same.test(value, live)) {
            return false;
        }
        value = source.snapshot.apply(live);
        version++;
        return true;
    }
}
