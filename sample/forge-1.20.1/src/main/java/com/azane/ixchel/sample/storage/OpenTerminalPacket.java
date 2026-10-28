package com.azane.ixchel.sample.storage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * The sample index's way into the terminal without holding one: the server opens it on the storage
 * around the player, valid while the player stays within reach. A development convenience, as the
 * index itself.
 */
public record OpenTerminalPacket() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static OpenTerminalPacket decode(FriendlyByteBuf buf) {
        return new OpenTerminalPacket();
    }

    public void handle(ServerPlayer player) {
        TerminalItem.open(player, null);
    }
}
