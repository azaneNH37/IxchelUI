package com.azane.ixchel.neoforge;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.source.WorldProjection;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Supplier;

/** NeoForge 1.21.1 glue for host mods: registers the mc layer's hosts with NeoForge. */
public final class IxchelNeoForge {
    private static boolean worldProjectionInstalled;

    private IxchelNeoForge() {
    }

    /**
     * Registers a {@link UiHud} of the template {@code type} declares with
     * {@link com.azane.ixchel.core.binding.UiTemplate} as a GUI layer above all vanilla layers and as a
     * resource reload listener: the document loads at startup and again on F3+T, keeping its view
     * model; a load error fails that reload. The first call also installs the listener that lets
     * {@link WorldProjection} see the camera.
     *
     * <pre>{@code
     * UiHud hud = IxchelNeoForge.registerHud(modBus, AmmoHudView.class, AmmoHudView::new);
     * }</pre>
     *
     * The layer id is {@code <namespace>:<template path without .html>} ({@code mymod:ui/env/hud}).
     *
     * @param modBus the host mod's event bus
     * @param type   the view model class; it declares exactly one concrete template
     * @param model  creates the view model on the first load
     * @throws IllegalArgumentException if {@code type} does not declare exactly one concrete template
     */
    public static <V extends ViewModel> UiHud registerHud(IEventBus modBus, Class<V> type, Supplier<? extends V> model) {
        return register(modBus, new UiHud(type, model));
    }

    /**
     * As {@link #registerHud(IEventBus, Class, Supplier)}, for an explicit template.
     *
     * @param modBus   the host mod's event bus
     * @param template the template under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements. The layer id is
     *                 {@code <namespace>:<path without .html>} ({@code mymod:ui/env/hud})
     * @param model    creates the view model on the first load; may return {@code null} for a
     *                 template without bindings
     */
    public static UiHud registerHud(IEventBus modBus, ResourceLocation template, Supplier<? extends ViewModel> model) {
        return register(modBus, new UiHud(template, model));
    }

    /**
     * As {@link #registerHud(IEventBus, Class, Supplier)}, for a template without a view model.
     *
     * @param modBus   the host mod's event bus
     * @param template the template under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements. The layer id is
     *                 {@code <namespace>:<path without .html>} ({@code mymod:ui/env/hud})
     */
    public static UiHud registerHud(IEventBus modBus, ResourceLocation template) {
        return register(modBus, new UiHud(template));
    }

    private static UiHud register(IEventBus modBus, UiHud hud) {
        ResourceLocation template = hud.template();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(template.getNamespace(), layerId(template.getPath()));
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) manager -> hud.reload()));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAboveAll(id, (LayeredDraw.Layer) (g, deltaTracker) -> hud.render(g)));
        installWorldProjection();
        return hud;
    }

    static String layerId(String templatePath) {
        return templatePath.endsWith(".html") ? templatePath.substring(0, templatePath.length() - 5) : templatePath;
    }

    /** Lets {@link WorldProjection} see the camera every frame the level is drawn; once per library copy. */
    private static void installWorldProjection() {
        if (worldProjectionInstalled) {
            return;
        }
        worldProjectionInstalled = true;
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            // After the sky: fired once per level render, sky drawn or not, with the final matrices.
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                WorldProjection.capture(e.getModelViewMatrix(), e.getProjectionMatrix(),
                        e.getCamera().getPosition(), e.getCamera().getPartialTickTime());
            }
        });
    }
}
