package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Base class for the data a view binds to. Declare cells and actions as fields; the field name is
 * the name used in {@code bind:*} and {@code on:*} attributes. Dependencies are fixed when a cell is
 * built and can only point at cells built before it, so the graph cannot have cycles. A component
 * with a view model of its own declares its {@code <component>}'s parameters and events as ports:
 * {@link #param()}, {@link #twoWay()} and {@link #event()} fields named after them.
 *
 * <pre>{@code
 * final class AmmoView extends ViewModel {
 *     final Cell<Integer> loaded = source(Source.polled(Gun::loaded));
 *     final Derived<Boolean> empty = derive(loaded, n -> n == 0);
 *     final Action<Void> reload = task(() -> Net.request(new Reload()));   // CompletableFuture
 * }
 * }</pre>
 *
 * A frame first runs work posted to the {@link #scope()}, then polls sources and {@linkplain Feed
 * feeds}, then notifies {@linkplain #subscribe subscribers}; bindings read afterwards. The host
 * closes the model with its view; a component's model closes with its instance. Client thread only,
 * except {@link Scope#post}.
 */
@HostApi
public abstract class ViewModel implements AutoCloseable {
    @FunctionalInterface
    public interface Fn3<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    private final Scope scope = new Scope();
    private final List<Polled<?>> sources = new ArrayList<>();
    private final List<Feed.Reference<?, ?>> feeds = new ArrayList<>();
    /** The ports made here and not wired yet. */
    private final List<Object> ports = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean stateChanged = true;

    protected final <T> Cell<T> source(Source<T> source) {
        Polled<T> p = new Polled<>(source);
        sources.add(p);
        return p;
    }

    /**
     * The value of {@code feed} at {@code key}, shared with every other reference to them. The
     * reference ends with this model.
     */
    protected final <K, T> Cell<T> source(Feed<K, T> feed, K key) {
        Feed.Reference<K, T> r = reference(feed, new Held<>(null, key));
        r.key(key);
        return r.cell();
    }

    /**
     * The value of {@code feed} at the current value of {@code key}, which is first read when the
     * model is polled: a {@link Param} may be the key. When the key changes, the reference moves
     * from the old instance to the new one.
     */
    protected final <K, T> Cell<T> source(Feed<K, T> feed, Cell<? extends K> key) {
        return reference(feed, key).cell();
    }

    private <K, T> Feed.Reference<K, T> reference(Feed<K, T> feed, Cell<? extends K> key) {
        Feed.Reference<K, T> r = feed.reference(key);
        feeds.add(r);
        scope.own(r);
        return r;
    }

    protected final <T> Value<T> state(T initial) {
        return new Value<>(this, initial);
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

    /** A synchronous action without an argument. */
    protected final Action<Void> action(Runnable handler) {
        return new Action<>(this, a -> handler.run(), null);
    }

    /** A synchronous action taking one argument. */
    protected final <A> Action<A> action(Consumer<A> handler) {
        return new Action<>(this, handler, null);
    }

    /** An asynchronous action without an argument; see {@link Action}. */
    protected final Action<Void> task(Supplier<? extends CompletableFuture<?>> handler) {
        return new Action<>(this, null, a -> handler.get());
    }

    /** An asynchronous action taking one argument; see {@link Action}. */
    protected final <A> Action<A> task(Function<A, ? extends CompletableFuture<?>> handler) {
        return new Action<>(this, null, handler::apply);
    }

    /** A read-only component parameter; see {@link Param}. */
    protected final <T> Param<T> param() {
        Param<T> p = new Param<>(this);
        ports.add(p);
        return p;
    }

    /** A two-way component parameter; see {@link TwoWay}. */
    protected final <T> TwoWay<T> twoWay() {
        TwoWay<T> p = new TwoWay<>(this);
        ports.add(p);
        return p;
    }

    /** A component event; see {@link Event}. */
    protected final <T> Event<T> event() {
        Event<T> p = new Event<>(this);
        ports.add(p);
        return p;
    }

    /** The model's lifetime: posting from other threads, owning resources. */
    public final Scope scope() {
        return scope;
    }

    /**
     * Calls {@code listener} at frame start whenever {@code cell} changed, for consumers outside the
     * UI tree. The subscription ends when the returned handle or the model is closed.
     */
    public final AutoCloseable subscribe(Cell<?> cell, Runnable listener) {
        Subscription s = new Subscription(cell, listener, cell.version());
        subscriptions.add(s);
        AutoCloseable handle = () -> subscriptions.remove(s);
        scope.own(handle);
        return handle;
    }

    /** Ends the model's lifetime: subscriptions and feed references end, pending replies are dropped, actions do nothing. */
    @Override
    public void close() {
        scope.close();
    }

    void changed() {
        stateChanged = true;
    }

    void wired(Object port) {
        ports.remove(port);
    }

    /** How many ports made here are not wired yet. */
    int unwiredPorts() {
        return ports.size();
    }

    /**
     * Runs posted work, polls every source and feed once and notifies subscribers; returns whether
     * any source, feed or local state changed since the last call.
     */
    boolean poll() {
        scope.drain();
        boolean changed = stateChanged;
        stateChanged = false;
        for (int i = 0, n = sources.size(); i < n; i++) {
            changed |= sources.get(i).poll();
        }
        for (int i = 0, n = feeds.size(); i < n; i++) {
            changed |= feeds.get(i).poll();
        }
        if (changed) {
            for (int i = 0; i < subscriptions.size(); i++) {
                Subscription s = subscriptions.get(i);
                long v = s.cell.version();
                if (v != s.seen) {
                    s.seen = v;
                    s.listener.run();
                }
            }
        }
        return changed;
    }

    private static final class Subscription {
        final Cell<?> cell;
        final Runnable listener;
        long seen;

        Subscription(Cell<?> cell, Runnable listener, long seen) {
            this.cell = cell;
            this.listener = listener;
            this.seen = seen;
        }
    }
}
