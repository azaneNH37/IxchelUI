package com.azane.ixchel.sample.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Intent: set the machine's settings, all at once as the config page holds them. */
public record SetConfigPacket(BlockPos pos, MachineConfig config) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        config.encode(buf);
    }

    public static SetConfigPacket decode(FriendlyByteBuf buf) {
        return new SetConfigPacket(buf.readBlockPos(), MachineConfig.decode(buf));
    }

    /** Dropped unless the player can reach the machine. */
    public void handle(ServerPlayer player) {
        MachineBlockEntity be = MachineBlockEntity.reachable(player, pos);
        if (be != null) {
            be.setConfig(config);
        }
    }
}
