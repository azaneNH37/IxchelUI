package com.azane.ixchel.sample.quests;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The quests a player claimed, kept in the player's persistent data on the server and replicated
 * whole ({@code Network.QUESTS}).
 */
public record Claimed(Set<String> ids) {
    public static final Claimed NONE = new Claimed(Set.of());
    private static final String TAG = "ixchelsample_quests";

    public boolean has(String id) {
        return ids.contains(id);
    }

    /** The replication view: every player sees their own; the key is unused. */
    public static Claimed view(ServerPlayer player, Integer key) {
        Set<String> out = new HashSet<>();
        ListTag list = player.getPersistentData().getList(TAG, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            out.add(list.getString(i));
        }
        return new Claimed(Set.copyOf(out));
    }

    static void add(ServerPlayer player, String id) {
        CompoundTag data = player.getPersistentData();
        ListTag list = data.getList(TAG, Tag.TAG_STRING);
        list.add(StringTag.valueOf(id));
        data.put(TAG, list);
    }

    /** Forgets every claim, for a check that starts from nothing. */
    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(TAG);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(List.copyOf(ids), FriendlyByteBuf::writeUtf);
    }

    public static Claimed decode(FriendlyByteBuf buf) {
        return new Claimed(Set.copyOf(buf.readList(FriendlyByteBuf::readUtf)));
    }
}
