package com.azane.ixchel.sample.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Intent: set the mode of one side of the machine. */
public record SetSidePacket(BlockPos pos, Direction side, MachineState.SideMode mode) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos).writeEnum(side).writeEnum(mode);
    }

    public static SetSidePacket decode(FriendlyByteBuf buf) {
        return new SetSidePacket(buf.readBlockPos(), buf.readEnum(Direction.class), buf.readEnum(MachineState.SideMode.class));
    }

    /** Dropped unless the player can reach the machine; the result replicates back. */
    public void handle(ServerPlayer player) {
        MachineBlockEntity be = MachineBlockEntity.reachable(player, pos);
        if (be != null) {
            be.setSide(side, mode);
        }
    }
}
