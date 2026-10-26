package com.azane.ixchel.core.binding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Base class for the data a template binds to. Declare cells as fields; the field name is the name
 * used in {@code bind:*} attributes. Dependencies are fixed when a cell is built and can only point
 * at cells built before it, so the graph cannot have cycles.
 *
 * <pre>{@code
 * final class AmmoView extends ViewModel {
 *     final Value<Integer> loaded = source(Source.polled(Gun::loaded));
 *     final Derived<Boolean> empty = derive(loaded, n -> n == 0);
 * }
 * }</pre>
 *
 * Client thread only.
 */
public abstract class ViewModel {
    @FunctionalInterface
    public interface Fn3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    private final List<Value<?>> sources = new ArrayList<>();
    private boolean stateChanged = true;

    protected final <T> Value<T> source(Source<T> source) {
        Value<T> v = new Value<>(this, source, null);
        sources.add(v);
        return v;
    }

    protected final <T> Value<T> state(T initial) {
        return new Value<>(this, null, initial);
    }

    @SuppressWarnings("unchecked")
    protected final <A, R> Derived<R> derive(Cell<A> a, Function<? super A, ? extends R> fn) {
        return new Derived<>(new Cell<?>[]{a}, args -> fn.apply((A) args[0]));
    }

    @SuppressWarnings("unchecked")
    protected final <A, B, R> Derived<R> derive(Cell<A> a, Cell<B> b,
                                                BiFunction<? super A, ? super B, ? extends R> fn) {
        return new Derived<>(new Cell<?>[]{a, b}, args -> fn.apply((A) args[0], (B) args[1]));
    }

    @SuppressWarnings("unchecked")
    protected final <A, B, C, R> Derived<R> derive(Cell<A> a, Cell<B> b, Cell<C> c,
                                                   Fn3<? super A, ? super B, ? super C, ? extends R> fn) {
        return new Derived<>(new Cell<?>[]{a, b, c}, args -> fn.apply((A) args[0], (B) args[1], (C) args[2]));
    }

    void changed() {
        stateChanged = true;
    }

    /** Polls every source once; returns whether any source or local state changed since the last call. */
    boolean poll() {
        boolean changed = stateChanged;
        stateChanged = false;
        for (int i = 0, n = sources.size(); i < n; i++) {
            changed |= sources.get(i).poll();
        }
        return changed;
    }
}
