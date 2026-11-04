package com.azane.ixchel.forge;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.document.ExitingDocuments;
import com.azane.ixchel.mc.playtest.Playtests;
import com.azane.ixchel.mc.source.WorldProjection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Forge 1.20.1 glue for host mods: registers the mc layer's hosts with Forge and drives its playtests. */
public final class IxchelForge {
    private static boolean worldProjectionInstalled;
    private static boolean screenExitsInstalled;

    private IxchelForge() {
    }

    /**
     * Registers a {@link UiHud} of the page {@code type} declares with
     * {@link com.azane.ixchel.core.binding.UiView} as a HUD layer above all vanilla layers and as a
     * resource reload listener: the document loads at startup and again on F3+T, keeping its view
     * model; a load error fails that reload. The first call also installs the listener that lets
     * {@link WorldProjection} see the camera.
     *
     * <pre>{@code
     * UiHud hud = IxchelForge.registerHud(modBus, EnvModel.class, EnvModel::new);
     * }</pre>
     *
     * The overlay id is the page path without {@code .html} ({@code ui/env/hud}), unique within
     * the host mod.
     *
     * @param modBus the host mod's event bus
     * @param type   the view model class; it declares exactly one concrete page
     * @param model  creates the view model on the first load
     * @throws IllegalArgumentException if {@code type} does not declare exactly one concrete page
     */
    public static <V extends ViewModel> UiHud registerHud(IEventBus modBus, Class<V> type, Supplier<? extends V> model) {
        return register(modBus, new UiHud(type, model));
    }

    /**
     * As {@link #registerHud(IEventBus, Class, Supplier)}, for an explicit page.
     *
     * @param modBus   the host mod's event bus
     * @param page the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements. The overlay id is the
     *                 path without {@code .html} ({@code ui/env/hud}), unique within the host mod
     * @param model    creates the view model on the first load; may return {@code null} for a
     *                 page without bindings
     */
    public static UiHud registerHud(IEventBus modBus, ResourceLocation page, Supplier<? extends ViewModel> model) {
        return register(modBus, new UiHud(page, model));
    }

    /**
     * As {@link #registerHud(IEventBus, Class, Supplier)}, for a page without a view model.
     *
     * @param modBus   the host mod's event bus
     * @param page the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements. The overlay id is the
     *                 path without {@code .html} ({@code ui/env/hud}), unique within the host mod
     */
    public static UiHud registerHud(IEventBus modBus, ResourceLocation page) {
        return register(modBus, new UiHud(page));
    }

    private static UiHud register(IEventBus modBus, UiHud hud) {
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) manager -> hud.reload()));
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll(layerId(hud.page().getPath()),
                (IGuiOverlay) (gui, g, partialTick, width, height) -> hud.render(g)));
        installWorldProjection();
        installScreenExits();
        return hud;
    }

    /**
     * Lets closed {@code UiScreen}s and {@code UiContainerScreen}s play their exit: what is left of
     * their documents draws over the game and over the next screen. Call once from the client side of
     * the host's setup; {@code registerHud} includes it, and without it screens close at once.
     */
    public static void installScreenExits() {
        if (screenExitsInstalled) {
            return;
        }
        screenExitsInstalled = true;
        ExitingDocuments.hooked();
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> ExitingDocuments.drawOverGame(e.getGuiGraphics()));
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e) -> ExitingDocuments.drawOverScreen(e.getGuiGraphics()));
    }

    /**
     * Installs the host's in-game playtests ({@link Playtests}): with {@code -Dixchel.playtest.<modid>=<name>[,…]|all}
     * a development client runs them unattended and quits; without it, nothing is registered. Call
     * once from the client side of the host's constructor, typically in a development-only mod.
     *
     * <pre>{@code
     * IxchelForge.installPlaytests("examplemod", playtests -> playtests.resetPlayer().register("panel", PanelPlaytest::run));
     * }</pre>
     */
    public static void installPlaytests(String modid, Consumer<Playtests> playtests) {
        Playtests.install(modid, IxchelForge::pump, playtests);
    }

    /** Calls {@code frame} at the end of each client tick that follows a drawn frame. */
    private static void pump(Runnable frame) {
        boolean[] drawn = {false};
        MinecraftForge.EVENT_BUS.addListener((TickEvent.RenderTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                drawn[0] = true;
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END && drawn[0]) {
                drawn[0] = false;
                frame.run();
            }
        });
    }

    static String layerId(String pagePath) {
        return pagePath.endsWith(".html") ? pagePath.substring(0, pagePath.length() - 5) : pagePath;
    }

    /** Lets {@link WorldProjection} see the camera every frame the level is drawn; once per library copy. */
    private static void installWorldProjection() {
        if (worldProjectionInstalled) {
            return;
        }
        worldProjectionInstalled = true;
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            // After the sky: fired once per level render, sky drawn or not, with the final matrices.
            // AFTER_LEVEL would not do: it is handed the projection stack instead of the camera pose.
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                WorldProjection.capture(e.getPoseStack().last().pose(), e.getProjectionMatrix(),
                        e.getCamera().getPosition(), e.getPartialTick());
            }
        });
    }

}
