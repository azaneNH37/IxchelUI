package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

/**
 * A readable node of a view model's data graph. {@link #version()} grows only when the value really
 * changed, so a consumer that remembers the version it last read can skip unchanged inputs.
 */
@HostApi
public abstract sealed class Cell<T> permits Value, Polled, Held, Derived, Flat, Param, TwoWay {
    Cell() {
    }

    public abstract T get();

    /** Brings the cell up to date and returns its version. */
    abstract long version();
}
