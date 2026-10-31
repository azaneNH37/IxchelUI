package com.azane.ixchel.sample.check.scenario;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.mc.FrameTimer;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.check.Checks;
import com.azane.ixchel.mc.check.Script;
import com.azane.ixchel.sample.env.EnvHud;
import com.azane.ixchel.sample.waypoints.Waypoints;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

import java.util.List;

/**
 * {@code -Pcheck=env}: stands the player on the dev world's superflat ground at the origin, facing
 * north, and puts things in front of the crosshair: oak stairs, stone, a pig. Then sets the time and
 * the weather, turns with a waypoint to the north, sets the vitals, and times the layer while the
 * player moves and turns every tick.
 */
public final class EnvCheck {
    /** Two blocks north of the player, at eye height. */
    private static final BlockPos AHEAD = new BlockPos(0, -59, -2);
    private static final int TIMED_TICKS = 200;
    /*
     * Budgets are about twice the p95 on the dev machine (AMD iGPU, 2026-10-28): still median 1.4 ms /
     * p95 3.1 ms, moving 1.3 / 3.4 ms (the ammo HUD: p95 0.5 ms; this HUD before the compass: moving
     * 0.9 / 1.8 ms). Still costs as much as moving, so the time goes to drawing the display list every
     * frame (the whole compass strip included, clipped or not), not to layout.
     */
    private static final double STILL_BUDGET_US = 6000;
    private static final double MOVING_BUDGET_US = 6000;

    private EnvCheck() {
    }

