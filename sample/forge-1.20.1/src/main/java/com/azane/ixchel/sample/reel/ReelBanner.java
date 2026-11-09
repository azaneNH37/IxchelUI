package com.azane.ixchel.sample.reel;

import com.azane.ixchel.mc.UiHud;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * The demo reel's title card: a banner that drops in at the top of the screen naming the sample that
 * follows, then leaves by itself. Unlike a HUD layer it draws over screens too, after the HUD when no
 * screen is open and after the screen otherwise, so a recording that moves between screens and HUDs
 * shows it over both.
 */
public final class ReelBanner {
    private static final ReelBannerModel MODEL = new ReelBannerModel();
    private static UiHud hud;

    private ReelBanner() {
    }

    /** Client only; from client setup. */
    public static void register(IEventBus modBus) {
        hud = new UiHud(ReelBannerModel.class, () -> MODEL);
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) manager -> hud.reload()));
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
            if (Minecraft.getInstance().screen == null) {
                hud.render(e.getGuiGraphics());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) -> hud.render(e.getGuiGraphics()));
    }

    /**
     * Shows the card of sample {@code id} ({@code ixchel_sample.reel.<id>.title} and {@code .subtitle}),
     * number {@code n} of {@code total}, in the accent of {@code theme} (holo, amber, dark or vanilla).
     */
    public static void show(String id, int n, int total, String theme) {
        MODEL.show(id, n, total, theme);
    }

    public static UiHud hud() {
        return hud;
    }
}
