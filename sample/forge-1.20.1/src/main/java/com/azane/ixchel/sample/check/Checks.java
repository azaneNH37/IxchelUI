package com.azane.ixchel.sample.check;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Unattended functional checks, run with {@code -Pcheck=<name>[,<name>…]|all}. Once the player is in
 * the world the selected checks run one after another, in the order given ({@code all}: registration
 * order). Each starts from the same state (no screen, empty inventory, no effects, creative, peaceful,
 * full health and food), then runs the steps its body queued. Results are logged as
 * {@code [ixchel-bench] <name> check <what>: PASS|FAIL} and written to {@code run/checks/report.txt};
 * after the last check the game quits.
 */
public final class Checks {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<String, Consumer<Script>> REGISTERED = new LinkedHashMap<>();
    private static final List<Script> finished = new ArrayList<>();
    private static Deque<String> pending;
    private static Script current;
    private static boolean drawn;

    private Checks() {
    }

    /** Adds a check; {@code body} queues its steps on the script when the check starts. */
    public static void register(String name, Consumer<Script> body) {
        if (REGISTERED.putIfAbsent(name, body) != null) {
            throw new IllegalStateException("check " + name + " is registered twice");
        }
    }

    /** Starts the run when {@code -Pcheck} selected checks; call once, from the mod constructor. */
    public static void init() {
        String selected = System.getProperty("ixchelsample.check");
        if (selected == null) {
            return;
        }
        // A step runs only once a frame was drawn since the previous one, as between a user's actions:
        // documents lay out when they are drawn, and ticks catch up without frames after a world loads.
        MinecraftForge.EVENT_BUS.addListener((TickEvent.RenderTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                drawn = true;
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase != TickEvent.Phase.END || !drawn || Minecraft.getInstance().player == null) {
                return;
            }
            drawn = false;
            if (pending == null) {
                pending = new ArrayDeque<>(selected.equals("all") ? REGISTERED.keySet() : Arrays.asList(selected.split(",")));
            }
            tick();
        });
    }

    private static void tick() {
        if (current == null) {
            String name = pending.poll();
            if (name == null) {
                finish();
                return;
            }
            current = new Script(name.trim());
            Consumer<Script> body = REGISTERED.get(current.name());
            if (body == null) {
                current.report("is registered (known: " + String.join(", ", REGISTERED.keySet()) + ")", false);
            } else {
                reset(current);
                body.accept(current);
            }
        }
        if (current.tick()) {
            finished.add(current);
            current = null;
        }
    }

    private static void reset(Script s) {
        s.run(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?>) {
                mc.player.closeContainer();
            } else {
                mc.setScreen(null);
            }
            mc.player.getInventory().selected = 0;
        });
        s.server(p -> {
            p.server.setDifficulty(Difficulty.PEACEFUL, true);
            p.setGameMode(GameType.CREATIVE);
            p.getInventory().clearContent();
            p.removeAllEffects();
            p.setHealth(p.getMaxHealth());
            p.getFoodData().setFoodLevel(20);
            p.setExperienceLevels(0);
            p.experienceProgress = 0;
        });
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder report = new StringBuilder();
        int passed = 0;
        int failed = 0;
        for (Script s : finished) {
            report.append(s.name()).append('\n');
            for (String line : s.results()) {
                report.append("  ").append(line).append('\n');
            }
            report.append(s.name()).append(": ").append(s.passed()).append(" passed, ").append(s.failed())
                    .append(" failed\n\n");
            passed += s.passed();
            failed += s.failed();
        }
        report.append("total: ").append(passed).append(" passed, ").append(failed).append(" failed\n");
        Path file = mc.gameDirectory.toPath().resolve("checks/report.txt");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, report, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.error("[ixchel-bench] cannot write {}", file, ex);
        }
        LOG.info("[ixchel-bench] checks done, {} passed, {} failed", passed, failed);
        mc.stop();
    }
}
