package com.azane.ixchel.sample.network;

import net.minecraft.core.BlockPos;
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
 * object has an identity (a {@link Kind} and a block position); a client declares interest by
 * {@linkplain #watch watching} it; at the end of every server tick the server takes the snapshot that
 * player may see and sends it when it differs from the last one sent; when the player may no longer
 * see it (gone, out of reach, logged out) the watch ends and the client replica reads {@code null}.
 *
 * <p>UIs only read replicas; they change the object through intent packets, which the server
 * validates, and see the result when it replicates back. A new kind of object declares its snapshot
 * record, the record's codec and its visibility rule, never packet plumbing.
 */
public final class Replication {
    /**
     * A kind of replicated object.
     *
     * @param view the snapshot {@code player} may see of the object at the position, or {@code null}
     *             when there is none or the player may not see it; snapshots compare with {@code equals}
     */
    public record Kind<V>(int id, BiConsumer<V, FriendlyByteBuf> encode, Function<FriendlyByteBuf, V> decode,
                          BiFunction<ServerPlayer, BlockPos, V> view) {
    }

    private record Key(int kind, BlockPos pos) {
    }

    private static final List<Kind<?>> KINDS = new ArrayList<>();
    /** Server: per player, every watched object and the snapshot last sent ({@code null} before the first). */
    private static final Map<UUID, Map<Key, Object>> WATCHES = new HashMap<>();
    /**
     * Client: the open replicas of each watched object. A screen's model is built before the screen
     * it replaces closes, so the same object can have two for a moment; they share one server watch.
     */
    private static final Map<Key, List<Replica<?>>> REPLICAS = new HashMap<>();

    private Replication() {
    }

    /** Declares a kind; call from {@link Network}'s static initialization so ids match on both sides. */
    static <V> Kind<V> kind(BiConsumer<V, FriendlyByteBuf> encode, Function<FriendlyByteBuf, V> decode,
                            BiFunction<ServerPlayer, BlockPos, V> view) {
        Kind<V> kind = new Kind<>(KINDS.size(), encode, decode, view);
        KINDS.add(kind);
        return kind;
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
     * Client: starts watching the object; the replica reads {@code null} until the first snapshot
     * arrives and after the server ends the watch. Close it to stop.
     */
    public static <V> Replica<V> watch(Kind<V> kind, BlockPos pos) {
        Key key = new Key(kind.id(), pos.immutable());
        List<Replica<?>> open = REPLICAS.computeIfAbsent(key, k -> new ArrayList<>());
        Replica<V> replica = new Replica<>();
        replica.onClose(() -> {
            // Absent when the server already ended the watch.
            if (REPLICAS.get(key) == open && open.remove(replica) && open.isEmpty()) {
                REPLICAS.remove(key);
                Network.sendToServer(new Watch(key.kind(), key.pos(), false));
            }
        });
        if (open.isEmpty()) {
            Network.sendToServer(new Watch(key.kind(), key.pos(), true));
        } else {
            replica.set(open.get(0).get());
        }
        open.add(replica);
        return replica;
    }

    private static void tick(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Map<Key, Object>>> players = WATCHES.entrySet().iterator(); players.hasNext(); ) {
            Map.Entry<UUID, Map<Key, Object>> entry = players.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                players.remove();
                continue;
            }
            for (Iterator<Map.Entry<Key, Object>> watches = entry.getValue().entrySet().iterator(); watches.hasNext(); ) {
                Map.Entry<Key, Object> w = watches.next();
                Object now = KINDS.get(w.getKey().kind()).view().apply(player, w.getKey().pos());
                if (now == null) {
                    watches.remove();
                    Network.sendTo(player, new Update(w.getKey().kind(), w.getKey().pos(), null));
                } else if (!now.equals(w.getValue())) {
                    w.setValue(now);
                    Network.sendTo(player, new Update(w.getKey().kind(), w.getKey().pos(), now));
                }
            }
        }
    }

    /** Client to server: start or stop watching an object. */
    record Watch(int kind, BlockPos pos, boolean on) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind).writeBlockPos(pos).writeBoolean(on);
        }

        static Watch decode(FriendlyByteBuf buf) {
            return new Watch(buf.readVarInt(), buf.readBlockPos(), buf.readBoolean());
        }

        void handle(ServerPlayer player) {
            if (kind < 0 || kind >= KINDS.size()) {
                return;
            }
            Map<Key, Object> watches = WATCHES.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
            if (on) {
                // Null is never equal to a snapshot: the next tick sends the current one.
                watches.put(new Key(kind, pos), null);
            } else {
                watches.remove(new Key(kind, pos));
            }
        }
    }

    /** Server to client: a new snapshot of a watched object, or {@code null} when the watch ended. */
    record Update(int kind, BlockPos pos, Object value) {
        @SuppressWarnings("unchecked")
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind).writeBlockPos(pos).writeBoolean(value != null);
            if (value != null) {
                ((Kind<Object>) KINDS.get(kind)).encode().accept(value, buf);
            }
        }

        static Update decode(FriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            return new Update(kind, pos, buf.readBoolean() ? KINDS.get(kind).decode().apply(buf) : null);
        }

        void handleOnClient() {
            Key key = new Key(kind, pos);
            List<Replica<?>> open = value == null ? REPLICAS.remove(key) : REPLICAS.get(key);
            if (open != null) {
                open.forEach(r -> r.set(value));
            }
        }
    }
}
