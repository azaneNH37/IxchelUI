package com.azane.ixchel.neoforge;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.mc.DevOverlay;
import com.azane.ixchel.mc.DevResources;
import com.azane.ixchel.mc.McElements;
import com.azane.ixchel.mc.McResources;
import com.azane.ixchel.mc.UiHud;
import com.mojang.logging.LogUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** NeoForge 1.21.1 glue for host mods. */
public final class IxchelNeoForge {
    private static final Logger LOG = LogUtils.getLogger();

    private IxchelNeoForge() {
    }

    /**
     * Registers a HUD layer above all vanilla layers. The document is loaded from the host's
     * resources on every resource reload (startup and F3+T), each time with a fresh view model; a
     * load error fails that reload.
     *
     * <p>In a development run with {@code -Dixchel.devResources.<hostModId>=<resource root>}, the
     * template and stylesheets are read from that directory and reloaded when saved. A failed load
     * then keeps the last good document and lists the errors in an overlay instead of failing.
     *
     * @param modBus       the host mod's event bus
     * @param hostModId    namespace of the template, stylesheets and layer id
     * @param layerId      layer path, unique within the host mod
     * @param templatePath template path under {@code assets/<hostModId>/}
     * @param cssPaths     stylesheet paths under {@code assets/<hostModId>/}, applied in order
     * @param model        creates the view model the template binds to; may return {@code null} for
     *                     a template without bindings
     */
    public static void registerHud(IEventBus modBus, String hostModId, String layerId, String templatePath,
                                   List<String> cssPaths, Supplier<? extends ViewModel> model) {
        DevResources dev = FMLEnvironment.production ? null : DevResources.forHost(hostModId);
        HudLayer layer = new HudLayer(new McResources(hostModId, dev), dev != null, templatePath, cssPaths, model);
        if (dev != null) {
            List<String> watched = new ArrayList<>(cssPaths);
            watched.add(templatePath);
            dev.watch(watched, () -> layer.load(Minecraft.getInstance().getResourceManager()));
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(hostModId, layerId);
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(layer));
        modBus.addListener((RegisterGuiLayersEvent e) -> e.registerAboveAll(id, layer));
    }

    private static final class HudLayer implements LayeredDraw.Layer, ResourceManagerReloadListener {
        private final McResources resources;
        private final boolean dev;
        private final String templatePath;
        private final List<String> cssPaths;
        private final Supplier<? extends ViewModel> model;
        private UiHud hud;
        private List<Diagnostic> loadErrors = List.of();

        HudLayer(McResources resources, boolean dev, String templatePath, List<String> cssPaths,
                 Supplier<? extends ViewModel> model) {
            this.resources = resources;
            this.dev = dev;
            this.templatePath = templatePath;
            this.cssPaths = List.copyOf(cssPaths);
            this.model = model;
        }

        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            load(manager);
        }

        void load(ResourceManager manager) {
            try {
                hud = new UiHud(resources.load(manager, templatePath, cssPaths, McElements.TAGS, model.get()));
                loadErrors = List.of();
            } catch (UiLoadException e) {
                if (!dev) {
                    throw e;
                }
                LOG.error("Ixchel UI: {} failed to load, keeping the previous version\n{}", templatePath, e.getMessage());
                loadErrors = e.diagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
            }
        }

        @Override
        public void render(GuiGraphics g, DeltaTracker delta) {
            if (hud != null) {
                hud.render(g);
            }
            if (dev) {
                DevOverlay.draw(g, loadErrors.isEmpty() && hud != null ? hud.runtimeErrors() : loadErrors);
            }
        }
    }
}
