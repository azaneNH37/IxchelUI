package com.azane.ixchel.sample.storage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: put the carried stack, or one of it, into the open terminal's storage. */
public record StorePacket(int containerId, boolean one) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeBoolean(one);
    }

    public static StorePacket decode(FriendlyByteBuf buf) {
        return new StorePacket(buf.readVarInt(), buf.readBoolean());
    }

    public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof TerminalMenu menu && menu.containerId == containerId) {
            menu.store(one);
        }
    }
}
