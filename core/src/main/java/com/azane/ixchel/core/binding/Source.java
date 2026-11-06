package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * External data polled once per frame. Mutable objects must come with a snapshot (copy) and a content
 * comparison: the live object is compared with the last snapshot, and copied only when they differ.
 */
@HostApi
public final class Source<T> {
    final Supplier<T> live;
    final UnaryOperator<T> snapshot;
    final BiPredicate<T, T> same;

    private Source(Supplier<T> live, UnaryOperator<T> snapshot, BiPredicate<T, T> same) {
        this.live = live;
        this.snapshot = snapshot;
        this.same = same;
    }

    /** A source of immutable values, compared with {@link Objects#equals}. */
    public static <T> Source<T> polled(Supplier<T> live) {
        return new Source<>(live, v -> v, Objects::equals);
    }

    /**
     * A source of mutable values.
     *
     * @param snapshot copies the live value; the copy is what bindings see
     * @param same     compares the last snapshot (first) with the live value (second)
     */
    public static <T> Source<T> polled(Supplier<T> live, UnaryOperator<T> snapshot, BiPredicate<T, T> same) {
        return new Source<>(live, snapshot, same);
    }
}
