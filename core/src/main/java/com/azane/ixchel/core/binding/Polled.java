package com.azane.ixchel.core.binding;

/** A value polled from a {@link Source} once per frame; read-only to host code and views. */
final class Polled<T> extends Cell<T> {
    private final Source<T> source;
    private T value;
    private long version;

    Polled(Source<T> source) {
        this.source = source;
    }

    @Override
    public T get() {
        return value;
    }

    @Override
    long version() {
        return version;
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
