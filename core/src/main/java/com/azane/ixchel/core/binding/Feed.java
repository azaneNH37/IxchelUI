package com.azane.ixchel.core.binding;

import com.azane.ixchel.core.api.HostApi;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * External data addressed by a key and shared between view models. Define a feed as a static constant;
 * its read depends only on the key. Every {@link ViewModel#source(Feed, Object) source} of one feed
 * and key reads the same instance, so however many models poll it, it is read once per cycle: per
 * pass by default (one {@code Bindings.update}), or per game tick after {@link #perTick()}.
 *
 * <pre>{@code
 * static final Feed<BlockPos, MachineState> MACHINE = Feed.polled(pos -> ...);
 * static final Feed<BlockPos, MachineState> WATCHED = Feed.opened(
 *         pos -> Replication.watch(Network.MACHINE, pos),      // first reference
 *         replica -> replica.get());                           // closed on the last release
 *
 * final Param<BlockPos> pos = param();
 * final Cell<MachineState> machine = source(MACHINE, pos);     // follows the key
 * }</pre>
 *
 * Values follow the rules of {@link Source}. A model's reference ends with its {@link Scope}; an
 * instance ends with its last reference. A {@code null} key reads as {@code null}, without touching
 * the feed. Client thread only, except {@link #tickClock}.
 */
@HostApi
public final class Feed<K, T> {
    private static final long NEVER = Long.MIN_VALUE;
    private static long pass;
    private static volatile LongSupplier ticks;

    /** How one instance reads, and the resource to close with it, or {@code null}. */
    private record Opened<T>(Supplier<T> live, AutoCloseable resource) {
    }

    private final Function<K, Opened<T>> open;
    private final UnaryOperator<T> snapshot;
    private final BiPredicate<T, T> same;
    private final boolean perTick;
    private final Map<K, Instance<T>> instances = new HashMap<>();

    private Feed(Function<K, Opened<T>> open, UnaryOperator<T> snapshot, BiPredicate<T, T> same, boolean perTick) {
        this.open = open;
        this.snapshot = snapshot;
        this.same = same;
        this.perTick = perTick;
    }

    /** A feed of immutable values, compared with {@link Objects#equals}. */
    public static <K, T> Feed<K, T> polled(Function<? super K, ? extends T> read) {
        return polled(read, v -> v, Objects::equals);
    }

    /**
     * A feed of mutable values.
     *
     * @param snapshot copies the live value; the copy is what bindings see
     * @param same     compares the last snapshot (first) with the live value (second)
     */
    public static <K, T> Feed<K, T> polled(Function<? super K, ? extends T> read, UnaryOperator<T> snapshot,
                                           BiPredicate<T, T> same) {
        return new Feed<>(key -> new Opened<>(() -> read.apply(key), null), snapshot, same, false);
    }

    /**
     * A feed of immutable values read from a resource opened for each key: on the key's first
     * reference, and closed on its last release.
     */
    public static <K, R extends AutoCloseable, T> Feed<K, T> opened(Function<? super K, ? extends R> open,
                                                                    Function<? super R, ? extends T> read) {
        return new Feed<>(key -> {
            R resource = open.apply(key);
            return new Opened<>(() -> read.apply(resource), resource);
        }, v -> v, Objects::equals, false);
    }

    /** A feed reading like this one, once per game tick; it has instances of its own. */
    public Feed<K, T> perTick() {
        return perTick ? this : new Feed<>(open, snapshot, same, true);
    }

    /**
     * Sets the clock of {@link #perTick()} feeds, a count that moves once per game tick. The host
     * installs it; without one ({@code null}) they read per pass. Any thread.
     */
    public static void tickClock(LongSupplier ticks) {
        Feed.ticks = ticks;
    }

    /** Starts a pass: every instance polled from now on reads again. */
    static void nextPass() {
        pass++;
    }

    /** A reference from a model, without a key yet. */
    Reference<K, T> reference(Cell<? extends K> key) {
        return new Reference<>(this, key);
    }

    private long cycle() {
        LongSupplier clock = perTick ? ticks : null;
        return clock != null ? clock.getAsLong() : pass;
    }

    private Instance<T> acquire(K key) {
        Instance<T> i = instances.get(key);
        if (i == null) {
            Opened<T> opened = open.apply(key);
            i = new Instance<>(new Polled<>(Source.polled(opened.live(), snapshot, same)), opened.resource());
            instances.put(key, i);
        }
        i.references++;
        return i;
    }

    private void release(K key, Instance<T> i) {
        if (--i.references > 0) {
            return;
        }
        instances.remove(key);
        if (i.resource != null) {
            try {
                i.resource.close();
            } catch (Exception e) {
                throw new IllegalStateException("closing " + i.resource + " failed", e);
            }
        }
    }

    /** The shared cell of one key. */
    private static final class Instance<T> {
        final Polled<T> cell;
        final AutoCloseable resource;
        int references;
        long readAt = NEVER;

        Instance(Polled<T> cell, AutoCloseable resource) {
            this.cell = cell;
            this.resource = resource;
        }
    }

    /** One model's use of a feed: its current instance, followed through {@link #cell()}. */
    static final class Reference<K, T> implements AutoCloseable {
        private final Feed<K, T> feed;
        private final Cell<? extends K> keyCell;
        private final Held<Cell<T>> current = new Held<>(null, null);
        private final Flat<T> cell = new Flat<>(current);
        private K key;
        private Instance<T> instance;
        private long seen;
        private boolean closed;

        private Reference(Feed<K, T> feed, Cell<? extends K> keyCell) {
            this.feed = feed;
            this.keyCell = keyCell;
        }

        Cell<T> cell() {
            return cell;
        }

        /** Releases the current instance and takes the one of {@code next}, if not {@code null}. */
        void key(K next) {
            if (instance != null) {
                Instance<T> old = instance;
                instance = null;
                feed.release(key, old);
            }
            key = next;
            if (next != null) {
                instance = feed.acquire(next);
            }
            current.replace(instance == null ? null : instance.cell);
        }

        /** Follows the key and reads the instance, unless this cycle already did; returns whether the value moved. */
        boolean poll() {
            if (closed) {
                return false;
            }
            K k = keyCell.get();
            boolean moved = !Objects.equals(k, key);
            if (moved) {
                key(k);
            }
            if (instance == null) {
                return moved;
            }
            long now = feed.cycle();
            if (instance.readAt != now) {
                instance.readAt = now;
                instance.cell.poll();
            }
            long v = instance.cell.version();
            moved |= v != seen;
            seen = v;
            return moved;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                key(null);
            }
        }
    }
}
