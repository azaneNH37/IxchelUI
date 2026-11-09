package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.reel.ReelBanner;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code -Precord=reel}: the showcase samples in one take, each after its title card, from the
 * workbench to the arcane tree. Each scene is its playtest's setup, recorded walk and teardown, so
 * the reel also checks that they run back to back.
 */
public final class ReelPlaytest {
    private record Scene(String id, String theme, Consumer<Playtest> body) {
    }

    private static final List<Scene> SCENES = List.of(
            new Scene("workbench", "holo", WorkbenchPlaytest::scene),
            new Scene("machine", "amber", MachinePlaytest::scene),
            new Scene("loot", "amber", LootPlaytest::scene),
            new Scene("spell", "holo", SpellPlaytest::scene),
            new Scene("boss", "dark", BossPlaytest::scene),
            new Scene("storage", "vanilla", StoragePlaytest::scene),
            new Scene("arcane", "arcane", ArcanePlaytest::scene));

    /** How long a card shows alone before its scene starts under it. */
    private static final int LEAD_MS = 1400;

    private ReelPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("reel", s -> s.take("reel", t -> {
            for (int i = 0; i < SCENES.size(); i++) {
                Scene scene = SCENES.get(i);
                int n = i + 1;
                t.run(() -> ReelBanner.show(scene.id(), n, SCENES.size(), scene.theme()));
                t.hold(LEAD_MS);
                scene.body().accept(t);
                t.hold(600);
            }
        }));
    }
}
