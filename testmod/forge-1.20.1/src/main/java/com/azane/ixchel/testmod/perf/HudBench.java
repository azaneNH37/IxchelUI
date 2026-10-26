package com.azane.ixchel.testmod.perf;

import com.azane.ixchel.testmod.AmmoHudView;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.util.Arrays;

/**
 * M2 frame-time comparison. J switches between the Ixchel ammo HUD and {@link #renderVanilla}, a
 * hand-written equivalent with the same primitives (held item, two images, one bar, four strings, one
 * of them doubled) drawn with {@link GuiGraphics} calls. The CPU time of the active layer is measured
 * from its overlay Pre to Post event and logged every {@value #WINDOW} frames.
 */
public final class HudBench {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int WINDOW = 600;
    private static final ResourceLocation IXCHEL = new ResourceLocation("ixcheltest", "ammo_hud");
    private static final ResourceLocation VANILLA = new ResourceLocation("ixcheltest", "vanilla_hud");
    private static final ResourceLocation GUN = new ResourceLocation("ixcheltest", "textures/hud/gun.png");
    private static final ResourceLocation FIRE_MODE = new ResourceLocation("ixcheltest", "textures/hud/fire_mode_auto.png");
    private static final KeyMapping SWITCH =
            new KeyMapping("key.ixcheltest.hud_bench", GLFW.GLFW_KEY_J, "key.categories.misc");

    /** Set by {@code -Pbench}: cycle the layers unattended, screenshot each window, then quit. */
    private static final boolean AUTO = "auto".equals(System.getProperty("ixcheltest.bench"));
    /** Windows in auto mode: one warm-up, then ixchel/vanilla alternating. */
    private static final int AUTO_WINDOWS = 5;

    private static boolean vanilla;
    private static long start;
    private static final long[] samples = new long[WINDOW];
    private static int count;
    private static int windows;
    private static boolean stocked;

    private HudBench() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(SWITCH));
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll(VANILLA.getPath(), (gui, g, pt, w, h) -> renderVanilla(g)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            while (e.phase == TickEvent.Phase.END && SWITCH.consumeClick()) {
                vanilla = !vanilla;
                count = 0;
                LOG.info("[ixchel-bench] showing {}", vanilla ? "vanilla" : "ixchel");
            }
            Minecraft mc = Minecraft.getInstance();
            if (AUTO && !stocked && mc.player != null) {
                // Client-side only: the HUD reads the local inventory, the server never needs to agree.
                mc.player.getInventory().setItem(mc.player.getInventory().selected, new ItemStack(Items.ARROW, 30));
                mc.player.getInventory().setItem(20, new ItemStack(Items.ARROW, 64));
                stocked = true;
            }
            if (AUTO && windows >= AUTO_WINDOWS) {
                LOG.info("[ixchel-bench] done");
                mc.stop();
            }
        });
        // Lowest priority so the timed span excludes other listeners of the same event.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (RenderGuiOverlayEvent.Pre e) -> {
            ResourceLocation id = e.getOverlay().id();
            if (id.equals(IXCHEL) && vanilla || id.equals(VANILLA) && !vanilla) {
                e.setCanceled(true);
            } else if (id.equals(IXCHEL) || id.equals(VANILLA)) {
                start = System.nanoTime();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (RenderGuiOverlayEvent.Post e) -> {
            ResourceLocation id = e.getOverlay().id();
            if (id.equals(IXCHEL) || id.equals(VANILLA)) {
                record(System.nanoTime() - start);
            }
        });
    }

    private static void record(long nanos) {
        samples[count++] = nanos;
        if (count < WINDOW) {
            return;
        }
        count = 0;
        long[] sorted = samples.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElse(0);
        String mode = vanilla ? "vanilla" : "ixchel";
        LOG.info("[ixchel-bench] {} over {} frames: median {} us, p95 {} us, mean {} us{}",
                mode, WINDOW, us(sorted[WINDOW / 2]), us(sorted[WINDOW * 95 / 100]), us(mean),
                AUTO && windows == 0 ? " (warm-up)" : "");
        if (AUTO) {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, "bench-" + windows + "-" + mode + ".png", mc.getMainRenderTarget(),
                    msg -> { });
            windows++;
            if (windows > 1) {
                vanilla = !vanilla;
            }
        }
    }

    private static String us(double nanos) {
        return String.format("%.1f", nanos / 1000);
    }

    /** Same content as the Ixchel HUD at fixed offsets from the bottom-right corner. */
    private static void renderVanilla(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Font font = mc.font;
        ItemStack gun = mc.player.getMainHandItem();
        int x = g.guiWidth() - 170;
        int y = g.guiHeight() - 53;
        g.renderItem(gun, x, y);
        g.blit(GUN, x + 20, y, 0, 0, 39, 13, 39, 13);
        g.fill(x + 62, y, x + 63, y + 18, 0xFFFFFFFF);
        int loaded = gun.getCount();
        g.pose().pushPose();
        g.pose().translate(x + 67, y, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, String.format("%03d", loaded), 0, 0, loaded == 0 ? 0xFFFF5555 : 0xFFFFFFFF, false);
        g.pose().popPose();
        g.drawString(font, String.format("%04d", AmmoHudView.reserve(gun, mc.player.getInventory().items)), x + 105, y, 0xFFAAAAAA, false);
        g.blit(FIRE_MODE, x + 105, y + 9, 0, 0, 10, 10, 10, 10);
        g.drawString(font, gun.getHoverName().getString(), x + 67, y + 19, 0xFFAAAAAA, false);
    }
}
