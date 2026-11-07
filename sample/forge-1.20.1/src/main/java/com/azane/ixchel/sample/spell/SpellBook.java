package com.azane.ixchel.sample.spell;

import net.minecraft.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The spells on the wheel and the caster's state, on the client only: mana, which refills over time,
 * and when each spell is ready again. Casting spends both and plays a sound; nothing happens in the
 * world. Time is {@link Util#getMillis()}, which a recording steps frame by frame.
 */
final class SpellBook {
    static final int MANA_MAX = 100;
    /** Mana refilled per second. */
    static final float REGEN = 3;

    record Spell(String id, Item icon, int cost, long cooldownMs) {
    }

    static final List<Spell> SPELLS = List.of(
            new Spell("fireball", Items.FIRE_CHARGE, 20, 4000),
            new Spell("frost", Items.SNOWBALL, 15, 3000),
            new Spell("blink", Items.ENDER_PEARL, 25, 6000),
            new Spell("mend", Items.GOLDEN_APPLE, 40, 8000),
            new Spell("ward", Items.SHIELD, 20, 5000),
            new Spell("haste", Items.SUGAR, 10, 2000),
            new Spell("light", Items.GLOWSTONE_DUST, 5, 1000),
            new Spell("storm", Items.TRIDENT, 60, 12000));

    private final long[] readyAt = new long[SPELLS.size()];
    private float manaAt = MANA_MAX;
    private long manaTime = Util.getMillis();

    float mana(long now) {
        return Math.min(MANA_MAX, manaAt + (now - manaTime) * REGEN / 1000f);
    }

    /** Milliseconds until spell {@code i} is ready, 0 when it is. */
    long cooldownLeft(int i, long now) {
        return Math.max(0, readyAt[i] - now);
    }

    boolean canCast(int i, long now) {
        return cooldownLeft(i, now) == 0 && mana(now) >= SPELLS.get(i).cost();
    }

    /** Casts spell {@code i} when it is ready and affordable; whether it did. */
    boolean cast(int i, long now) {
        if (!canCast(i, now)) {
            return false;
        }
        manaAt = mana(now) - SPELLS.get(i).cost();
        manaTime = now;
        readyAt[i] = now + SPELLS.get(i).cooldownMs();
        return true;
    }
}
