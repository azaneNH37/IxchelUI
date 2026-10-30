package com.azane.ixchel.core.binding;

import java.util.Objects;

/**
 * A value the framework holds for templates to read: a block's row, an event, a component's event
 * payload, an action's pending flag and error. Templates cannot write it.
 */
final class Held<T> extends Cell<T> {
    private final ViewModel owner;
    private T value;
    private long version;

    /** @param owner flagged by {@link #set}; {@code null} for a holder only ever {@link #replace replaced} */
    Held(ViewModel owner, T initial) {
        this.owner = owner;
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

    /** Sets the value and flags the owner, so bindings see it on the next frame. */
    void set(T v) {
        if (!Objects.equals(value, v)) {
            value = v;
            version++;
            owner.changed();
        }
    }

    /** Sets the value without flagging the owner: the caller already runs in a changed frame or a handler. */
    void replace(T v) {
        if (version == 0 || !Objects.equals(value, v)) {
            value = v;
            version++;
        }
    }
}
