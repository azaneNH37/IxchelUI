package com.azane.ixchel.mc.document;

import com.azane.ixchel.core.binding.Feed;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.Diagnostic;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.event.ClickSound;
import com.azane.ixchel.core.event.Clipboard;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.runtime.UiHosting;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.mc.element.McElements;
import com.azane.ixchel.mc.element.Tooltips;
import com.azane.ixchel.mc.paint.McPainter;
import com.azane.ixchel.mc.paint.NativePainter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A document as every host (screen, container screen, HUD) runs it: loaded from the host mod's
 * resources, reloaded on demand, framed and painted with the native elements, with the tooltip of
 * what is under the pointer, the system clipboard for its inputs and vanilla's click sound for its
 * controls. The view model lives as long as this object and is kept across reloads.
 *
 * <p>In a development run with {@code -Dixchel.devResources.<hostModId>=<resource root>} the files
 * are read from the source tree and, once {@link #watch watched}, reloaded on save; a failed load
 * then keeps the last good document and lists its errors in an overlay instead of throwing.
 */
public final class HostedDocument implements AutoCloseable {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Clipboard CLIPBOARD = new Clipboard() {
        @Override
        public String get() {
            return Minecraft.getInstance().keyboardHandler.getClipboard();
        }

        @Override
        public void set(String text) {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        }
    };
    /** Vanilla's button sound, as {@code AbstractWidget.playDownSound} plays it. */
    private static final ClickSound CLICK_SOUND = () -> Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

    private final McResources resources;
    private final DevResources dev;
    private final String templatePath;
    private final TagRegistry tags;
    private final Map<String, NativePainter<?>> painters;
    private final UiHosting hosting;
    private final ViewModel model;
    private final Runnable onReload;
    private final McPainter painter;
    /** Draws the {@code <ix-tooltip>} above the document, after the host's own drawing. */
    private final McPainter tooltipPainter;
    private UiDocument document;
    private List<String> files;
    private DisplayList displayList;
    /** The element whose tooltip shows, picked by the last {@link #frame}; {@code null} for none. */
    private Element tooltipTarget;
    private List<Diagnostic> loadErrors = List.of();
    private DevResources.Watcher watcher;
    private int logged;

    static {
        // Counts while no level is loaded too; it stands still while the game is paused, as the game does.
        Feed.tickClock(() -> Minecraft.getInstance().gui.getGuiTicks());
    }

    /**
     * Loads the document now. In a development run, warns when {@code model}'s class does not declare
     * {@code template} with {@link UiTemplate}: the lint then skips the template's binding checks.
     *
     * @param template the template; its namespace is the host mod id, its stylesheets are {@code <link>}ed
     * @param tags     the elements the template may use, such as {@link McElements#TAGS}; the host's
     *                 own, {@linkplain McElements#register registered} under its mod id, are added to them
     * @param hosting  the library and the host mod's Java components
     * @param model    the view model the template binds to, or {@code null}; closed by {@link #close}
     * @param onReload runs after a later load replaced the document
     * @throws UiLoadException outside development when the document has errors
     */
    public HostedDocument(ResourceLocation template, TagRegistry tags, UiHosting hosting, ViewModel model, Runnable onReload) {
        this(template, tags, Map.of(), hosting, model, onReload);
    }

    /**
     * As {@link #HostedDocument(ResourceLocation, TagRegistry, UiHosting, ViewModel, Runnable)}, with painters for
     * elements of {@code tags} that declare no payload but get one from the host, by tag.
     */
    public HostedDocument(ResourceLocation template, TagRegistry tags, Map<String, NativePainter<?>> own, UiHosting hosting,
                          ViewModel model, Runnable onReload) {
        this.dev = DevResources.forHost(template.getNamespace());
        this.resources = new McResources(template.getNamespace(), dev);
        this.templatePath = template.getPath();
        McElements.Hosted hosted = McElements.forHost(template.getNamespace(), tags);
        this.tags = hosted.tags();
        Map<String, NativePainter<?>> all = new HashMap<>(hosted.painters());
        all.putAll(own);
        this.painters = all;
        this.painter = new McPainter(template.getNamespace(), Minecraft.getInstance().font, this.tags, painters);
        this.tooltipPainter = new McPainter(template.getNamespace(), Minecraft.getInstance().font, this.tags, painters);
        this.hosting = hosting;
        this.model = model;
        this.onReload = onReload;
        if (dev != null && model != null && !UiTemplate.Ids.covers(model.getClass(), template.toString())) {
            LOG.warn("Ixchel UI: {} is not declared in the @UiTemplate of {}; the lint skips its binding checks",
                    template, model.getClass().getName());
        }
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
            McResources.Loaded loaded = resources.load(Minecraft.getInstance().getResourceManager(), templatePath, tags, model,
                    hosting);
            Diagnostics compiled = new Diagnostics();
            painter.compileEffects(loaded.document().effects().all(), compiled);
            if (compiled.errorCount() > 0) {
                loaded.document().close();
                compiled.throwIfErrors();
            }
            if (document != null) {
                document.close();
            }
            document = loaded.document();
            document.events().setClipboard(CLIPBOARD);
            document.events().setClickSound(CLICK_SOUND);
            files = loaded.files();
            displayList = null;
            tooltipTarget = null;
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

    /**
     * Brings the document up to date for a screen of the given size and the pointer, NaN in a HUD, and
     * logs new diagnostics; {@code null} without a document. Picks the tooltip of what is under the
     * pointer before the frame, so the frame it shows in reads its bindings and lays it out.
     */
    public DisplayList frame(int width, int height, float pointerX, float pointerY) {
        if (document == null) {
            return null;
        }
        // While an element is dragged its preview takes the pointer: no tooltip, not even vanilla's.
        tooltipTarget = Float.isNaN(pointerX) || document.events().dragSource() != null ? null
                : Tooltips.target(document.events().hitTest(pointerX, pointerY), painters);
        document.setTooltip(tooltipTarget == null ? null : Tooltips.tooltipOf(tooltipTarget), pointerX, pointerY);
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
            painter.paint(g, displayList, pointerX, pointerY, document.frameTime());
        }
    }

    /** The depth above everything the last {@link #paint} drew. */
    public float topZ() {
        return painter.topZ();
    }

    /**
     * Draws what the last {@link #frame} shows at the pointer above everything the document drew: the
     * drag preview of a dragged element, the shown {@code <ix-tooltip>}, or the vanilla tooltip of the
     * native element's painter or the {@code title}.
     */
    public void drawTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (document == null) {
            return;
        }
        if (document.topLayer().atPointer() == null) {
            if (tooltipTarget != null) {
                Tooltips.renderVanilla(g, Minecraft.getInstance().font, tooltipTarget, painters, painter.topZ(), mouseX, mouseY);
            }
            return;
        }
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0, 0, painter.topZ() + Tooltips.Z);
        tooltipPainter.paint(g, document.atPointerDisplayList(), Float.NaN, Float.NaN, document.frameTime());
        ps.popPose();
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

    /** Closes the document, the view model and stops the development watcher. */
    @Override
    public void close() {
        if (document != null) {
            document.close();
        }
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
