package com.azane.ixchel.sample.status;

import com.azane.ixchel.forge.IxchelForge;
import com.azane.ixchel.sample.check.Checks;
import com.azane.ixchel.sample.check.Script;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** {@code -Pcheck=status}: adds two effects on the server, checks the rows, then removes one. */
final class StatusCheck {
    private StatusCheck() {
    }

    static void init(IxchelForge.Hud hud) {
        Checks.register(StatusHud.ID, s -> s
                .server(p -> {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 150, 0));
                })
                .run(() -> hud.show(true))
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
