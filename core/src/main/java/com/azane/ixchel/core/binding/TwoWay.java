package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

import java.util.function.Consumer;

/**
 * A component's two-way input, made with {@link ViewModel#twoWay()} and stored in a field named after
 * the {@code <component params>} entry. {@link #set} writes to the writable path the user passed; when
 * the user passed none it is this instance's local state, starting at the view's default. It is
 * wired after the constructor: {@link #get()} and {@link #set} throw until then.
 */
@HostApi
public final class TwoWay<T> extends Cell<T> {
    private final ViewModel owner;
    private Cell<T> source;
    private Consumer<T> write;

    TwoWay(ViewModel owner) {
        this.owner = owner;
    }

    @Override
    public T get() {
        requireWired();
        return source.get();
    }

    @Override
    long version() {
        return source == null ? 0 : source.version();
    }

    /** Writes through to where the value lives; bindings see it on the next frame. Client thread. */
    public void set(T v) {
        requireWired();
        write.accept(v);
    }

    void wire(Cell<T> cell, Consumer<T> writer) {
        source = cell;
        write = writer;
        owner.wired(this);
    }

    private void requireWired() {
        if (source == null) {
            throw new IllegalStateException("a TwoWay has no value in its model's constructor: use it in derive, a supplier or an action");
        }
    }
}
