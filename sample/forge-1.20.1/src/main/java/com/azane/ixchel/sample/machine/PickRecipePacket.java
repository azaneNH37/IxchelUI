package com.azane.ixchel.sample.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** Intent: use the stonecutting recipe at {@code index} for the machine's current input ({@code -1}: none). */
public record PickRecipePacket(BlockPos pos, int index) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos).writeVarInt(index);
    }

    public static PickRecipePacket decode(FriendlyByteBuf buf) {
        return new PickRecipePacket(buf.readBlockPos(), buf.readVarInt());
    }

    /** Dropped unless the player can reach the machine; the result replicates back. */
    public void handle(ServerPlayer player) {
        MachineBlockEntity be = MachineBlockEntity.reachable(player, pos);
        if (be != null) {
            be.pickRecipe(index);
        }
    }
}
