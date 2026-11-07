package com.azane.ixchel.sample.boss;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Source;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The boss bar. Three phases split the health in thirds; the last renames the boss enraged. An
 * effect's sweep runs from the time it had when first seen (or last extended) to none.
 */
@UiView("ixchel_sample:ui/boss/bar.html")
final class BossModel extends ViewModel {
    static final int PHASES = 3;
    private static final String[] NUMERALS = {"I", "II", "III"};

    /**
     * An effect's icon. {@code left}: the part of its time left, 1 to 0; {@code level} the roman
     * level from II, empty at I.
     */
    record Buff(String effect, String icon, Component name, String level, float left, String time, boolean beneficial) {
    }

    /** The phase, 1 to {@link #PHASES}, in a one-element list: a new phase is a new row, which restarts its animation. */
    record Phase(int number, Component label, Component title) {
    }

    /** Ticks each effect had at most, by id, to sweep from; dropped with the effect. */
    private final Map<String, Integer> totals = new HashMap<>();

    final Cell<BossState> boss = source(Source.polled(BossHud::state));
    final Derived<Boolean> present = derive(boss, BossState::present);
    final Derived<List<Phase>> phase = derive(boss, BossModel::phase);
    final Derived<Boolean> enraged = derive(boss, b -> b.present() && b.health() <= b.maxHealth() / PHASES);
    final Derived<String> healthText = derive(boss, b -> String.format(Locale.ROOT, "%d / %d",
            (int) Math.ceil(b.health()), (int) Math.ceil(b.maxHealth())));
    final Derived<Boolean> shielded = derive(boss, b -> b.shield() > 0);
    final Derived<String> shieldWidth = derive(boss, b -> Math.min(100, b.shield() * 100 / b.maxHealth()) + "%");
    final Derived<String> shieldText = derive(boss, b -> "+" + (int) Math.ceil(b.shield()));
    final Derived<List<Buff>> buffs = derive(boss, this::buffs);

    private static List<Phase> phase(BossState b) {
        if (!b.present()) {
            return List.of();
        }
        float part = b.health() / b.maxHealth();
        int n = part > 2f / 3 ? 1 : part > 1f / 3 ? 2 : 3;
        Component title = n == PHASES ? Component.translatable("ixchel_sample.boss.enraged", b.name())
                : Component.literal(b.name());
        return List.of(new Phase(n, Component.translatable("ixchel_sample.boss.phase", NUMERALS[n - 1]), title));
    }

    private List<Buff> buffs(BossState b) {
        List<Buff> out = new ArrayList<>(b.buffs().size());
        Map<String, Integer> seen = new HashMap<>();
        for (BossState.Buff e : b.buffs()) {
            int total = Math.max(e.ticks(), totals.getOrDefault(e.effect(), 0));
            seen.put(e.effect(), total);
            ResourceLocation id = new ResourceLocation(e.effect());
            out.add(new Buff(e.effect(), id.getNamespace() + ":textures/mob_effect/" + id.getPath() + ".png",
                    Component.translatable("effect." + id.getNamespace() + "." + id.getPath()),
                    e.amplifier() == 0 ? "" : NUMERALS.length > e.amplifier() ? NUMERALS[e.amplifier()] : String.valueOf(e.amplifier() + 1),
                    total == 0 ? 0 : e.ticks() / (float) total, (e.ticks() + 19) / 20 + "s", e.beneficial()));
        }
        totals.clear();
        totals.putAll(seen);
        return out;
    }
}
