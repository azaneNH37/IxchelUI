package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.document.HostedDocument;
import com.azane.ixchel.mc.element.McElements;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A full screen drawn and driven by one document. Mouse and keyboard go to the document first; input
 * it does not take falls back to {@link Screen} (Escape closes). Closing the screen closes the view
 * model, so late replies and subscriptions end with it. An {@code <mc-item>} or a {@code title} under the
 * pointer shows a vanilla tooltip. Does not pause a single-player game.
 *
 * <pre>{@code
 * mc.setScreen(new UiScreen(new ResourceLocation(MODID, "ui/waypoints/list.html"), new WaypointsView()));
 * }</pre>
 *
 * <p>It needs no world: opened from the title screen or Forge's mod list ({@code Minecraft.level} and
 * {@code player} are {@code null}) it draws vanilla's menu background instead of the in-game shade,
 * and item tooltips are those without a player. Given a parent screen, closing returns to it, as a
 * {@code ConfigScreenFactory} screen must:
 *
 * <pre>{@code
 * context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
 *         new ConfigScreenHandler.ConfigScreenFactory((mc, modList) -> new UiScreen(template, new ConfigView(), modList)));
 * }</pre>
 *
 * <p>In a development run the files are read from the source tree and reloaded on save, keeping the
 * view model and its state (see {@link HostedDocument}).
 */
public class UiScreen extends Screen {
    private final HostedDocument doc;
    private final Screen parent;

    /**
     * A screen that closes to the game (or to the title screen without a world).
     *
     * @param template the template under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     * @param model    the view model the template binds to, or {@code null}; closed with the screen
     * @throws UiLoadException outside development when the document has errors
     */
    public UiScreen(ResourceLocation template, ViewModel model) {
        this(template, model, null);
    }

    /**
     * A screen that closes to {@code parent}.
     *
     * @param template the template under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     * @param model    the view model the template binds to, or {@code null}; closed with the screen
     * @param parent   the screen {@link #onClose} returns to, such as the mod list; {@code null} closes
     *                 as the two-argument constructor does
     * @throws UiLoadException outside development when the document has errors
     */
    public UiScreen(ResourceLocation template, ViewModel model, Screen parent) {
        super(Component.empty());
        this.doc = new HostedDocument(template, McElements.TAGS, model, () -> { });
        this.parent = parent;
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
        doc.paint(g, mouseX, mouseY);
        doc.drawOverlay(g);
        doc.drawTooltip(g, mouseX, mouseY);
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
        return d != null && d.events().mousePressed((float) x, (float) y, button, Modifiers.held())
                || super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        UiDocument d = doc.document();
        return d != null && d.events().mouseReleased((float) x, (float) y, button, Modifiers.held())
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
    public boolean charTyped(char c, int modifiers) {
        UiDocument d = doc.document();
        return d != null && SharedConstants.isAllowedChatCharacter(c) && d.events().charTyped(c, modifiers)
                || super.charTyped(c, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Returns to the parent screen when there is one; otherwise closes as any screen does. */
    @Override
    public void onClose() {
        if (parent != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        doc.close();
    }
}
