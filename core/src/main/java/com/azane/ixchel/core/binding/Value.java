package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

import java.util.Objects;

/** Local state: set by host code, and by the view where a view binds it two-way. */
@HostApi
public final class Value<T> extends Cell<T> {
    private final ViewModel owner;
    private T value;
    private long version;

    Value(ViewModel owner, T initial) {
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

    /** Sets the value; bindings see it on the next frame. Setting an equal value does nothing. */
    public void set(T v) {
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
}
