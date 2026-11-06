package com.azane.ixchel.mc.playtest;

import com.azane.ixchel.core.playtest.Recording;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;

/**
 * The recorder of a recording run ({@code -Dixchel.record.<modid>}): opens and closes takes,
 * captures each frame drawn while one is open into {@code playtests/<modid>/recordings/<take>.mp4},
 * steps the game's time one video frame per frame ({@link GameClock}) and keeps the integrated server
 * in step with each frame ({@link #settle}). The window size, GUI scale, frame rate, output scale and
 * ffmpeg come from system properties ({@link Settings}). Client thread only.
 */
final class Recorder implements Recording {
    private static final Logger LOG = LogUtils.getLogger();
    /** How long a frame waits for the server at most, in seconds of the wall clock. */
    private static final int SERVER_WAIT_SECONDS = 5;
    /** How long a packet is given to reach the other side, in nanoseconds of the wall clock. */
    private static final long PACKET_NANOS = 2_000_000L;
    private static final long NOON = 6000;

    /**
     * The run's settings, from {@code ixchel.recording.fps} (60), {@code .ffmpeg} (the {@code ffmpeg}
     * on PATH), {@code .window} ({@code 1280x720}), {@code .guiScale} (2), {@code .scale} (1: the
     * video has the window's pixels; 0.5 with a window twice as big and twice the GUI scale is
     * supersampling) and {@code .crf} (18; 0 keeps every pixel, for checking frames).
     */
    record Settings(int fps, String ffmpeg, int windowWidth, int windowHeight, int guiScale, double scale, int crf) {
        static Settings fromSystemProperties() {
            String window = System.getProperty("ixchel.recording.window", "1280x720");
            int x = window.indexOf('x');
            if (x < 0) {
                throw new IllegalArgumentException("ixchel.recording.window must be <width>x<height>, not " + window);
            }
            Settings s = new Settings(Integer.parseInt(System.getProperty("ixchel.recording.fps", "60")),
                    System.getProperty("ixchel.recording.ffmpeg", "ffmpeg"),
                    Integer.parseInt(window.substring(0, x)), Integer.parseInt(window.substring(x + 1)),
                    Integer.parseInt(System.getProperty("ixchel.recording.guiScale", "2")),
                    Double.parseDouble(System.getProperty("ixchel.recording.scale", "1")),
                    Integer.parseInt(System.getProperty("ixchel.recording.crf", "18")));
            if (s.fps <= 0 || s.windowWidth <= 0 || s.windowHeight <= 0 || s.guiScale <= 0 || !(s.scale > 0) || s.crf < 0) {
                throw new IllegalArgumentException("recording settings out of range: " + s);
            }
            return s;
        }
    }

    private final Settings settings;
    private final Path dir;
    private final GameClock clock = new GameClock();
    private final RecordingOverlay overlay = new RecordingOverlay();
    private Take take;
    /** The server whose sky {@link #stillSky} held, and its cycles before. */
    private MinecraftServer stillServer;
    private boolean daylightCycle;
    private boolean weatherCycle;

    Recorder(Settings settings, Path dir) {
        this.settings = settings;
        this.dir = dir;
    }

    /**
     * Gives the window the run's size and GUI scale, and turns the clouds off (they drift with the
     * count of ticks since the world loaded); in memory only, before the world loads.
     */
    void prepareWindow() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        window.setWindowed(settings.windowWidth, settings.windowHeight);
        mc.options.guiScale().set(settings.guiScale);
        mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.resizeDisplay();
        if (window.getWidth() != settings.windowWidth || window.getHeight() != settings.windowHeight
                || window.getGuiScale() != settings.guiScale) {
            LOG.warn("[ixchel-playtest] recording at {}x{}, GUI scale {}, not the {}x{}, GUI scale {} asked for",
                    window.getWidth(), window.getHeight(), window.getGuiScale(),
                    settings.windowWidth, settings.windowHeight, settings.guiScale);
        }
    }

    /**
     * Holds the sky still for the run, so it looks the same in every recording: noon, clear, with the
     * daylight and weather cycles off until {@link #close}. Once the world is loaded.
     */
    void stillSky(MinecraftServer server) {
        server.submit(() -> {
            GameRules rules = server.getGameRules();
            daylightCycle = rules.getBoolean(GameRules.RULE_DAYLIGHT);
            weatherCycle = rules.getBoolean(GameRules.RULE_WEATHER_CYCLE);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            server.overworld().setDayTime(NOON);
            server.overworld().setWeatherParameters(0, 0, false, false);
        }).join();
        stillServer = server;
    }

    @Override
    public long nanos() {
        return Util.getNanos();
    }

    @Override
    public float viewportHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    @Override
    public void beginTake(String name, float[] region, boolean gif) {
        if (take != null) {
            throw new IllegalStateException("take " + name + " begins while " + take.name() + " is open");
        }
        take = Take.open(name, dir, region, gif, settings);
        overlay.clear();
        clock.step(settings.fps);
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            long millis = clock.serverMillis();
            server.submit(() -> GameClock.alignServerTicks(server, millis)).join();
        }
    }

    @Override
    public void endTake() {
        if (take == null) {
            return;
        }
        Take t = take;
        take = null;
        clock.run();
        LOG.info("[ixchel-playtest] recorded {}", t.finish());
    }

    @Override
    public void pressed(double x, double y, int button) {
        if (take != null) {
            overlay.pressed(x, y, button, nanos());
        }
    }

    /** After each drawn frame: captures it into the open take, then moves the take's time to the next frame. */
    void frame() {
        if (take == null) {
            return;
        }
        take.capture(overlay, nanos(), ScreenInput.INSTANCE.pointer());
        clock.advance();
    }

    /**
     * After the frame's steps, before the next frame: inside a take, waits until the integrated
     * server has done everything the time and the frame's input ask of it and what it sent has come
     * back, so that it shows on the same frame of every recording, however fast the frames come.
     * Packets travel on threads of their own, which nothing can be asked about: the two pauses let
     * them land. The server's tasks run after any tick that is due, so waiting for one waits for
     * that too. A server busy past {@value #SERVER_WAIT_SECONDS} s is left to catch up by itself.
     */
    void settle() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (take == null || server == null || !server.isRunning()) {
            return;
        }
        LockSupport.parkNanos(PACKET_NANOS);
        try {
            server.submit(() -> { }).get(SERVER_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.warn("[ixchel-playtest] take {}: a frame gave up waiting for the server", take.name());
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException("waiting for the server failed", e);
        }
        LockSupport.parkNanos(PACKET_NANOS);
    }

    /** Ends the run: stops a take left open without finishing its video, frees the overlay, gives the world its cycles back. */
    void close() {
        if (take != null) {
            LOG.error("[ixchel-playtest] the run ended inside take {}; its video is not finished", take.name());
            take.abort();
            take = null;
            clock.run();
        }
        overlay.close();
        MinecraftServer server = stillServer;
        if (server != null && server.isRunning()) {
            server.submit(() -> {
                server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylightCycle, server);
                server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(weatherCycle, server);
            }).join();
        }
        stillServer = null;
    }
}
