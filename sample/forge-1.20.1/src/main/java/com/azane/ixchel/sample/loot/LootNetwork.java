package com.azane.ixchel.sample.loot;

import com.azane.ixchel.sample.IxchelSampleMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * The radar's pick-up request: the client names a drop, the server picks it up for the player only
 * if it is an item within {@link #PICK_RANGE}; the result reaches the client through the entity
 * removal and the inventory sync, so there is no reply.
 */
public final class LootNetwork {
    /** Blocks within which a drop can be picked up from the list. */
    static final double PICK_RANGE = 8;
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(IxchelSampleMod.MOD_ID, "loot"), () -> VERSION, VERSION::equals, VERSION::equals);

    record PickUp(int entityId) {
        void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
        }

        static PickUp read(FriendlyByteBuf buf) {
            return new PickUp(buf.readVarInt());
        }
    }

    private LootNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(PickUp.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PickUp::write).decoder(PickUp::read).consumerMainThread(LootNetwork::onServer).add();
    }

    static void pickUp(int entityId) {
        CHANNEL.sendToServer(new PickUp(entityId));
    }

    private static void onServer(PickUp m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) {
            return;
        }
        Entity e = p.serverLevel().getEntity(m.entityId());
        if (e instanceof ItemEntity item && item.isAlive() && item.distanceTo(p) <= PICK_RANGE) {
            item.setNoPickUpDelay();
            item.playerTouch(p);
        }
    }
}
