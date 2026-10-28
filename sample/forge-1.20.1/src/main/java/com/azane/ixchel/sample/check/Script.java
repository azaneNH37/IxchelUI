package com.azane.ixchel.sample.check;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.UiContainerScreen;
import com.azane.ixchel.mc.UiScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The steps of one check ({@link Checks#register}), run on the client thread: at most one step per
 * client tick, and a frame is drawn between two steps. Steps that wait ({@link #waitUntil},
 * {@link #server}) hold the queue until they are done; use {@link #pause} only where game time has
 * to pass (a fade, an effect running out).
 *
 * <p>Selector steps act on the open {@link UiScreen} or {@link UiContainerScreen}: the first element
 * in document order that matches, at the center of its border box as drawn (scroll offsets
 * applied). A step that throws, or a selector that matches nothing, fails the check and the queue
 * goes on.
 *
 * <pre>{@code
 * Checks.register("waypoints", s -> s
 *         .run(() -> mc.setScreen(WaypointsClient.screen()))
 *         .click(".button")
 *         .waitUntil("one row", () -> Script.query(".row").size() == 1, 10)
 *         .shot("waypoints-one"));
 * }</pre>
 */
public final class Script {
    private static final Logger LOG = LogUtils.getLogger();
    /** Default wait limit: one second. */
    public static final int WAIT = 20;

    /** One queued step; {@link #tick} returns whether it is done. */
    private interface Step {
        boolean tick();
    }

    private final String name;
    private final Deque<Step> steps = new ArrayDeque<>();
    private final List<String> results = new ArrayList<>();
    private int failed;

    Script(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }

    List<String> results() {
        return results;
    }

    int passed() {
        return results.size() - failed;
    }

    int failed() {
        return failed;
    }

    /** Advances the current step; whether the queue is empty. */
    boolean tick() {
        Step s = steps.peek();
        if (s == null) {
            return true;
        }
        boolean done;
        try {
            done = s.tick();
        } catch (RuntimeException ex) {
            LOG.error("[ixchel-bench] {} step failed", name, ex);
            report("step without exception (" + ex.getMessage() + ")", false);
            done = true;
        }
        if (done) {
            steps.poll();
        }
        return steps.isEmpty();
    }

    /** Logs and records one result; also usable from inside a {@link #run} action. */
    public void report(String what, boolean pass) {
        if (!pass) {
            failed++;
        }
        results.add((pass ? "PASS " : "FAIL ") + what);
        LOG.info("[ixchel-bench] {} check {}: {}", name, what, pass ? "PASS" : "FAIL");
    }

    /** Runs {@code action} on the next tick. */
    public Script run(Runnable action) {
        steps.add(() -> {
            action.run();
            return true;
        });
        return this;
    }

    /** Lets {@code ticks} client ticks pass, for checks about elapsed game time. */
    public Script pause(int ticks) {
        int[] left = {ticks};
        steps.add(() -> left[0]-- <= 0);
        return this;
    }

    /** Checks {@code check} once, on the next tick. */
    public Script expect(String what, BooleanSupplier check) {
        return run(() -> report(what, check.getAsBoolean()));
    }

    /** Passes as soon as {@code check} holds; fails after {@code maxTicks} ticks without it. */
    public Script waitUntil(String what, BooleanSupplier check, int maxTicks) {
        int[] left = {maxTicks};
        steps.add(() -> {
            if (check.getAsBoolean()) {
                report(what, true);
                return true;
            }
            if (left[0]-- <= 0) {
                report(what + " (within " + maxTicks + " ticks)", false);
                return true;
            }
            return false;
        });
        return this;
    }

    /**
     * {@link #waitUntil} against the integrated server's copy of the player, tested on the server
     * thread once per tick.
     */
    public Script waitUntilServer(String what, Predicate<ServerPlayer> check, int maxTicks) {
        int[] left = {maxTicks};
        // 0: no test queued; 1: queued; 2: held; 3: did not hold.
        AtomicInteger state = new AtomicInteger();
        steps.add(() -> {
            int s = state.get();
            if (s == 2) {
                report(what, true);
                return true;
            }
            if (s == 3 && left[0]-- <= 0) {
                report(what + " (within " + maxTicks + " ticks)", false);
                return true;
            }
            if (s != 1) {
                state.set(1);
                onServer(p -> state.set(check.test(p) ? 2 : 3));
            }
            return false;
        });
        return this;
    }

    /** Runs {@code change} on the integrated server's copy of the player; the next step waits for it. */
    public Script server(Consumer<ServerPlayer> change) {
        AtomicInteger state = new AtomicInteger();
        steps.add(() -> {
            if (state.compareAndSet(0, 1)) {
                onServer(p -> {
                    try {
                        change.accept(p);
                    } finally {
                        state.set(2);
                    }
                });
            }
            return state.get() == 2;
        });
        return this;
    }

    /** Left click on the element {@code selector} matches. */
    public Script click(String selector) {
        return click(selector, 0);
    }

    public Script click(String selector, int button) {
        return run(() -> {
            float[] p = center(selector);
            clickAt(p[0], p[1], button);
        });
    }

    /** Moves the pointer onto the element: the cursor vanilla reads, and the document's hover. */
    public Script hover(String selector) {
        return run(() -> {
            float[] p = center(selector);
            hoverAt(p[0], p[1]);
            screen().mouseMoved(p[0], p[1]);
        });
    }

    /** A wheel turn over the element; positive {@code delta} scrolls up, as vanilla. */
    public Script scroll(String selector, double delta) {
        return run(() -> {
            float[] p = center(selector);
            screen().mouseScrolled(p[0], p[1], delta);
        });
    }

    /** A press and release of a GLFW key on the open screen. */
    public Script key(int key) {
        return run(() -> {
            screen().keyPressed(key, 0, 0);
            screen().keyReleased(key, 0, 0);
        });
    }

    /** Saves {@code run/screenshots/<file>.png} on the next tick. */
    public Script shot(String file) {
        return run(() -> {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, file + ".png", mc.getMainRenderTarget(), msg -> { });
        });
    }

    /** The open screen's document. */
    public static UiDocument document() {
        Screen s = screen();
        UiDocument d = s instanceof UiScreen u ? u.document()
                : s instanceof UiContainerScreen<?> c ? c.document() : null;
        if (d == null) {
            throw new IllegalStateException("no Ixchel document on screen " + s);
        }
        return d;
    }

    /** Elements of the open screen's document that match {@code selector}. */
    public static List<Element> query(String selector) {
        return document().querySelectorAll(selector);
    }

    /** Where {@code selector}'s first match is drawn: the center of its box, moved by the scroll offsets above it. */
    private static float[] center(String selector) {
        List<Element> found = query(selector);
        if (found.isEmpty()) {
            throw new IllegalStateException("no element matches " + selector);
        }
        Element e = found.get(0);
        float y = e.box().y() + e.box().height() / 2;
        for (Element a = e.parent(); a != null; a = a.parent()) {
            y -= a.scrollTop();
        }
        return new float[]{e.box().x() + e.box().width() / 2, y};
    }

    private static Screen screen() {
        Screen s = Minecraft.getInstance().screen;
        if (s == null) {
            throw new IllegalStateException("no screen is open");
        }
        return s;
    }

    /** Runs {@code change} on the integrated server's copy of the player, on the server thread, without waiting. */
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
        Screen s = screen();
        s.mouseMoved(x, y);
        s.mouseClicked(x, y, button);
        s.mouseReleased(x, y, button);
    }
}
