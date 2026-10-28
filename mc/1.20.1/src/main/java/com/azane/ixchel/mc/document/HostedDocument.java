package com.azane.ixchel.mc.document;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.element.McElements;
import com.azane.ixchel.mc.element.Tooltips;
import com.azane.ixchel.mc.paint.McPainter;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.List;

/**
 * A document as every host (screen, container screen, HUD) runs it: loaded from the host mod's
 * resources, reloaded on demand, framed and painted with the native elements, with the vanilla
 * tooltip of what is under the pointer. The view model lives as long as this object and is kept
 * across reloads.
 *
 * <p>In a development run with {@code -Dixchel.devResources.<hostModId>=<resource root>} the files
 * are read from the source tree and, once {@link #watch watched}, reloaded on save; a failed load
 * then keeps the last good document and lists its errors in an overlay instead of throwing.
 */
public final class HostedDocument implements AutoCloseable {
    private static final Logger LOG = LogUtils.getLogger();

    private final McResources resources;
    private final DevResources dev;
    private final String templatePath;
    private final TagRegistry tags;
    private final ViewModel model;
    private final Runnable onReload;
    private final McPainter painter = new McPainter(Minecraft.getInstance().font, McElements.PAINTERS);
    private UiDocument document;
    private List<String> files;
    private DisplayList displayList;
    private List<Diagnostic> loadErrors = List.of();
    private DevResources.Watcher watcher;
    private int logged;

    /**
     * Loads the document now.
     *
     * @param template the template; its namespace is the host mod id, its stylesheets are {@code <link>}ed
     * @param tags     the elements the template may use, such as {@link McElements#TAGS}
     * @param model    the view model the template binds to, or {@code null}; closed by {@link #close}
     * @param onReload runs after a later load replaced the document
     * @throws UiLoadException outside development when the document has errors
     */
    public HostedDocument(ResourceLocation template, TagRegistry tags, ViewModel model, Runnable onReload) {
        this.dev = DevResources.forHost(template.getNamespace());
        this.resources = new McResources(template.getNamespace(), dev);
        this.templatePath = template.getPath();
        this.tags = tags;
        this.model = model;
        this.onReload = onReload;
        load();
    }

    /**
     * Loads the document again from the current resources, as after a resource reload.
     *
     * @throws UiLoadException outside development when the document has errors
     */
    public void reload() {
        if (load()) {
            onReload.run();
        }
    }

    private boolean load() {
        try {
            McResources.Loaded loaded = resources.load(Minecraft.getInstance().getResourceManager(), templatePath, tags, model);
            document = loaded.document();
            files = loaded.files();
            displayList = null;
            loadErrors = List.of();
            logged = 0;
            if (watcher != null) {
                watcher.watch(files);
            }
            return true;
        } catch (UiLoadException e) {
            if (dev == null) {
                throw e;
            }
            LOG.error("Ixchel UI: {} failed to load, keeping the previous version\n{}", templatePath, e.getMessage());
            loadErrors = e.diagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).toList();
            return false;
        }
    }

    /** Starts reloading on save in a development run; idempotent. The watched set follows each successful load. */
    public void watch() {
        if (dev != null && watcher == null) {
            watcher = dev.watcher(this::reload);
            watcher.watch(files == null ? List.of(templatePath) : files);
        }
    }

    /** The current document; {@code null} only when the first development load failed. */
    public UiDocument document() {
        return document;
    }

    /** Brings the document up to date for a screen of the given size and logs new diagnostics; {@code null} without a document. */
    public DisplayList frame(int width, int height) {
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

    /** Paints the display list of the last {@link #frame}; native elements react to the pointer, NaN in a HUD. */
    public void paint(GuiGraphics g, float pointerX, float pointerY) {
        if (displayList != null) {
            painter.paint(g, displayList, pointerX, pointerY);
        }
    }

    /** The depth above everything the last {@link #paint} drew. */
    public float topZ() {
        return painter.topZ();
    }

    /** Draws the vanilla tooltip of the {@code <item>} or {@code title} under the pointer, above everything the document drew. */
    public void drawTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (document != null) {
            Tooltips.render(g, Minecraft.getInstance().font, document.events().hitTest(mouseX, mouseY), painter.topZ(),
                    mouseX, mouseY);
        }
    }

    /** Draws load or runtime errors over everything in a development run. */
    public void drawOverlay(GuiGraphics g) {
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
            } catch (RuntimeException e) {
                LOG.warn("Ixchel UI: stopping the dev watcher failed", e);
            }
            watcher = null;
        }
    }
}
