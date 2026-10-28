package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.runtime.UiDocument;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A full screen drawn and driven by one document. Mouse and keyboard go to the document first; input
 * it does not take falls back to {@link Screen} (Escape closes). Closing the screen closes the view
 * model, so late replies and subscriptions end with it. An {@code <item>} or a {@code title} under the
 * pointer shows a vanilla tooltip ({@link Tooltips}). Does not pause a single-player game.
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
    private final ScreenDocument doc;

    /**
     * @param hostModId    namespace of the template and stylesheets
     * @param templatePath template path under {@code assets/<hostModId>/}
     * @param cssPaths     stylesheet paths under {@code assets/<hostModId>/}, applied in order
     * @param model        the view model the template binds to, or {@code null}; closed with the screen
     * @throws UiLoadException outside development when the document has errors
     */
    public UiScreen(String hostModId, String templatePath, List<String> cssPaths, ViewModel model) {
        super(Component.empty());
        this.doc = new ScreenDocument(hostModId, templatePath, cssPaths, McElements.TAGS, model, () -> { });
    }

    /** The current document, for querying or driving it; {@code null} only when the first development load failed. */
    public UiDocument document() {
        return doc.document();
    }

    @Override
    protected void init() {
        doc.watch();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        doc.frame(width, height);
        doc.paint(g);
        doc.drawOverlay(g);
        doc.drawTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public void mouseMoved(double x, double y) {
        UiDocument d = doc.document();
        if (d != null) {
            d.events().mouseMoved((float) x, (float) y);
        }
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        UiDocument d = doc.document();
        return d != null && d.events().mousePressed((float) x, (float) y, button)
                || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        UiDocument d = doc.document();
        return d != null && d.events().mouseReleased((float) x, (float) y, button)
                || super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        UiDocument d = doc.document();
        return d != null && d.events().mouseScrolled((float) x, (float) y, delta)
                || super.mouseScrolled(x, y, delta);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        UiDocument d = doc.document();
        return d != null && d.events().keyPressed(key, modifiers)
                || super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        UiDocument d = doc.document();
        return d != null && d.events().keyReleased(key, modifiers)
                || super.keyReleased(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        doc.close();
    }
}
