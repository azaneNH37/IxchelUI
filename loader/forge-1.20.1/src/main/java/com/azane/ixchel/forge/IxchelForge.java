package com.azane.ixchel.forge;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.mc.DevOverlay;
import com.azane.ixchel.mc.DevResources;
import com.azane.ixchel.mc.McElements;
import com.azane.ixchel.mc.McResources;
import com.azane.ixchel.mc.UiHud;
import com.azane.ixchel.mc.WorldProjection;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Forge 1.20.1 glue for host mods. */
public final class IxchelForge {
    private static final Logger LOG = LogUtils.getLogger();

    private IxchelForge() {
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
     * @param hostModId    namespace of the template and stylesheets
     * @param layerId      overlay id, unique within the host mod
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
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(layer));
        modBus.addListener((RegisterGuiOverlaysEvent e) -> e.registerAboveAll(layerId, layer));
    }

    /**
     * Lets {@link WorldProjection} see the camera every frame the level is drawn. Call once, on the
     * client only (for example from client setup); it adds a listener to the game event bus.
     */
    public static void registerWorldProjection() {
        MinecraftForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            // After the sky: fired once per level render, sky drawn or not, with the final matrices.
            // AFTER_LEVEL would not do: it is handed the projection stack instead of the camera pose.
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                WorldProjection.capture(e.getPoseStack().last().pose(), e.getProjectionMatrix(),
                        e.getCamera().getPosition(), e.getPartialTick());
            }
        });
    }

    private static final class HudLayer implements IGuiOverlay, ResourceManagerReloadListener {
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
        public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
            if (hud != null) {
                hud.render(g);
            }
            if (dev) {
                DevOverlay.draw(g, loadErrors.isEmpty() && hud != null ? hud.runtimeErrors() : loadErrors);
            }
        }
    }
}
