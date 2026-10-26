package com.azane.ixchel.testmod.refit;

import com.azane.ixchel.testmod.IxchelTestMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Refit commands: the client asks, the server checks against the real inventory and applies, the
 * item change reaches the client through normal inventory sync, and a reply settles the request.
 * Replies are held back {@value #REPLY_DELAY_TICKS} ticks so a pending state is visible.
 */
public final class RefitNetwork {
    private static final int REPLY_DELAY_TICKS = 6;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IxchelTestMod.MOD_ID, "refit"), () -> VERSION, VERSION::equals, VERSION::equals);

    /** {@code type} is an attachment type or {@code "laser"}; an empty {@code option} removes. */
    record Request(int id, int slot, String type, String option) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id).writeVarInt(slot).writeUtf(type).writeUtf(option);
        }

        static Request read(FriendlyByteBuf buf) {
            return new Request(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }
    }

    /** {@code error} is empty on success. */
    record Reply(int id, String error) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id).writeUtf(error);
        }

        static Reply read(FriendlyByteBuf buf) {
            return new Reply(buf.readVarInt(), buf.readUtf());
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

    public static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Request::write).decoder(Request::read).consumerMainThread(RefitNetwork::onServer).add();
        CHANNEL.messageBuilder(Reply.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Reply::write).decoder(Reply::read).consumerMainThread(RefitNetwork::onClient).add();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END) {
                return;
            }
            for (Iterator<Delayed> it = delayed.iterator(); it.hasNext(); ) {
                Delayed d = it.next();
                if (--d.ticks[0] <= 0) {
                    it.remove();
                    CHANNEL.send(PacketDistributor.PLAYER.with(d::player), d.reply);
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
        CHANNEL.sendToServer(new Request(id, slot, type, option));
        return f;
    }

    private static void onClient(Reply r, Supplier<NetworkEvent.Context> ctx) {
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

    private static void onServer(Request r, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) {
            return;
        }
        String error = apply(p.getInventory(), r);
        delayed.add(new Delayed(p, new Reply(r.id, error), new int[]{REPLY_DELAY_TICKS}));
    }

    /** Returns an error message, or {@code ""} once applied. */
    private static String apply(Inventory inv, Request r) {
        if (r.slot < 0 || r.slot >= inv.getContainerSize() || !Attachments.isGun(inv.getItem(r.slot))) {
            return "The crossbow is no longer in that slot";
        }
        ItemStack gun = inv.getItem(r.slot);
        if (r.type.equals("laser")) {
            if (Attachments.option(Attachments.LASERS, r.option) == null) {
                return "Unknown laser color '" + r.option + "'";
            }
            Attachments.setLaser(gun, r.option);
            return "";
        }
        Attachments.Type type = Attachments.type(r.type);
        if (type == null) {
            return "Unknown attachment type '" + r.type + "'";
        }
        Attachments.Option current = Attachments.option(type.options(), Attachments.installed(gun, type.id()));
        if (r.option.isEmpty()) {
            if (current == null) {
                return "No " + type.name().getString().toLowerCase(java.util.Locale.ROOT) + " is installed";
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
