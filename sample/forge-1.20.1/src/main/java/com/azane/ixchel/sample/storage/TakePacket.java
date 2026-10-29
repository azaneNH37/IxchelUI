package com.azane.ixchel.sample.storage;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Client to server: take from the open terminal's storage. Ignored unless that terminal is still open. */
public record TakePacket(int containerId, StoredItem item, Mode mode) {
    enum Mode {
        /** A full stack, or what there is, onto the cursor. */
        STACK,
        /** Half a stack, rounded up, onto the cursor. */
        HALF,
        /** A full stack straight into the inventory; what does not fit goes back. */
        TO_INVENTORY
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        item.encode(buf);
        buf.writeEnum(mode);
    }

    public static TakePacket decode(FriendlyByteBuf buf) {
        return new TakePacket(buf.readVarInt(), StoredItem.decode(buf), buf.readEnum(Mode.class));
    }

    public void handle(ServerPlayer player) {
        if (player.containerMenu instanceof TerminalMenu menu && menu.containerId == containerId) {
            menu.take(item, mode);
        }
    }
}
