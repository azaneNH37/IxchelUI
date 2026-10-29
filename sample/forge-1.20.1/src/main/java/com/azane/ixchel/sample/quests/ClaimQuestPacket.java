package com.azane.ixchel.sample.quests;

import com.azane.ixchel.sample.network.Network;
import com.azane.ixchel.sample.network.Replication;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Claims a quest: honored only when it is not claimed yet, every prerequisite is, and the player holds
 * the items it asks for (they are not taken). The reward goes to the inventory, or drops at the player
 * when it is full; the claim reaches the client through {@code Network.QUESTS}.
 */
public record ClaimQuestPacket(String id) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(id);
    }

    public static ClaimQuestPacket decode(FriendlyByteBuf buf) {
        return new ClaimQuestPacket(buf.readUtf());
    }

    public void handle(ServerPlayer player) {
        Quests.Quest q = Quests.byId(id);
        Claimed claimed = Claimed.view(player, 0);
        if (q == null || claimed.has(id) || !q.deps().stream().allMatch(claimed::has)
                || player.getInventory().countItem(q.item()) < q.count()) {
            return;
        }
        Claimed.add(player, id);
        ItemStack reward = new ItemStack(q.reward(), q.rewardCount());
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        Replication.refresh(player, Network.QUESTS, 0);
    }
}
