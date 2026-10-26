package com.azane.ixchel.forge;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.McElements;
import com.azane.ixchel.mc.McResources;
import com.azane.ixchel.mc.UiHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.IEventBus;

import java.util.List;
import java.util.function.Supplier;

/** Forge 1.20.1 glue for host mods. */
public final class IxchelForge {
    private IxchelForge() {
    }

    /**
     * Registers a HUD layer above all vanilla layers. The document is loaded from the host's
     * resources on every resource reload (startup and F3+T), each time with a fresh view model; a
     * load error fails that reload.
     *
     * @param modBus       the host mod's event bus
     * @param hostModId    namespace of the template and stylesheets
     * @param layerId      overlay id, unique within the host mod
     * @param templatePath template path under {@code assets/<hostModId>/}
     * @param cssPaths     stylesheet paths under {@code assets/<hostModId>/}, applied in order
     * @param model        creates the view model the template binds to; may return {@code null} for
     *                     a template without bindings
     */
    public static void registerHud(IEventBus modBus, String hostModId, String layerId, String templatePath,
                                   List<String> cssPaths, Supplier<? extends ViewModel> model) {
        HudLayer layer = new HudLayer(new McResources(hostModId), templatePath, cssPaths, model);
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(layer));
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll(layerId, layer));
    }

    private static final class HudLayer implements IGuiOverlay, ResourceManagerReloadListener {
        private final McResources resources;
        private final String templatePath;
        private final List<String> cssPaths;
        private final Supplier<? extends ViewModel> model;
        private UiHud hud;

        HudLayer(McResources resources, String templatePath, List<String> cssPaths,
                 Supplier<? extends ViewModel> model) {
            this.resources = resources;
            this.templatePath = templatePath;
            this.cssPaths = List.copyOf(cssPaths);
            this.model = model;
        }

        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            hud = new UiHud(resources.load(manager, templatePath, cssPaths, McElements.TAGS, model.get()));
        }

        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
            hud.render(g);
        }
    }
}
