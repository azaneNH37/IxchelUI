package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.document.HostedDocument;
import com.azane.ixchel.mc.element.McElements;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

/**
 * Display-only UI over the whole GUI-scaled screen. The loader layer registers it as a HUD layer
 * that calls {@link #render}, and as a resource reload listener that calls {@link #reload}; the
 * document is loaded on the first reload (startup) and again on every later one (F3+T), keeping its
 * view model. A development run also reloads on save. Client thread only.
 *
 * <pre>{@code
 * UiHud hud = IxchelForge.registerHud(modBus, new ResourceLocation(MODID, "ui/env/hud.html"), EnvView::new);
 * hud.show(false);
 * }</pre>
 */
public final class UiHud {
    private final ResourceLocation template;
    private final Supplier<? extends ViewModel> model;
    private HostedDocument doc;
    private boolean shown = true;

    /**
     * @param template the template under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     * @param model    creates the view model on the first load; may return {@code null} for a template
     *                 without bindings
     */
    public UiHud(ResourceLocation template, Supplier<? extends ViewModel> model) {
        this.template = template;
        this.model = model;
    }

    /** The template; the loader layer derives the layer id from it. */
    public ResourceLocation template() {
        return template;
    }

    /**
     * Loads the document from the current resources.
     *
     * @throws UiLoadException outside development when the document has errors, failing that reload
     */
    public void reload() {
        if (doc == null) {
            ViewModel m = model.get();
            try {
                doc = new HostedDocument(template, McElements.TAGS, m, () -> { });
            } catch (UiLoadException e) {
                if (m != null) {
                    m.close();
                }
                throw e;
            }
            doc.watch();
        } else {
            doc.reload();
        }
    }

    /** Whether the layer is drawn; a hidden layer does no work, not even polling its sources. */
    public boolean shown() {
        return shown;
    }

    /** Shows or hides the layer; returns this, so {@code registerHud(...).show(false)} starts hidden. */
    public UiHud show(boolean shown) {
        this.shown = shown;
        return this;
    }

    /** The current document, for querying or driving it; {@code null} before the first load. */
    public UiDocument document() {
        return doc == null ? null : doc.document();
    }

    public void render(GuiGraphics g) {
        if (!shown || doc == null) {
            return;
        }
        doc.frame(g.guiWidth(), g.guiHeight());
        doc.paint(g, Float.NaN, Float.NaN);
        doc.drawOverlay(g);
    }
}
