package com.azane.ixchel.sample.status;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;

/**
 * {@code -Pcheck=status}: sets health, food, experience and two effects on the server, shows the HUD
 * and checks the icons and effect rows, then removes an effect.
 */
final class StatusCheck {
    private StatusCheck() {
    }

    static void init(IxchelForge.Hud hud) {
        Checks.register(StatusHud.ID, s -> s
                // Easy, not the dev world's peaceful: peaceful refills health and food every few ticks.
                .server(p -> {
                    p.server.setDifficulty(Difficulty.EASY, true);
                    p.setGameMode(GameType.SURVIVAL);
                    p.setHealth(7);
                    p.getFoodData().setFoodLevel(12);
                    p.setExperienceLevels(5);
                    p.experienceProgress = 0.4f;
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 150, 0));
                })
                .run(() -> hud.show(true))
                .waitUntil("7 health is 3 full hearts and a half", () -> count(hud, ".heart .fill.full") == 3
                        && count(hud, ".heart .fill.half") == 1 && count(hud, ".heart") == 10, Script.WAIT)
                .waitUntil("12 food is 6 full shanks", () -> count(hud, ".food .fill.full") == 6, Script.WAIT)
                .waitUntil("two effect rows, night vision ending", () -> count(hud, ".effect") == 2
                        && count(hud, ".effect.ending") == 1, Script.WAIT)
                .shot("status-on")
                .server(p -> p.removeEffect(MobEffects.MOVEMENT_SPEED))
                .waitUntil("removed effect leaves its row", () -> count(hud, ".effect") == 1, Script.WAIT)
                .shot("status-one-effect")
                .run(() -> hud.show(false)));
    }

    private static int count(IxchelForge.Hud hud, String selector) {
        return hud.document().querySelectorAll(selector).size();
    }
}
