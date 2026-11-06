package com.azane.ixchel.mc.playtest;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.playtest.PlaytestDriver;
import com.azane.ixchel.core.playtest.Dump;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.UiHud;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One playtest in a Minecraft client: the {@link PlaytestDriver} steps through the open vanilla
 * screen, plus steps on the integrated server's copy of the player and screenshots, and reads of the
 * open screen's or a HUD's document. Each failed assertion writes the open Ixchel screen's document
 * ({@link Dump}) to {@code playtests/<modid>/dumps/<playtest>-<n>.txt} under the game directory.
 * Client thread only.
 *
 * <pre>{@code
 * playtests.register("index", s -> s
 *         .run(SampleIndex::open)
 *         .waitUntil("index lists every sample", () -> Playtest.query(".row").size() == 10, Playtest.WAIT)
 *         .shot("sample-index"));
 * }</pre>
 */
@HostApi
public final class Playtest extends PlaytestDriver<Playtest> {
    private static final Logger LOG = LogUtils.getLogger();

    private final Path dir;

    /** @param recorder the run's recorder in a recording run, else {@code null} */
    Playtest(String name, Path dir, Recorder recorder) {
        super(name, ScreenInput.INSTANCE, recorder);
        this.dir = dir;
    }

    @Override
    protected void recorded(String what, boolean pass) {
        LOG.info("[ixchel-playtest] {} {}: {}", name(), what, pass ? "PASS" : "FAIL");
        if (!pass) {
            dump(what);
        }
    }

    @Override
    protected void threw(RuntimeException ex) {
        LOG.error("[ixchel-playtest] {} step failed", name(), ex);
    }

    private void dump(String what) {
        UiDocument d = ScreenInput.openDocument();
        Path dumps = dir.resolve("dumps");
        Path file = dumps.resolve(name() + "-" + failed() + ".txt");
        String text = "FAIL " + what + "\nscreen: " + Minecraft.getInstance().screen + "\n\n"
                + (d == null ? "no Ixchel document on screen\n" : Dump.of(d));
        try {
            Files.createDirectories(dumps);
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            LOG.error("[ixchel-playtest] cannot write {}", file, ex);
        }
    }

    /**
     * {@link #waitUntil} against the integrated server's copy of the player, tested on the server
     * thread once per tick.
     */
    public Playtest waitUntilServer(String what, Predicate<ServerPlayer> condition, int maxTicks) {
        int[] left = {maxTicks};
        // 0: no test queued; 1: queued; 2: held; 3: did not hold.
        AtomicInteger state = new AtomicInteger();
        return then(() -> {
            int s = state.get();
            if (s == 2) {
                record(what, true);
                return true;
            }
            if (s == 3 && left[0]-- <= 0) {
                record(what + " (within " + maxTicks + " ticks)", false);
                return true;
            }
            if (s != 1) {
                state.set(1);
                onServer(p -> state.set(condition.test(p) ? 2 : 3));
            }
            return false;
        });
    }

    /** Runs {@code change} on the integrated server's copy of the player; the next step waits for it. */
    public Playtest server(Consumer<ServerPlayer> change) {
        AtomicInteger state = new AtomicInteger();
        return then(() -> {
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
    }

    /** Saves {@code playtests/<modid>/screenshots/<file>.png} under the game directory on the next tick. */
    public Playtest shot(String file) {
        return run(() -> {
            Minecraft mc = Minecraft.getInstance();
            // Screenshot creates only the last directory, screenshots/, under the one it is given.
            dir.toFile().mkdirs();
            Screenshot.grab(dir.toFile(), file + ".png", mc.getMainRenderTarget(), msg -> { });
        });
    }

    /** Whether an Ixchel screen is open. */
    public static boolean isOpen() {
        return ScreenInput.openDocument() != null;
    }

    /** The open screen's document. */
    public static UiDocument document() {
        return ScreenInput.INSTANCE.document();
    }

    /** Elements of the open screen's document that match {@code selector}. */
    public static List<Element> query(String selector) {
        return query(document(), selector);
    }

    /** Elements of the HUD's document that match {@code selector}. */
    public static List<Element> query(UiHud hud, String selector) {
        return query(hud.document(), selector);
    }

    /** Text of the first match on the open screen, or an empty string. */
    public static String text(String selector) {
        return text(document(), selector);
    }

    /** Text of the first match on the HUD, or an empty string. */
    public static String text(UiHud hud, String selector) {
        return text(hud.document(), selector);
    }

    /** Texts of every match on the open screen, in document order. */
    public static List<String> texts(String selector) {
        return texts(document(), selector);
    }

    /** Texts of every match on the HUD, in document order. */
    public static List<String> texts(UiHud hud, String selector) {
        return texts(hud.document(), selector);
    }

    /** The attribute {@code name} of the first match on the open screen, or an empty string. */
    public static String attribute(String selector, String name) {
        return attribute(document(), selector, name);
    }

    /** The laid-out box of the first match on the open screen. */
    public static LayoutBox box(String selector) {
        return box(document(), selector);
    }

    /** The open container screen's menu if it is a {@code type}, else {@code null}. */
    public static <M extends AbstractContainerMenu> M menu(Class<M> type) {
        return Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> c && type.isInstance(c.getMenu())
                ? type.cast(c.getMenu()) : null;
    }

    public static AbstractContainerScreen<?> containerScreen() {
        return (AbstractContainerScreen<?>) ScreenInput.screen();
    }

    /** Slot {@code index} of the open container screen's menu. */
    public static Slot slot(int index) {
        return containerScreen().getMenu().slots.get(index);
    }

    /** A left click on the center of slot {@code index}, where the document laid it out. */
    public static void clickSlot(int index) {
        double[] p = slotCenter(index);
        clickAt(p[0], p[1], 0);
    }

    /** Moves the real cursor onto slot {@code index}, as vanilla and slot mods read it during render. */
    public static void hoverSlot(int index) {
        double[] p = slotCenter(index);
        hoverAt(p[0], p[1]);
    }

    /**
     * The center of slot {@code index} on screen. The screen's origin fields are protected and Forge's
     * getters are not on the mc layer's classpath; playtests run in development, under Mojang names.
     */
    private static double[] slotCenter(int index) {
        AbstractContainerScreen<?> screen = containerScreen();
        Slot slot = slot(index);
        try {
            Field left = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field top = AbstractContainerScreen.class.getDeclaredField("topPos");
            left.setAccessible(true);
            top.setAccessible(true);
            return new double[]{left.getInt(screen) + slot.x + 8, top.getInt(screen) + slot.y + 8};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sets the cursor position (GUI pixels) vanilla hands to {@code render}, which is what hover reads. */
    public static void hoverAt(double x, double y) {
        ScreenInput.INSTANCE.pointAt(x, y);
    }

    /** A press and release of {@code button} at {@code (x, y)} on the open screen, in GUI pixels. */
    public static void clickAt(double x, double y, int button) {
        ScreenInput.INSTANCE.mouseMoved(x, y);
        ScreenInput.INSTANCE.mouseClicked(x, y, button);
        ScreenInput.INSTANCE.mouseReleased(x, y, button);
    }

    /** Runs {@code change} on the integrated server's copy of the player, on the server thread, without waiting. */
    private static void onServer(Consumer<ServerPlayer> change) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> change.accept(server.getPlayerList().getPlayer(id)));
    }
}
