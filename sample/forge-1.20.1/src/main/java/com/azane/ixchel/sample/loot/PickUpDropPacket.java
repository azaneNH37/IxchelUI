package com.azane.ixchel.sample.loot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * The loot radar's pick-up request: the client names a drop, the server picks it up for the player
 * only if it is an item within {@link #PICK_RANGE}; the result reaches the client through the
 * entity removal and the inventory sync, so there is no reply.
 */
public record PickUpDropPacket(int entityId) {
    /** Blocks within which a drop can be picked up from the list. */
    public static final double PICK_RANGE = 8;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static PickUpDropPacket decode(FriendlyByteBuf buf) {
        return new PickUpDropPacket(buf.readVarInt());
    }

    public void handle(ServerPlayer player) {
        Entity e = player.serverLevel().getEntity(entityId);
        if (e instanceof ItemEntity item && item.isAlive() && item.distanceTo(player) <= PICK_RANGE) {
            item.setNoPickUpDelay();
            item.playerTouch(player);
        }
    }
}
