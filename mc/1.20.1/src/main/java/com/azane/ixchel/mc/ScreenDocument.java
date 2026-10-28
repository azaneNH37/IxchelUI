package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.template.TagRegistry;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The document behind a screen: loading, development reload with an error overlay, framing and
 * painting. Screens with different vanilla base classes share it by composition.
 */
final class ScreenDocument implements AutoCloseable {
    private static final Logger LOG = LogUtils.getLogger();

    private final McResources resources;
    private final DevResources dev;
    private final String templatePath;
    private final List<String> cssPaths;
    private final TagRegistry tags;
    private final ViewModel model;
    private final Runnable onReload;
    private final McPainter painter = new McPainter(Minecraft.getInstance().font);
    private UiDocument document;
    private DisplayList displayList;
    private List<Diagnostic> loadErrors = List.of();
    private AutoCloseable watcher;
    private int logged;

    /**
     * Loads the document now.
     *
     * @param onReload runs after a development reload replaced the document
     * @throws UiLoadException outside development when the document has errors
     */
    ScreenDocument(String hostModId, String templatePath, List<String> cssPaths, TagRegistry tags, ViewModel model,
                   Runnable onReload) {
        this.dev = DevResources.forHost(hostModId);
        this.resources = new McResources(hostModId, dev);
        this.templatePath = templatePath;
        this.cssPaths = List.copyOf(cssPaths);
        this.tags = tags;
        this.model = model;
        this.onReload = onReload;
        load();
    }

    private void load() {
        try {
            document = resources.load(Minecraft.getInstance().getResourceManager(), templatePath, cssPaths, tags, model);
            displayList = null;
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

    /** Starts watching the source files in a development run; idempotent. */
    void watch() {
        if (dev != null && watcher == null) {
            List<String> watched = new ArrayList<>(cssPaths);
            watched.add(templatePath);
            watcher = dev.watch(watched, () -> {
                load();
                onReload.run();
            });
        }
    }

    /** The current document; {@code null} only when the first development load failed. */
    UiDocument document() {
        return document;
    }

    /** Brings the document up to date for a screen of the given size and logs new diagnostics. */
    DisplayList frame(int width, int height) {
        if (document == null) {
            return null;
        }
        displayList = document.frame(width, height);
        List<Diagnostic> entries = document.diagnostics().entries();
        for (; logged < entries.size(); logged++) {
            LOG.warn("Ixchel UI: {}", entries.get(logged));
        }
        return displayList;
    }

    /** Paints the display list of the last {@link #frame}; {@code <entity>} elements look at the pointer. */
    void paint(GuiGraphics g, int mouseX, int mouseY) {
        if (displayList != null) {
            painter.paint(g, displayList, mouseX, mouseY);
        }
    }

    /** The depth above everything the last {@link #paint} drew, see {@link McPainter#topZ()}. */
    float topZ() {
        return painter.topZ();
    }

    /** Draws the vanilla tooltip of the {@code <item>} or {@code title} under the pointer, above everything the document drew. */
    void drawTooltip(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (document != null) {
            Tooltips.render(g, font, document.events().hitTest(mouseX, mouseY), painter.topZ(), mouseX, mouseY);
        }
    }

    /** Draws load or runtime errors over everything in a development run. */
    void drawOverlay(GuiGraphics g) {
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

    /** Closes the view model and stops the development watcher. */
    @Override
    public void close() {
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
