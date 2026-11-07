package com.azane.ixchel.sample.playtest;

import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.playtest.Playtest;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.sample.boss.BossHud;
import com.azane.ixchel.sample.boss.BossState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code -Pplaytest=boss}: an iron golem (the test world is peaceful) tagged as the boss, with a shield and three effects, shows the
 * bar with its name, phase I, the shield and the effects; breaking the shield hides it; damage past
 * each third moves the phase on, the last renaming the boss enraged; a new debuff joins the row;
 * the boss gone, the bar hides. The fight is scripted on the server by setting health and
 * absorption. {@code -Precord=boss} records it.
 */
public final class BossPlaytest {
    private static final String NAME = "Iron Colossus";
    private static final float MAX = 300;

    private BossPlaytest() {
    }

    public static void register(Playtests playtests) {
        playtests.register("boss", s -> {
            UiHud hud = BossHud.hud();
            s.server(p -> {
                Vec3 at = p.position().add(p.getLookAngle().multiply(9, 0, 9));
                Mob m = EntityType.IRON_GOLEM.create(p.serverLevel());
                m.moveTo(at.x, p.getY(), at.z, p.getYRot() + 180, 0);
                m.setNoAi(true);
                m.addTag(BossState.TAG);
                m.setCustomName(Component.literal(NAME));
                m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(MAX);
                m.setHealth(MAX);
                m.setAbsorptionAmount(60);
                m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 900, 1));
                m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 500));
                m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 300));
                p.serverLevel().addFreshEntity(m);
            });
            s.run(() -> hud.show(true));
            s.waitUntil("the bar shows the boss", () -> Playtest.query(hud, ".boss.shown").size() == 1, Playtest.WAIT);
            s.expect("the bar reports no errors", () -> hud.document().diagnostics().errorCount() == 0);
            s.expect("name, phase I, health, the shield and three effects", () ->
                    Playtest.text(hud, ".title").equals(NAME) && Playtest.text(hud, ".phase").equals("Phase I")
                            && Playtest.text(hud, ".numbers").equals("300 / 300")
                            && Playtest.query(hud, ".shield.shown").size() == 1
                            && Playtest.text(hud, ".shield-text").equals("+60")
                            && Playtest.query(hud, ".buff").size() == 3 && Playtest.query(hud, ".buff.debuff").size() == 1);
            s.take("boss", t -> {
                t.hold(1500);
                t.server(boss(m -> m.setAbsorptionAmount(25)));
                t.hold(700);
                t.server(boss(m -> m.setAbsorptionAmount(0)));
                // Fails if the shield does not follow the boss's absorption.
                t.waitUntil("the shield breaks", () -> Playtest.query(hud, ".shield.shown").isEmpty(), Playtest.WAIT);
                t.hold(800);
                t.server(boss(m -> m.setHealth(250)));
                t.hold(900);
                t.server(boss(m -> m.setHealth(190)));
                // Fails if the phase does not follow the health past the first third.
                t.waitUntil("phase II past the first third", () -> Playtest.text(hud, ".phase").equals("Phase II"), Playtest.WAIT);
                t.hold(1200);
                t.server(boss(m -> m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400))));
                t.waitUntil("weakness joins the row as a debuff", () -> Playtest.query(hud, ".buff.debuff").size() == 2, Playtest.WAIT);
                t.hold(800);
                t.server(boss(m -> m.setHealth(130)));
                t.hold(900);
                t.server(boss(m -> m.setHealth(80)));
                t.waitUntil("phase III enrages the boss", () -> Playtest.text(hud, ".phase").equals("Phase III")
                        && Playtest.text(hud, ".title").equals(NAME + ", Enraged")
                        && Playtest.query(hud, ".boss.enraged").size() == 1, Playtest.WAIT);
                t.hold(2000);
                t.server(boss(m -> m.setHealth(20)));
                t.hold(1200);
                t.server(boss(Mob::discard));
                t.waitUntil("the bar hides with the boss gone", () -> Playtest.query(hud, ".boss.shown").isEmpty(), Playtest.WAIT);
                t.hold(600);
            });
            s.run(() -> hud.show(false));
        });
    }

    /** A server step on every boss near the player. */
    private static Consumer<ServerPlayer> boss(Consumer<Mob> action) {
        return p -> {
            List<Mob> bosses = p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(64),
                    m -> m.getTags().contains(BossState.TAG));
            bosses.forEach(action);
        };
    }
}
