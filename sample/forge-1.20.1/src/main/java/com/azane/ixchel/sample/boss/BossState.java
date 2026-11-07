package com.azane.ixchel.sample.boss;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * What a player sees of the boss nearest to them: a mob tagged {@value #TAG} (for example
 * {@code /summon iron_golem ~ ~ ~ {Tags:["ixchel_boss"],CustomName:'"Colossus"'}}) within
 * {@value #RANGE} blocks. Health, absorption (the shield) and active effects are the mob's own;
 * {@link #NONE} when there is no such mob.
 */
public record BossState(int entity, String name, float health, float maxHealth, float shield, List<Buff> buffs) {
    public static final String TAG = "ixchel_boss";
    static final double RANGE = 64;
    public static final BossState NONE = new BossState(-1, "", 0, 1, 0, List.of());

    /** An active effect: its registry id, level from 0, ticks left, and whether it helps the boss. */
    public record Buff(String effect, int amplifier, int ticks, boolean beneficial) {
    }

    boolean present() {
        return entity >= 0;
    }

    /** The replication view; the key is unused. */
    public static BossState view(ServerPlayer player, Integer key) {
        Mob boss = null;
        double best = RANGE * RANGE;
        for (Mob m : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE),
                m -> m.isAlive() && m.getTags().contains(TAG))) {
            double d = m.distanceToSqr(player);
            if (d <= best) {
                best = d;
                boss = m;
            }
        }
        if (boss == null) {
            return NONE;
        }
        List<Buff> buffs = new ArrayList<>();
        for (MobEffectInstance e : boss.getActiveEffects()) {
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(e.getEffect());
            buffs.add(new Buff(String.valueOf(id), e.getAmplifier(), e.getDuration(), e.getEffect().isBeneficial()));
        }
        buffs.sort((a, b) -> a.effect().compareTo(b.effect()));
        return new BossState(boss.getId(), boss.getDisplayName().getString(), boss.getHealth(), boss.getMaxHealth(),
                boss.getAbsorptionAmount(), List.copyOf(buffs));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entity);
        buf.writeUtf(name);
        buf.writeFloat(health);
        buf.writeFloat(maxHealth);
        buf.writeFloat(shield);
        buf.writeVarInt(buffs.size());
        for (Buff b : buffs) {
            buf.writeUtf(b.effect());
            buf.writeVarInt(b.amplifier());
            buf.writeVarInt(b.ticks());
            buf.writeBoolean(b.beneficial());
        }
    }

    public static BossState decode(FriendlyByteBuf buf) {
        int entity = buf.readInt();
        String name = buf.readUtf();
        float health = buf.readFloat();
        float max = buf.readFloat();
        float shield = buf.readFloat();
        int n = buf.readVarInt();
        List<Buff> buffs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            buffs.add(new Buff(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        return new BossState(entity, name, health, max, shield, List.copyOf(buffs));
    }
}
