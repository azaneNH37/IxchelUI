package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.document.HostedDocument;
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
 * @UiView("ixchel_sample:ui/env/hud.html")
 * class EnvModel extends ViewModel { ... }
 *
 * UiHud hud = IxchelForge.registerHud(modBus, EnvModel.class, EnvModel::new);
 * hud.show(false);
 * }</pre>
 */
public final class UiHud {
    private final ResourceLocation page;
    private final Supplier<? extends ViewModel> model;
    private HostedDocument doc;
    private boolean shown = true;

    /**
     * A HUD of the page {@code type} declares with {@link UiView}.
     *
     * @param type  the view model class; it declares exactly one concrete page
     * @param model creates the view model on the first load
     * @throws IllegalArgumentException if {@code type} does not declare exactly one concrete page
     */
    public <V extends ViewModel> UiHud(Class<V> type, Supplier<? extends V> model) {
        this(DeclaredView.of(type), model);
    }

    /**
     * A HUD of a page without a view model.
     *
     * @param page the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     */
    public UiHud(ResourceLocation page) {
        this(page, () -> null);
    }

    /**
     * A HUD of an explicit page.
     *
     * @param page the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     * @param model    creates the view model on the first load; may return {@code null} for a page
     *                 without bindings
     */
    public UiHud(ResourceLocation page, Supplier<? extends ViewModel> model) {
        this.page = page;
        this.model = model;
    }

    /** The page; the loader layer derives the layer id from it. */
    public ResourceLocation page() {
        return page;
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
                doc = new HostedDocument(page, m, () -> { });
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

    /** Whether the layer is shown; a hidden layer, once its exit ended, does no work, not even polling its sources. */
    public boolean shown() {
        return shown;
    }

    /**
     * Shows or hides the layer; returns this, so {@code registerHud(...).show(false)} starts hidden.
     * With {@code :-ix-absent} rules the document animates in and out: hiding stops its bindings and
     * draws it until the exit ended; showing again takes it back from where it got to.
     */
    public UiHud show(boolean shown) {
        if (shown != this.shown) {
            this.shown = shown;
            UiDocument d = document();
            if (d != null) {
                if (shown) {
                    d.enter();
                } else {
                    d.exit(() -> { });
                }
            }
        }
        return this;
    }

    /** The current document, for querying or driving it; {@code null} before the first load. */
    public UiDocument document() {
        return doc == null ? null : doc.document();
    }

    public void render(GuiGraphics g) {
        if (doc == null || !shown && !exiting()) {
            return;
        }
        FrameTimer.start(this);
        doc.frame(g.guiWidth(), g.guiHeight(), Float.NaN, Float.NaN);
        if (shown || exiting()) {
            doc.paint(g, Float.NaN, Float.NaN);
            doc.drawOverlay(g);
        }
        FrameTimer.stop(this);
    }

    private boolean exiting() {
        UiDocument d = document();
        return d != null && d.exiting();
    }
}
