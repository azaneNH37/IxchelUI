package com.azane.ixchel.sample.check;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * An unattended check, run with {@code -Pbench=<name>}: steps run on the client thread a number of
 * ticks apart once the player is in the world; each check logs
 * {@code [ixchel-bench] <name> check <what>: PASS|FAIL}, and after the last step the game quits
 * with {@code <name> done, N check(s) failed}. Screenshots land in {@code run/screenshots/}.
 */
public final class Script {
    private static final Logger LOG = LogUtils.getLogger();

    private record Step(int delayTicks, Runnable action) {
    }

    private final String name;
    private final Deque<Step> steps = new ArrayDeque<>();
    private int failures;
    private Step next;
    private int wait;

    private Script(String name) {
        this.name = name;
    }

    /** The script for {@code name}, or {@code null} when this run is not {@code -Pbench=<name>}. */
    public static Script forBench(String name) {
        return name.equals(System.getProperty("ixchelsample.bench")) ? new Script(name) : null;
    }

    /** Runs {@code action} {@code delayTicks} client ticks after the previous step. */
    public Script step(int delayTicks, Runnable action) {
        steps.add(new Step(delayTicks, action));
        return this;
    }

    /** Starts running the steps; call once, after adding them. */
    public void start() {
        MinecraftForge.EVENT_BUS.addListener(this::tick);
    }

    private void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        if (next == null) {
            next = steps.poll();
            if (next == null) {
                LOG.info("[ixchel-bench] {} done, {} check(s) failed", name, failures);
                mc.stop();
                return;
            }
            wait = next.delayTicks;
        }
        if (wait-- > 0) {
            return;
        }
        Step s = next;
        next = null;
        try {
            s.action.run();
        } catch (RuntimeException ex) {
            LOG.error("[ixchel-bench] {} step failed", name, ex);
            report("step without exception", false);
        }
    }

    public void report(String what, boolean pass) {
        if (!pass) {
            failures++;
        }
        LOG.info("[ixchel-bench] {} check {}: {}", name, what, pass ? "PASS" : "FAIL");
    }

    public void expect(String what, BooleanSupplier check) {
        report(what, check.getAsBoolean());
    }

    /** Runs {@code check} against the integrated server's copy of the player, on the server thread. */
    public void expectServer(String what, Predicate<ServerPlayer> check) {
        onServer(p -> report(what, check.test(p)));
    }

    /** Saves {@code run/screenshots/<file>.png}. */
    public static void shot(String file) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, file + ".png", mc.getMainRenderTarget(), msg -> { });
    }

    /** Runs {@code change} on the integrated server's copy of the player, on the server thread. */
    public static void onServer(Consumer<ServerPlayer> change) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> change.accept(server.getPlayerList().getPlayer(id)));
    }

    /**
     * Sets the cursor position (GUI pixels) vanilla hands to {@code render}, which is what hover
     * reads. The window is not focused in an unattended run, so moving the OS cursor does not reach
     * the game; the dev run has mojmap names.
     */
    public static void hoverAt(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        try {
            Field xpos = MouseHandler.class.getDeclaredField("xpos");
            Field ypos = MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(mc.mouseHandler, x * scale);
            ypos.setDouble(mc.mouseHandler, y * scale);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A press and release of {@code button} at {@code (x, y)} on the open screen, in GUI pixels. */
    public static void clickAt(double x, double y, int button) {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) {
            throw new IllegalStateException("no screen is open");
        }
        s.mouseMoved(x, y);
        s.mouseClicked(x, y, button);
        s.mouseReleased(x, y, button);
    }
}
