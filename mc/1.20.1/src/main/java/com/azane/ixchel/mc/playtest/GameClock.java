package com.azane.ixchel.mc.playtest;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import net.minecraft.client.gui.Gui;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Field;
import java.util.Arrays;

/**
 * The game's time source in a recording run, put into {@link Util#timeSource} when the first take
 * begins and kept for the rest of the run. Inside a take it reads the take's start plus one video
 * frame per captured frame, however long the frames took to draw, read back and encode; between takes
 * it runs with the wall clock from where the last take left it, so it never goes back.
 *
 * <p>Everything that reads {@link Util#getNanos} or {@link Util#getMillis} follows it: the client's
 * ticks and partial tick, the integrated server's tick loop, documents ({@code HostedDocument} gives
 * them this clock), so transitions, animations, the caret's blink and effects' {@code ix_time}.
 * What reads {@link System#nanoTime} itself does not.
 *
 * <p>A take starts at the same point of every cycle of absolute time that shows on screen
 * ({@code ix_time} wraps every hour, the enchantment glint every 82.5 s): the client's time leaps
 * forward to the next multiple of {@value #PERIOD_NANOS} ns, and its tick phase and the count
 * {@code <mc-item-cycle>} and per-tick feeds read ({@link Gui#getGuiTicks}) are set to match, so two
 * recordings of a take show the same frames. Only the client thread sees the leap: the integrated
 * server and every other thread read the time without it, so the server's schedule and its
 * connection's keep-alive are not disturbed. Animations started before the leap end or move on by
 * it; what a take shows should start inside it.
 *
 * <p>Stepped on the client thread; read on any.
 */
final class GameClock {
    /** 11 hours: whole hours, and whole cycles of the enchantment glint at its default speed. */
    static final long PERIOD_NANOS = 39_600_000_000_000L;
    /** One hour of ticks: a multiple of every whole-second cycle interval. */
    private static final int TICK_PERIOD = 72_000;
    private static final long TICK_MILLIS = 50;

    private Thread client;
    private volatile boolean stepping;
    /** Outside takes: the client's time minus the wall clock's. */
    private volatile long offset;
    /** How far the client's time leapt ahead of every other thread's. */
    private volatile long leapt;
    /** Inside a take: the time of the frame being drawn, as the client sees it. */
    private volatile long frameNanos;
    private long base;
    private long frames;
    private int fps;

    private long now() {
        long t = stepping ? frameNanos : System.nanoTime() + offset;
        return Thread.currentThread() == client ? t : t - leapt;
    }

    /** The time as the integrated server sees it, in milliseconds; client thread. */
    long serverMillis() {
        return (now() - leapt) / 1_000_000;
    }

    /**
     * From now on, one video frame of {@code fps} per {@link #advance}, starting at the next multiple
     * of {@value #PERIOD_NANOS} ns; client thread.
     */
    void step(int fps) {
        if (client == null) {
            client = Thread.currentThread();
            offset = Util.timeSource.getAsLong() - System.nanoTime();
            Util.timeSource = this::now;
        }
        // The other threads' time leaps to a whole millisecond, so that it reads the same on each
        // frame of every take whatever the wall clock's phase.
        long now = Math.floorDiv(System.nanoTime() + offset, 1_000_000L) * 1_000_000L;
        base = Math.floorDiv(now, PERIOD_NANOS) * PERIOD_NANOS + PERIOD_NANOS;
        leapt += base - now;
        this.fps = fps;
        frames = 0;
        frameNanos = base;
        stepping = true;
        alignTicks(base / 1_000_000);
    }

    /** The next video frame. */
    void advance() {
        if (stepping) {
            frames++;
            frameNanos = base + frames * 1_000_000_000L / fps;
        }
    }

    /** Back to the wall clock, from the current time on. */
    void run() {
        if (stepping) {
            offset = frameNanos - System.nanoTime();
            stepping = false;
        }
    }

    /**
     * Starts the client's next tick a whole tick from {@code millis}, as if one had just run, instead
     * of catching up the leap; and moves the GUI's tick count on to a multiple of {@value #TICK_PERIOD}.
     */
    private static void alignTicks(long millis) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Timer timer = (Timer) field(Minecraft.class, "timer", "f_90991_").get(mc);
            field(Timer.class, "lastMs", "f_92520_").setLong(timer, millis);
            timer.partialTick = 0;
            Field ticks = field(Gui.class, "tickCount", "f_92989_");
            int count = ticks.getInt(mc.gui);
            ticks.setInt(mc.gui, (count / TICK_PERIOD + 1) * TICK_PERIOD);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot align the client's ticks with the take", e);
        }
    }

    /**
     * Schedules {@code server}'s next tick a whole tick from {@code millis}, so its ticks fall on the
     * same frames of the take every time; on the server's thread, between its ticks.
     */
    static void alignServerTicks(MinecraftServer server, long millis) {
        try {
            field(MinecraftServer.class, "nextTickTime", "f_129726_").setLong(server, millis + TICK_MILLIS);
            field(MinecraftServer.class, "delayedTasksMaxNextTickTime", "f_129727_").setLong(server, millis + TICK_MILLIS);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot align the server's ticks with the take", e);
        }
    }

    /** The field under its Mojang name (development) or its SRG name (a reobfuscated game). */
    private static Field field(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // try the next candidate name
            }
        }
        throw new IllegalStateException(owner.getSimpleName() + " has no field " + Arrays.toString(names));
    }
}
