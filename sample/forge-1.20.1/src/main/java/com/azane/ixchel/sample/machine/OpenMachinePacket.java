package com.azane.ixchel.sample.machine;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * The sample index's way into the machine panel: the server places a powered stonecutter next to
 * the player when there is none and opens it. A development convenience, as the index itself.
 */
public record OpenMachinePacket() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static OpenMachinePacket decode(FriendlyByteBuf buf) {
        return new OpenMachinePacket();
    }

    public void handle(ServerPlayer player) {
        MachineScreen.placeAndOpen(player);
    }
}
