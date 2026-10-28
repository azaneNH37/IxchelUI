package com.azane.ixchel.sample.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Intent: set the machine's redstone mode. */
public record SetRedstonePacket(BlockPos pos, MachineState.Redstone mode) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos).writeEnum(mode);
    }

    public static SetRedstonePacket decode(FriendlyByteBuf buf) {
        return new SetRedstonePacket(buf.readBlockPos(), buf.readEnum(MachineState.Redstone.class));
    }

    /** Dropped unless the player can reach the machine; the result replicates back. */
    public void handle(ServerPlayer player) {
        MachineBlockEntity be = MachineBlockEntity.reachable(player, pos);
        if (be != null) {
            be.setRedstone(mode);
        }
    }
}
