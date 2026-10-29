package com.azane.ixchel.sample.network;

import com.azane.ixchel.sample.IxchelSampleMod;
import com.azane.ixchel.sample.loot.PickUpDropPacket;
import com.azane.ixchel.sample.machine.MachineBlockEntity;
import com.azane.ixchel.sample.machine.MachineState;
import com.azane.ixchel.sample.machine.OpenMachinePacket;
import com.azane.ixchel.sample.machine.PickRecipePacket;
import com.azane.ixchel.sample.machine.SetRedstonePacket;
import com.azane.ixchel.sample.machine.SetSidePacket;
import com.azane.ixchel.sample.storage.OpenTerminalPacket;
import com.azane.ixchel.sample.storage.StorePacket;
import com.azane.ixchel.sample.storage.StoredItem;
import com.azane.ixchel.sample.storage.TakePacket;
import com.azane.ixchel.sample.storage.TerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The sample's one channel, {@code ixchelsample:main}, and the replicated kinds; like {@code registry},
 * it lists what the features declare. State reaches clients only through {@link Replication}. Clients
 * act through intent packets, records in their feature's package with {@code encode}, {@code decode}
 * and {@code handle}, handled on the server thread with the sender's player and dropped when there is
 * no sender.
 */
public final class Network {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IxchelSampleMod.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static int nextId;

    /** A powered stonecutter, for players within reach; every tick. */
    public static final Replication.Kind<BlockPos, MachineState> MACHINE = Replication.snapshot(
            FriendlyByteBuf::writeBlockPos, FriendlyByteBuf::readBlockPos, 1,
            MachineState::encode, MachineState::decode, MachineBlockEntity::view);

    /**
     * The storage an open terminal shows, by the menu's container id, for the player with that menu
     * open: how many of each item; every five ticks, as a scan reads every container in range.
     */
    public static final Replication.Kind<Integer, List<Replication.Entry<StoredItem, Long>>> STORAGE =
            Replication.collection(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt, 5,
                    StoredItem::encode, StoredItem::decode, (count, buf) -> buf.writeVarLong(count),
                    FriendlyByteBuf::readVarLong, TerminalMenu::view);

    private Network() {
    }

    /** Registers every packet; call once, from the mod constructor, on both sides. */
    public static void register() {
        toServer(Replication.Watch.class, Replication.Watch::encode, Replication.Watch::decode, Replication.Watch::handle);
        toClient(Replication.Update.class, Replication.Update::encode, Replication.Update::decode,
                Replication.Update::handleOnClient);
        Replication.init();

        toServer(PickUpDropPacket.class, PickUpDropPacket::encode, PickUpDropPacket::decode, PickUpDropPacket::handle);
        toServer(OpenMachinePacket.class, OpenMachinePacket::encode, OpenMachinePacket::decode, OpenMachinePacket::handle);
        toServer(PickRecipePacket.class, PickRecipePacket::encode, PickRecipePacket::decode, PickRecipePacket::handle);
        toServer(SetRedstonePacket.class, SetRedstonePacket::encode, SetRedstonePacket::decode, SetRedstonePacket::handle);
        toServer(SetSidePacket.class, SetSidePacket::encode, SetSidePacket::decode, SetSidePacket::handle);
        toServer(OpenTerminalPacket.class, OpenTerminalPacket::encode, OpenTerminalPacket::decode, OpenTerminalPacket::handle);
        toServer(TakePacket.class, TakePacket::encode, TakePacket::decode, TakePacket::handle);
        toServer(StorePacket.class, StorePacket::encode, StorePacket::decode, StorePacket::handle);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    static void sendTo(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Handled on the client's main thread; the handler may use client classes, which only load there. */
    private static <T> void toClient(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode,
                                     Function<FriendlyByteBuf, T> decode, Consumer<T> handle) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(encode).decoder(decode)
                .consumerMainThread((packet, ctx) -> handle.accept(packet))
                .add();
    }

    private static <T> void toServer(Class<T> type, BiConsumer<T, FriendlyByteBuf> encode,
                                     Function<FriendlyByteBuf, T> decode, BiConsumer<T, ServerPlayer> handle) {
        CHANNEL.messageBuilder(type, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(encode).decoder(decode)
                .consumerMainThread((packet, ctx) -> {
                    ServerPlayer sender = ctx.get().getSender();
                    if (sender != null) {
                        handle.accept(packet, sender);
                    }
                })
                .add();
    }
}
