package com.azane.ixchel.sample.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Server to client: what changed in an open terminal's storage. */
public record StorageSyncPacket(int containerId, List<Change> changes) {
    /**
     * @param prototype one of the stack, the first time the id is sent; null afterwards
     * @param count     how many there are now; 0 means none are left
     */
    record Change(int id, ItemStack prototype, long count) {
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(changes.size());
        for (Change c : changes) {
            buf.writeVarInt(c.id());
            buf.writeVarLong(c.count());
            buf.writeBoolean(c.prototype() != null);
            if (c.prototype() != null) {
                buf.writeItem(c.prototype());
            }
        }
    }

    public static StorageSyncPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int n = buf.readVarInt();
        List<Change> changes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = buf.readVarInt();
            long count = buf.readVarLong();
            changes.add(new Change(id, buf.readBoolean() ? buf.readItem() : null, count));
        }
        return new StorageSyncPacket(containerId, changes);
    }

    /** Dropped when the terminal has closed meanwhile. */
    public void handleOnClient() {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof TerminalMenu menu && menu.containerId == containerId) {
            menu.apply(changes);
        }
    }
}
