package com.azane.ixchel.sample.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Server-to-client state replication in the engine style, as small as the sample needs. A replicated
 * object has an identity (a {@link Kind} and a key of the kind's type: a block position, a menu's
 * container id); a client declares interest by {@linkplain #watch watching} it; every
 * {@code interval} server ticks, at the tick's end, the server samples what that player may see and
 * sends what changed since the last sample; when the player may no longer see it (gone, out of reach,
 * menu closed, logged out) the watch ends and the client replica reads {@code null}.
 *
 * <p>A {@linkplain #snapshot snapshot} kind sends its whole value, a record compared with
 * {@code equals}, whenever it differs. A {@linkplain #collection collection} kind is a map from an
 * identity to a value and sends only the entries that appeared, changed or went away; after its first
 * appearance an identity travels as a small id, fixed for the watch.
 *
 * <p>UIs only read replicas; they change the object through intent packets, which the server
 * validates, and see the result when it replicates back, at once when the handler calls
 * {@link #refresh}. A new kind of object declares its key, its value's codec and its visibility rule,
 * never packet plumbing.
 */
public final class Replication {
    /**
     * A kind of replicated object: its key's codec, how often the server samples it, and how its
     * changes travel. Declared through {@link #snapshot} or {@link #collection}.
     *
     * @param <K> the key naming one object of the kind
     * @param <V> what a replica reads
     */
    public abstract static class Kind<K, V> {
        private final int id;
        private final BiConsumer<FriendlyByteBuf, K> writeKey;
        private final Function<FriendlyByteBuf, K> readKey;
        private final int interval;

        private Kind(BiConsumer<FriendlyByteBuf, K> writeKey, Function<FriendlyByteBuf, K> readKey, int interval) {
            if (interval < 1) {
                throw new IllegalArgumentException("interval " + interval);
            }
            this.id = KINDS.size();
            this.writeKey = writeKey;
            this.readKey = readKey;
            this.interval = interval;
            KINDS.add(this);
        }

        /** Server: the watch of the object at {@code key}, which has sent nothing yet. */
        abstract Sampler sampler(K key);

        abstract void writeChange(Object change, FriendlyByteBuf buf);

        abstract Object readChange(FriendlyByteBuf buf);

        /** Client: the copy of one watched object, shared by its replicas, before any change arrived. */
        abstract Copy<V> copy();
    }

    /** Server side of one watch: what it sent, and how many ticks until its next sample. */
    private abstract static class Sampler {
        int wait;

        /** The change since the last sample, {@code null} when there is none, or {@link #ENDED}. */
        abstract Object sample(ServerPlayer player);
    }

    /** Client side of one watched object: applies a change and returns what the replicas read now. */
    private interface Copy<V> {
        V apply(Object change);
    }

    /** One entry of a collection replica; {@code id} is fixed while the watch lasts, a row key. */
    public record Entry<I, W>(int id, I identity, W value) {
    }

    private record Key(int kind, Object key) {
    }

    private static final Object ENDED = new Object();
    private static final List<Kind<?, ?>> KINDS = new ArrayList<>();
    /** Server: per player, every watched object. */
    private static final Map<UUID, Map<Key, Sampler>> WATCHES = new HashMap<>();
    /**
     * Client: the copy and open replicas of each watched object. A screen's model is built before the
     * screen it replaces closes, so the same object can have two replicas for a moment; they share
     * one server watch.
     */
    private static final Map<Key, Watched> REPLICAS = new HashMap<>();

    private static final class Watched {
        final Copy<?> copy;
        final List<Replica<?>> replicas = new ArrayList<>();

        Watched(Copy<?> copy) {
            this.copy = copy;
        }
    }

    private Replication() {
    }

    /**
     * Declares a kind whose value travels whole whenever it differs; call from {@link Network}'s
     * static initialization so ids match on both sides.
     *
     * @param interval server ticks between samples
     * @param view     the snapshot {@code player} may see of the object at the key, or {@code null} when
     *                 there is none or the player may not see it; snapshots compare with {@code equals}
     */
    static <K, V> Kind<K, V> snapshot(BiConsumer<FriendlyByteBuf, K> writeKey, Function<FriendlyByteBuf, K> readKey,
                                      int interval, BiConsumer<V, FriendlyByteBuf> encode,
                                      Function<FriendlyByteBuf, V> decode, BiFunction<ServerPlayer, K, V> view) {
        return new Snapshot<>(writeKey, readKey, interval, encode, decode, view);
    }

    /**
     * Declares a kind whose value is a map sent as its changes; call from {@link Network}'s static
     * initialization so ids match on both sides. The replica reads the entries in no particular order,
     * a new list after every change.
     *
     * @param interval server ticks between samples
     * @param view     what {@code player} may see of the object at the key, or {@code null} when there is
     *                 none or the player may not see it; identities compare with {@code equals} and
     *                 travel once per watch, values compare with {@code equals} and travel on change
     */
    static <K, I, W> Kind<K, List<Entry<I, W>>> collection(
            BiConsumer<FriendlyByteBuf, K> writeKey, Function<FriendlyByteBuf, K> readKey, int interval,
            BiConsumer<I, FriendlyByteBuf> encodeIdentity, Function<FriendlyByteBuf, I> decodeIdentity,
            BiConsumer<W, FriendlyByteBuf> encodeValue, Function<FriendlyByteBuf, W> decodeValue,
            BiFunction<ServerPlayer, K, Map<I, W>> view) {
        return new Collection<>(writeKey, readKey, interval, encodeIdentity, decodeIdentity, encodeValue, decodeValue, view);
    }

    static void init() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                tick(e.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) ->
                WATCHES.remove(e.getEntity().getUUID()));
        // A single-player world quit and reopened is a new server in the same process.
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> WATCHES.clear());
    }

    /**
     * Client: starts watching the object; the replica reads {@code null} until the first change
     * arrives and after the server ends the watch. Close it to stop.
     */
    public static <K, V> Replica<V> watch(Kind<K, V> kind, K key) {
        Key k = new Key(kind.id, key);
        Watched watched = REPLICAS.get(k);
        Replica<V> replica = new Replica<>();
        if (watched == null) {
            watched = new Watched(kind.copy());
            REPLICAS.put(k, watched);
            Network.sendToServer(new Watch(kind.id, key, true));
        } else {
            replica.set(watched.replicas.get(0).get());
        }
        Watched open = watched;
        replica.onClose(() -> {
            // Absent when the server already ended the watch.
            if (REPLICAS.get(k) == open && open.replicas.remove(replica) && open.replicas.isEmpty()) {
                REPLICAS.remove(k);
                Network.sendToServer(new Watch(kind.id, key, false));
            }
        });
        open.replicas.add(replica);
        return replica;
    }

    /**
     * Server: samples {@code player}'s watch of the object now, out of turn, and sends what changed;
     * for an intent handler, so the client sees the result with the vanilla slot updates of the same
     * tick. Nothing when the player does not watch it.
     */
    public static <K> void refresh(ServerPlayer player, Kind<K, ?> kind, K key) {
        Map<Key, Sampler> watches = WATCHES.get(player.getUUID());
        Key k = new Key(kind.id, key);
        Sampler s = watches == null ? null : watches.get(k);
        if (s != null && !sample(player, k, s)) {
            watches.remove(k);
        }
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Map<Key, Sampler>>> players = WATCHES.entrySet().iterator(); players.hasNext(); ) {
            Map.Entry<UUID, Map<Key, Sampler>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                players.remove();
                continue;
            }
            for (Iterator<Map.Entry<Key, Sampler>> watches = entry.getValue().entrySet().iterator(); watches.hasNext(); ) {
                Map.Entry<Key, Sampler> w = watches.next();
                Sampler s = w.getValue();
                if (s.wait > 0) {
                    s.wait--;
                    continue;
                }
                s.wait = KINDS.get(w.getKey().kind()).interval - 1;
                if (!sample(player, w.getKey(), s)) {
                    watches.remove();
                }
            }
        }
    }

    /** Sends what changed; whether the watch goes on. */
    private static boolean sample(ServerPlayer player, Key key, Sampler s) {
        Object change = s.sample(player);
        if (change != null) {
            Network.sendTo(player, new Update(key.kind(), key.key(), change));
        }
        return change != ENDED;
    }

    @SuppressWarnings("unchecked")
    private static Kind<Object, ?> kindOf(int id) {
        return (Kind<Object, ?>) KINDS.get(id);
    }

    /** Client to server: start or stop watching an object. */
    record Watch(int kind, Object key, boolean on) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            kindOf(kind).writeKey.accept(buf, key);
            buf.writeBoolean(on);
        }

        static Watch decode(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            return new Watch(kind, kindOf(kind).readKey.apply(buf), buf.readBoolean());
        }

        void handle(ServerPlayer player) {
            Map<Key, Sampler> watches = WATCHES.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
            if (on) {
                // Sampled at the end of this tick: the first change holds everything.
                watches.put(new Key(kind, key), kindOf(kind).sampler(key));
            } else {
                watches.remove(new Key(kind, key));
            }
        }
    }

    /** Server to client: a watched object's change, or {@link #ENDED} when the watch ended. */
    record Update(int kind, Object key, Object change) {
        void encode(FriendlyByteBuf buf) {
            Kind<Object, ?> k = kindOf(kind);
            buf.writeVarInt(kind);
            k.writeKey.accept(buf, key);
            buf.writeBoolean(change != ENDED);
            if (change != ENDED) {
                k.writeChange(change, buf);
            }
        }

        static Update decode(FriendlyByteBuf buf) {
            Kind<Object, ?> k = kindOf(buf.readVarInt());
            Object key = k.readKey.apply(buf);
            return new Update(k.id, key, buf.readBoolean() ? k.readChange(buf) : ENDED);
        }

        void handleOnClient() {
            Key k = new Key(kind, key);
            if (change == ENDED) {
                Watched w = REPLICAS.remove(k);
                if (w != null) {
                    w.replicas.forEach(r -> r.set(null));
                }
                return;
            }
            Watched w = REPLICAS.get(k);
            if (w != null) {
                Object value = w.copy.apply(change);
                w.replicas.forEach(r -> r.set(value));
            }
        }
    }

    private static final class Snapshot<K, V> extends Kind<K, V> {
        private final BiConsumer<V, FriendlyByteBuf> encode;
        private final Function<FriendlyByteBuf, V> decode;
        private final BiFunction<ServerPlayer, K, V> view;

        Snapshot(BiConsumer<FriendlyByteBuf, K> writeKey, Function<FriendlyByteBuf, K> readKey, int interval,
                 BiConsumer<V, FriendlyByteBuf> encode, Function<FriendlyByteBuf, V> decode,
                 BiFunction<ServerPlayer, K, V> view) {
            super(writeKey, readKey, interval);
            this.encode = encode;
            this.decode = decode;
            this.view = view;
        }

        @Override
        Sampler sampler(K key) {
            return new Sampler() {
                private V sent;

                @Override
                Object sample(ServerPlayer player) {
                    V now = view.apply(player, key);
                    if (now == null) {
                        return ENDED;
                    }
                    if (now.equals(sent)) {
                        return null;
                    }
                    sent = now;
                    return now;
                }
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        void writeChange(Object change, FriendlyByteBuf buf) {
            encode.accept((V) change, buf);
        }

        @Override
        Object readChange(FriendlyByteBuf buf) {
            return decode.apply(buf);
        }

        @SuppressWarnings("unchecked")
        @Override
        Copy<V> copy() {
            return change -> (V) change;
        }
    }

    private static final class Collection<K, I, W> extends Kind<K, List<Entry<I, W>>> {
        private static final byte REMOVED = 0;
        private static final byte CHANGED = 1;
        private static final byte ADDED = 2;

        /**
         * @param first    the watch's first sample: the client starts from nothing, and drops what an
         *                 earlier watch of the same object still had on the way
         * @param identity set with {@code ADDED}
         * @param value    null with {@code REMOVED}
         */
        private record Change(int id, byte type, Object identity, Object value) {
        }

        private record Changes(boolean first, List<Change> changes) {
        }

        private final BiConsumer<I, FriendlyByteBuf> encodeIdentity;
        private final Function<FriendlyByteBuf, I> decodeIdentity;
        private final BiConsumer<W, FriendlyByteBuf> encodeValue;
        private final Function<FriendlyByteBuf, W> decodeValue;
        private final BiFunction<ServerPlayer, K, Map<I, W>> view;

        Collection(BiConsumer<FriendlyByteBuf, K> writeKey, Function<FriendlyByteBuf, K> readKey, int interval,
                   BiConsumer<I, FriendlyByteBuf> encodeIdentity, Function<FriendlyByteBuf, I> decodeIdentity,
                   BiConsumer<W, FriendlyByteBuf> encodeValue, Function<FriendlyByteBuf, W> decodeValue,
                   BiFunction<ServerPlayer, K, Map<I, W>> view) {
            super(writeKey, readKey, interval);
            this.encodeIdentity = encodeIdentity;
            this.decodeIdentity = decodeIdentity;
            this.encodeValue = encodeValue;
            this.decodeValue = decodeValue;
            this.view = view;
        }

        @Override
        Sampler sampler(K key) {
            return new Sampler() {
                /** Every identity this watch has named; an id stays with its identity even while absent. */
                private final Map<I, Integer> ids = new HashMap<>();
                /** What the client holds, per id. */
                private Map<Integer, W> sent = new HashMap<>();
                private boolean first = true;

                @Override
                Object sample(ServerPlayer player) {
                    Map<I, W> now = view.apply(player, key);
                    if (now == null) {
                        return ENDED;
                    }
                    List<Change> changes = new ArrayList<>();
                    Map<Integer, W> next = new HashMap<>(now.size() * 2);
                    for (Map.Entry<I, W> e : now.entrySet()) {
                        int id = ids.computeIfAbsent(e.getKey(), i -> ids.size());
                        W value = e.getValue();
                        next.put(id, value);
                        W before = sent.remove(id);
                        if (before == null) {
                            changes.add(new Change(id, ADDED, e.getKey(), value));
                        } else if (!before.equals(value)) {
                            changes.add(new Change(id, CHANGED, null, value));
                        }
                    }
                    // What is left was sent and is gone.
                    for (Integer id : sent.keySet()) {
                        changes.add(new Change(id, REMOVED, null, null));
                    }
                    sent = next;
                    if (changes.isEmpty() && !first) {
                        return null;
                    }
                    Changes out = new Changes(first, changes);
                    first = false;
                    return out;
                }
            };
        }

        @SuppressWarnings("unchecked")
        @Override
        void writeChange(Object change, FriendlyByteBuf buf) {
            Changes cs = (Changes) change;
            buf.writeBoolean(cs.first());
            buf.writeVarInt(cs.changes().size());
            for (Change c : cs.changes()) {
                buf.writeVarInt(c.id());
                buf.writeByte(c.type());
                if (c.type() == ADDED) {
                    encodeIdentity.accept((I) c.identity(), buf);
                }
                if (c.type() != REMOVED) {
                    encodeValue.accept((W) c.value(), buf);
                }
            }
        }

        @Override
        Object readChange(FriendlyByteBuf buf) {
            boolean first = buf.readBoolean();
            int n = buf.readVarInt();
            List<Change> changes = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                int id = buf.readVarInt();
                byte type = buf.readByte();
                Object identity = type == ADDED ? decodeIdentity.apply(buf) : null;
                Object value = type == REMOVED ? null : decodeValue.apply(buf);
                changes.add(new Change(id, type, identity, value));
            }
            return new Changes(first, changes);
        }

        @Override
        Copy<List<Entry<I, W>>> copy() {
            return new Copy<>() {
                private final Map<Integer, Entry<I, W>> entries = new HashMap<>();
                private List<Entry<I, W>> value;

                @SuppressWarnings("unchecked")
                @Override
                public List<Entry<I, W>> apply(Object change) {
                    Changes cs = (Changes) change;
                    if (cs.first()) {
                        entries.clear();
                    } else if (value == null) {
                        // From a watch that ended before this one began.
                        return null;
                    }
                    for (Change c : cs.changes()) {
                        switch (c.type()) {
                            case REMOVED -> entries.remove(c.id());
                            case ADDED -> entries.put(c.id(), new Entry<>(c.id(), (I) c.identity(), (W) c.value()));
                            default -> {
                                Entry<I, W> e = entries.get(c.id());
                                entries.put(c.id(), new Entry<>(c.id(), e.identity(), (W) c.value()));
                            }
                        }
                    }
                    value = List.copyOf(entries.values());
                    return value;
                }
            };
        }
    }
}