    public static void register(Checks checks) {
        checks.register("env", s -> {
            UiHud hud = EnvHud.hud();
            float[] stairsHeight = {0};
            Pig[] pig = {null};
            s.server(p -> {
                ServerLevel l = p.serverLevel();
                for (int z = -4; z <= 0; z++) {
                    for (int y = -60; y <= -58; y++) {
                        l.setBlockAndUpdate(new BlockPos(0, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
                l.setBlockAndUpdate(AHEAD, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
                l.setDayTime(6000);
                l.setWeatherParameters(6000, 0, false, false);
                p.teleportTo(l, 0.5, -60, 0.5, 180, 0);
            });
            s.run(() -> hud.show(true));
            // Fails if the hit result is not read, or the name, mod or tool tag is wrong.
            s.waitUntil("oak stairs: name, mod, axe", () -> Script.text(hud, ".block .name").equals("Oak Stairs")
                    && Script.text(hud, ".block .mod").equals("Minecraft") && Script.text(hud, ".tool").equals("Axe"), Script.WAIT);
            // Fails if block states are not listed or their values are not named.
            s.expect("four stair states, facing east", () -> Script.query(hud, ".property").size() == 4
                    && property(hud, "facing").equals("east"));
            s.run(() -> stairsHeight[0] = Script.query(hud, ".target").get(0).box().height());
            s.shot("env-block");
            s.server(p -> p.serverLevel().setBlockAndUpdate(AHEAD, Blocks.STONE.defaultBlockState()));
            // Fails if a panel keeps the size of what it showed before.
            s.waitUntil("stone: pickaxe in red, panel shrinks without states", () -> Script.text(hud, ".block .name").equals("Stone")
                    && Script.query(hud, ".tool.bad").size() == 1 && Script.query(hud, ".property").isEmpty()
                    && Script.query(hud, ".target").get(0).box().height() < stairsHeight[0], Script.WAIT);
            s.server(p -> {
                ServerLevel l = p.serverLevel();
                l.setBlockAndUpdate(AHEAD, Blocks.AIR.defaultBlockState());
                pig[0] = EntityType.PIG.create(l);
                pig[0].moveTo(0.5, -60, -2.5, 0, 0);
                pig[0].setNoAi(true);
                l.addFreshEntity(pig[0]);
                // Looking 30° down: the ray reaches the pig's front before the ground.
                p.teleportTo(l, 0.5, -60, 0.5, 180, 30);
            });
            // Fails if entities are not read from the hit result.
            s.waitUntil("pig: name and 10 / 10 health", () -> Script.text(hud, ".entity .name").equals("Pig")
                    && Script.text(hud, ".health-text").equals("10 / 10"), Script.WAIT);
            s.server(p -> pig[0].setHealth(4));
            // Fails if the bar does not follow the entity's health.
            s.waitUntil("pig at 4 health: bar 40% full", () -> Script.text(hud, ".health-text").equals("4 / 10")
                    && Math.abs(width(hud, ".health-fill") - width(hud, ".health-bar") * 0.4f) < 0.5f, Script.WAIT);
            s.shot("env-entity");
            s.server(p -> {
                pig[0].discard();
                p.serverLevel().setWeatherParameters(0, 6000, true, false);
            });
            // Fails if the entity section outlives its entity; the ray now ends on the ground.
            s.waitUntil("pig gone: entity replaced by the grass below", () -> Script.query(hud, ".entity").isEmpty()
                    && Script.text(hud, ".block .name").equals("Grass Block"), Script.WAIT);
            // Fails if a surroundings line reads the wrong thing; rain fades in over about 20 ticks.
            s.waitUntil("plains, noon of day 1, rain, origin, facing north", () -> Script.text(hud, ".biome").equals("Plains")
                    && Script.text(hud, ".time").startsWith("Day 1, 12:0") && Script.text(hud, ".weather").equals("Rain")
                    && Script.text(hud, ".coords").equals("0, -60, 0") && Script.text(hud, ".facing").equals("N 0\u00b0"), 60);
            // Compass: a waypoint 20 blocks north. Fails if the strip does not follow the heading, or a pip
            // its bearing, or the label and the end-of-bar state are wrong.
            s.run(() -> {
                Waypoints.clear();
                Waypoints.add(new BlockPos(0, -60, -20));
            });
            s.waitUntil("north: N under the needle, the waypoint ahead with its label", () -> offset(hud, "N") == 0
                    && Script.query(hud, ".waypoint.near").size() == 1 && Script.text(hud, ".waypoint-label").equals("Waypoint 1 20 m")
                    && Math.abs(center(hud, ".pip") - center(hud, ".needle")) < 1, Script.WAIT);
            s.shot("env-compass");
            s.server(p -> p.teleportTo(p.serverLevel(), 0.5, -60, 0.5, 225, 0));
            s.waitUntil("north-east: NE under the needle, the waypoint 45px left, no label", () -> offset(hud, "NE") == 0
                    && Script.query(hud, ".waypoint.near").isEmpty()
                    && Math.abs(center(hud, ".pip") - (center(hud, ".needle") - 45)) < 1, Script.WAIT);
            s.server(p -> p.teleportTo(p.serverLevel(), 0.5, -60, 0.5, 0, 0));
            s.waitUntil("south: S under the needle, the waypoint behind held at the left end", () -> offset(hud, "S") == 0
                    && Script.query(hud, ".waypoint.edge").size() == 1
                    && Math.abs(center(hud, ".pip") - (Script.query(hud, ".bar").get(0).box().x() + 0.5f)) < 1, Script.WAIT);
            // Easy, not peaceful: peaceful refills health and food every few ticks.
            s.server(p -> {
                p.server.setDifficulty(Difficulty.EASY, true);
                p.setGameMode(GameType.SURVIVAL);
                p.setHealth(7);
                p.getFoodData().setFoodLevel(12);
                p.setExperienceLevels(5);
                p.experienceProgress = 0.4f;
                p.serverLevel().setWeatherParameters(6000, 0, false, false);
            });
            // Fails if the vitals do not follow the player.
            s.waitUntil("7 health is 3 full hearts and a half, 12 food is 6 shanks", () -> Script.query(hud, ".heart .fill.full").size() == 3
                    && Script.query(hud, ".heart .fill.half").size() == 1 && Script.query(hud, ".food .fill.full").size() == 6
                    && Script.text(hud, ".level").equals("5"), Script.WAIT);
            s.shot("env-world");
            // Standing still only the clock changes, about every 17 ticks: most frames replay the last
            // layout. Fails if an unchanged frame costs more than the budget at p95.
            s.run(() -> FrameTimer.hud(hud, 4096));
            s.pause(100);
            s.run(() -> FrameTimer.report(s, "standing still", STILL_BUDGET_US));
            // Every tick the coordinates, the facing, the compass and the targeted block's position
            // change: the layer lays out and paints every tick. Fails if that costs more than the budget
            // at p95.
            s.run(() -> FrameTimer.hud(hud, 4096));
            for (int i = 0; i < TIMED_TICKS; i++) {
                int tick = i;
                s.run(() -> {
                    var player = Minecraft.getInstance().player;
                    player.setPos(0.5 + tick * 0.3, -60, 0.5);
                    player.setYRot(180 + tick * 1.8f);
                    player.setXRot(40);
                });
            }
            s.run(() -> FrameTimer.report(s, "moving and turning every tick", MOVING_BUDGET_US));
            s.shot("env-moving");
            s.run(() -> hud.show(false));
        });
    }

    private static float width(UiHud hud, String selector) {
        List<Element> found = Script.query(hud, selector);
        return found.isEmpty() ? -1 : found.get(0).box().width();
    }

    /** Horizontal center of {@code selector}'s first match, or NaN. */
    private static float center(UiHud hud, String selector) {
        List<Element> found = Script.query(hud, selector);
        return found.isEmpty() ? Float.NaN : found.get(0).box().x() + found.get(0).box().width() / 2;
    }

    /** Smallest distance from a compass tick labeled {@code label} to the needle, in pixels. */
    private static float offset(UiHud hud, String label) {
        List<Element> labels = Script.query(hud, ".mark .mark-label");
        List<Element> ticks = Script.query(hud, ".mark .tick");
        float needle = center(hud, ".needle");
        float best = Float.POSITIVE_INFINITY;
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).textContent().equals(label)) {
                Element t = ticks.get(i);
                best = Math.min(best, Math.abs(t.box().x() + t.box().width() / 2 - needle));
            }
        }
        return best;
    }

    /** The value shown for block state {@code name}, or an empty string. */
    private static String property(UiHud hud, String name) {
        return Script.valueOf(Script.query(hud, ".property .key"), Script.query(hud, ".property .value"), name);
    }
}
