package com.azane.ixchel.testmod.refit;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * M3 comparison, run unattended with {@code -Pbench=refit}: the Ixchel refit screen against
 * {@link VanillaRefitScreen}, both open on the Scope options. Each window times {@link Screen#render}
 * from the Forge render Pre to Post event (flushed, so both include submission) and counts the bytes
 * the client thread allocated meanwhile. "idle" windows change nothing; "churn" windows change an
 * attachment count shown on screen every tick. Afterwards the server moves the crossbow out of the
 * locked slot and back, and each screen must report the target as gone and then valid again.
 */
final class RefitBench {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int WINDOW = 600;
    private static final int GUN_SLOT = 0;
    private static final int AWAY_SLOT = 8;
    private static final int CHURN_SLOT = 2;
    private static final com.sun.management.ThreadMXBean THREADS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private enum Mode { IXCHEL, VANILLA }

    private record Window(Mode mode, boolean churn, boolean warmUp) {
    }

    private record Step(int delayTicks, Runnable action) {
    }

    private static final Deque<Step> script = new ArrayDeque<>();
    private static Step next;
    private static int wait;
    private static Screen screen;
    private static RefitView view;
    private static Window measuring;
    private static final long[] nanos = new long[WINDOW];
    private static final long[] bytes = new long[WINDOW];
    private static int count;
    private static int windows;
    private static long startNanos;
    private static long startBytes;
    private static boolean churnHigh;
    private static int failures;

    private RefitBench() {
    }

    static void init() {
        if (!"refit".equals(System.getProperty("ixcheltest.bench"))) {
            return;
        }
        script.add(new Step(40, RefitBench::stock));
        script.add(new Step(20, () -> { }));
        window(Mode.IXCHEL, false, true);
        for (int round = 0; round < 2; round++) {
            for (boolean churn : new boolean[]{false, true}) {
                window(Mode.IXCHEL, churn, false);
                window(Mode.VANILLA, churn, false);
            }
        }
        for (Mode mode : Mode.values()) {
            script.add(new Step(10, () -> open(mode)));
            script.add(new Step(20, () -> moveGun(GUN_SLOT, AWAY_SLOT)));
            script.add(new Step(20, () -> expect(mode, false)));
            script.add(new Step(0, () -> moveGun(AWAY_SLOT, GUN_SLOT)));
            script.add(new Step(20, () -> expect(mode, true)));
            script.add(new Step(0, () -> Minecraft.getInstance().setScreen(null)));
        }
        MinecraftForge.EVENT_BUS.addListener(RefitBench::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ScreenEvent.Render.Pre e) -> {
            if (measuring != null && e.getScreen() == screen) {
                startBytes = THREADS.getCurrentThreadAllocatedBytes();
                startNanos = System.nanoTime();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (ScreenEvent.Render.Post e) -> {
            if (measuring != null && e.getScreen() == screen) {
                e.getGuiGraphics().flush();
                long t = System.nanoTime() - startNanos;
                record(t, THREADS.getCurrentThreadAllocatedBytes() - startBytes);
            }
        });
    }

    private static void window(Mode mode, boolean churn, boolean warmUp) {
        script.add(new Step(10, () -> open(mode)));
        script.add(new Step(20, () -> {
            count = 0;
            measuring = new Window(mode, churn, warmUp);
        }));
    }

    private static void tick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        if (measuring != null) {
            if (measuring.churn) {
                // Client side only: both screens read the local inventory.
                churnHigh = !churnHigh;
                mc.player.getInventory().setItem(CHURN_SLOT, new ItemStack(Items.GLASS_PANE, churnHigh ? 6 : 5));
            }
            return;
        }
        if (next == null) {
            next = script.poll();
            if (next == null) {
                LOG.info("[ixchel-bench] refit done, {} check(s) failed", failures);
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
        s.action.run();
    }

    private static void record(long t, long allocated) {
        nanos[count] = t;
        bytes[count] = allocated;
        if (++count < WINDOW) {
            return;
        }
        Window w = measuring;
        measuring = null;
        long[] n = nanos.clone();
        long[] b = bytes.clone();
        Arrays.sort(n);
        Arrays.sort(b);
        String name = w.mode.name().toLowerCase(Locale.ROOT) + " " + (w.churn ? "churn" : "idle");
        LOG.info("[ixchel-bench] refit {} over {} frames: median {} us, p95 {} us, mean {} us; "
                        + "allocated median {} B, mean {} B per frame{}",
                name, WINDOW, us(n[WINDOW / 2]), us(n[WINDOW * 95 / 100]), us(Arrays.stream(n).average().orElse(0)),
                b[WINDOW / 2], Math.round(Arrays.stream(b).average().orElse(0)), w.warmUp ? " (warm-up)" : "");
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "refit-" + windows++ + "-" + name.replace(' ', '-') + ".png",
                mc.getMainRenderTarget(), msg -> { });
        // The churned stack goes back to its stocked count before the next window.
        mc.player.getInventory().setItem(CHURN_SLOT, new ItemStack(Items.GLASS_PANE, 5));
    }

    private static String us(double nanos) {
        return String.format(Locale.ROOT, "%.1f", nanos / 1000);
    }

    /** Opens the screen on the gun slot with the Scope options showing. */
    private static void open(Mode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == Mode.IXCHEL) {
            view = new RefitView(GUN_SLOT);
            view.selectedType.set("scope");
            screen = RefitClient.screen(view);
            mc.setScreen(screen);
        } else {
            VanillaRefitScreen v = new VanillaRefitScreen(GUN_SLOT);
            screen = v;
            mc.setScreen(v);
            v.selectType("scope");
        }
    }

    private static void expect(Mode mode, boolean valid) {
        boolean shown = mode == Mode.IXCHEL ? view.valid.get() : ((VanillaRefitScreen) screen).valid();
        boolean pass = shown == valid && Minecraft.getInstance().screen == screen;
        if (!pass) {
            failures++;
        }
        String name = mode.name().toLowerCase(Locale.ROOT);
        LOG.info("[ixchel-bench] refit check {}: target {} -> {}", name, valid ? "back" : "moved away by the server",
                pass ? "PASS" : "FAIL (valid=" + shown + ")");
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "refit-check-" + name + "-" + (valid ? "back" : "gone") + ".png",
                mc.getMainRenderTarget(), msg -> { });
    }

    /** Fills the inventory on the server: the crossbow with a scope and red laser, attachments to install. */
    private static void stock() {
        onServer(inv -> {
            inv.clearContent();
            ItemStack gun = new ItemStack(Items.CROSSBOW);
            Attachments.setInstalled(gun, "scope", "spyglass");
            Attachments.setLaser(gun, "red");
            inv.setItem(GUN_SLOT, gun);
            inv.setItem(1, new ItemStack(Items.SPYGLASS));
            inv.setItem(CHURN_SLOT, new ItemStack(Items.GLASS_PANE, 5));
            inv.setItem(3, new ItemStack(Items.IRON_BARS, 2));
            inv.setItem(4, new ItemStack(Items.STICK, 3));
        });
        Minecraft.getInstance().player.getInventory().selected = GUN_SLOT;
    }

    private static void moveGun(int from, int to) {
        onServer(inv -> {
            inv.setItem(to, inv.getItem(from));
            inv.setItem(from, ItemStack.EMPTY);
        });
    }

    private static void onServer(Consumer<Inventory> change) {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            change.accept(p.getInventory());
        });
    }
}
