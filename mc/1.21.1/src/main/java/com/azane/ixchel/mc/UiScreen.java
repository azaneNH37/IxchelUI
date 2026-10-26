package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.runtime.UiDocument;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A full screen drawn and driven by one document. Mouse and keyboard go to the document first; input
 * it does not take falls back to {@link Screen} (Escape closes). Closing the screen closes the view
 * model, so late replies and subscriptions end with it. Does not pause a single-player game.
 *
 * <pre>{@code
 * mc.setScreen(new UiScreen(MODID, "ui/refit.html", List.of("ui/refit.css"), new RefitView(slot)));
 * }</pre>
 *
 * <p>In a development run with {@code -Dixchel.devResources.<hostModId>=<resource root>} the files
 * are read from the source tree and reloaded on save, keeping the view model and its state; a failed
 * reload keeps the last good document and lists the errors in an overlay.
 */
public class UiScreen extends Screen {
    private static final Logger LOG = LogUtils.getLogger();

    private final McResources resources;
    private final DevResources dev;
    private final String templatePath;
    private final List<String> cssPaths;
    private final ViewModel model;
    private final McPainter painter = new McPainter(Minecraft.getInstance().font);
    private UiDocument document;
    private List<Diagnostic> loadErrors = List.of();
    private AutoCloseable watcher;
    private int logged;

    /**
     * @param hostModId    namespace of the template and stylesheets
     * @param templatePath template path under {@code assets/<hostModId>/}
     * @param cssPaths     stylesheet paths under {@code assets/<hostModId>/}, applied in order
     * @param model        the view model the template binds to, or {@code null}; closed with the screen
     * @throws UiLoadException outside development when the document has errors
     */
    public UiScreen(String hostModId, String templatePath, List<String> cssPaths, ViewModel model) {
        super(Component.empty());
        this.dev = DevResources.forHost(hostModId);
        this.resources = new McResources(hostModId, dev);
        this.templatePath = templatePath;
        this.cssPaths = List.copyOf(cssPaths);
        this.model = model;
        load();
    }

    private void load() {
        try {
            document = resources.load(Minecraft.getInstance().getResourceManager(), templatePath, cssPaths,
                    McElements.TAGS, model);
            loadErrors = List.of();
            logged = 0;
        } catch (UiLoadException e) {
            if (dev == null) {
                throw e;
            }
            LOG.error("Ixchel UI: {} failed to load, keeping the previous version\n{}", templatePath, e.getMessage());
            loadErrors = e.diagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
        }
    }

    @Override
    protected void init() {
        if (dev != null && watcher == null) {
            List<String> watched = new ArrayList<>(cssPaths);
            watched.add(templatePath);
            watcher = dev.watch(watched, this::load);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        if (document != null) {
            painter.paint(g, document.frame(width, height));
            List<Diagnostic> entries = document.diagnostics().entries();
            for (; logged < entries.size(); logged++) {
                LOG.warn("Ixchel UI: {}", entries.get(logged));
            }
        }
        if (dev != null) {
            DevOverlay.draw(g, loadErrors.isEmpty() ? runtimeErrors() : loadErrors);
        }
    }

    private List<Diagnostic> runtimeErrors() {
        if (document == null || document.diagnostics().errorCount() == 0) {
            return List.of();
        }
        return document.diagnostics().entries().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (document != null) {
            document.events().mouseMoved((float) x, (float) y);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return document != null && document.events().mousePressed((float) x, (float) y, button)
                || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return document != null && document.events().mouseReleased((float) x, (float) y, button)
                || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return document != null && document.events().mouseScrolled((float) x, (float) y, scrollY)
                || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return document != null && document.events().keyPressed(key, modifiers)
                || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        return document != null && document.events().keyReleased(key, modifiers)
                || super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (model != null) {
            model.close();
        }
        if (watcher != null) {
            try {
                watcher.close();
            } catch (Exception e) {
                LOG.warn("Ixchel UI: stopping the dev watcher failed", e);
            }
            watcher = null;
        }
    }
}
