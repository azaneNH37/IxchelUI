package com.azane.ixchel.sample.refit;

import com.azane.ixchel.sample.IxchelSampleMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Refit commands: the client asks, the server checks against the real inventory and applies, the
 * item change reaches the client through normal inventory sync, and a reply settles the request.
 * Replies are held back {@value #REPLY_DELAY_TICKS} ticks so a pending state is visible.
 */
public final class RefitNetwork {
    private static final int REPLY_DELAY_TICKS = 6;
    private static final String VERSION = "1";

    /** {@code attachment} is an attachment type or {@code "laser"}; an empty {@code option} removes. */
    record Request(int id, int slot, String attachment, String option) implements CustomPacketPayload {
        static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IxchelSampleMod.MOD_ID, "refit"));
        static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::id, ByteBufCodecs.VAR_INT, Request::slot,
                ByteBufCodecs.STRING_UTF8, Request::attachment, ByteBufCodecs.STRING_UTF8, Request::option,
                Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /** {@code error} is empty on success. */
    record Reply(int id, String error) implements CustomPacketPayload {
        static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(IxchelSampleMod.MOD_ID, "refit_reply"));
        static final StreamCodec<ByteBuf, Reply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reply::id, ByteBufCodecs.STRING_UTF8, Reply::error, Reply::new);

        @Override
        public Type<Reply> type() {
            return TYPE;
        }
    }

    /** A request the server turned down; the message is for the player. */
    public static final class RefitRejected extends RuntimeException {
        RefitRejected(String message) {
            super(message, null, false, false);
        }
    }

    private record Delayed(ServerPlayer player, Reply reply, int[] ticks) {
    }

    private static final Map<Integer, CompletableFuture<Void>> waiting = new ConcurrentHashMap<>();
    private static final AtomicInteger ids = new AtomicInteger();
    private static final List<Delayed> delayed = new ArrayList<>();

    private RefitNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar registrar = e.registrar(VERSION);
            registrar.playToServer(Request.TYPE, Request.CODEC, RefitNetwork::onServer);
            registrar.playToClient(Reply.TYPE, Reply.CODEC, RefitNetwork::onClient);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            for (Iterator<Delayed> it = delayed.iterator(); it.hasNext(); ) {
                Delayed d = it.next();
                if (--d.ticks[0] <= 0) {
                    it.remove();
                    PacketDistributor.sendToPlayer(d.player, d.reply);
                }
            }
        });
    }

    /** Installs {@code option} (or removes, if empty) on the gun in inventory slot {@code slot}. */
    public static CompletableFuture<Void> install(int slot, String type, String option) {
        return request(slot, type, option);
    }

    public static CompletableFuture<Void> laser(int slot, String color) {
        return request(slot, "laser", color);
    }

    private static CompletableFuture<Void> request(int slot, String type, String option) {
        int id = ids.incrementAndGet();
        CompletableFuture<Void> f = new CompletableFuture<>();
        waiting.put(id, f);
        PacketDistributor.sendToServer(new Request(id, slot, type, option));
        return f;
    }

    private static void onClient(Reply r, IPayloadContext ctx) {
        CompletableFuture<Void> f = waiting.remove(r.id);
        if (f == null) {
            return;
        }
        if (r.error.isEmpty()) {
            f.complete(null);
        } else {
            f.completeExceptionally(new RefitRejected(r.error));
        }
    }

    private static void onServer(Request r, IPayloadContext ctx) {
        ServerPlayer p = (ServerPlayer) ctx.player();
        String error = apply(p.getInventory(), r);
        delayed.add(new Delayed(p, new Reply(r.id, error), new int[]{REPLY_DELAY_TICKS}));
    }

    /** Returns an error message, or {@code ""} once applied. */
    private static String apply(Inventory inv, Request r) {
        if (r.slot < 0 || r.slot >= inv.getContainerSize() || !Attachments.isGun(inv.getItem(r.slot))) {
            return "The crossbow is no longer in that slot";
        }
        ItemStack gun = inv.getItem(r.slot);
        if (r.attachment.equals("laser")) {
            if (Attachments.option(Attachments.LASERS, r.option) == null) {
                return "Unknown laser color '" + r.option + "'";
            }
            Attachments.setLaser(gun, r.option);
            return "";
        }
        Attachments.Type type = Attachments.type(r.attachment);
        if (type == null) {
            return "Unknown attachment type '" + r.attachment + "'";
        }
        Attachments.Option current = Attachments.option(type.options(), Attachments.installed(gun, type.id()));
        if (r.option.isEmpty()) {
            if (current == null) {
                return "No " + type.name().getString().toLowerCase(Locale.ROOT) + " is installed";
            }
            inv.placeItemBackInInventory(new ItemStack(current.item()));
            Attachments.setInstalled(gun, type.id(), "");
            return "";
        }
        Attachments.Option wanted = Attachments.option(type.options(), r.option);
        if (wanted == null) {
            return "Unknown " + type.id() + " '" + r.option + "'";
        }
        if (wanted == current) {
            return wanted.name().getString() + " is already installed";
        }
        int from = -1;
        for (int i = 0; i < inv.getContainerSize() && from < 0; i++) {
            if (inv.getItem(i).is(wanted.item())) {
                from = i;
            }
        }
        if (from < 0) {
            return "You have no " + wanted.name().getString();
        }
        inv.getItem(from).shrink(1);
        if (current != null) {
            inv.placeItemBackInInventory(new ItemStack(current.item()));
        }
        Attachments.setInstalled(gun, type.id(), wanted.id());
        return "";
    }
}
