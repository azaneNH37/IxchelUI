package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.document.ExitingDocuments;
import com.azane.ixchel.mc.document.HostedDocument;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A full screen drawn and driven by one document. Mouse and keyboard go to the document first; input
 * it does not take falls back to {@link Screen} (Escape closes). Closing the screen closes the view
 * model, so late replies and subscriptions end with it. An {@code <mc-item>} or a {@code title} under the
 * pointer shows a vanilla tooltip. Does not pause a single-player game. A document with
 * {@code :-ix-absent} rules animates out after the screen closed, while input is back with the game;
 * the host calls the loader glue's {@code installScreenExits()} once for that.
 *
 * <p>The page is the one the view model's class declares with {@link UiView}; a model that
 * serves several pages, or none, is mounted with the page id:
 *
 * <pre>{@code
 * @UiView("ixchel_sample:ui/waypoints/list.html")
 * class WaypointsModel extends ViewModel { ... }
 *
 * mc.setScreen(new UiScreen(new WaypointsModel()));
 * mc.setScreen(new UiScreen(new ResourceLocation(MODID, "ui/guide/entries/" + page + ".html"), new GuideModel()));
 * }</pre>
 *
 * <p>It needs no world: opened from the title screen or Forge's mod list ({@code Minecraft.level} and
 * {@code player} are {@code null}) it draws vanilla's menu background instead of the in-game shade,
 * and item tooltips are those without a player. Given a parent screen, closing returns to it, as a
 * {@code ConfigScreenFactory} screen must:
 *
 * <pre>{@code
 * context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
 *         new ConfigScreenHandler.ConfigScreenFactory((mc, modList) -> new UiScreen(new ConfigModel(), modList)));
 * }</pre>
 *
 * <p>The document draws over that background, and a {@code backdrop-filter} reads it. A screen that
 * shows the game unshaded behind its own {@code backdrop-filter} overrides
 * {@code renderBackground(GuiGraphics)} to draw nothing.
 *
 * <p>In a development run the files are read from the source tree and reloaded on save, keeping the
 * view model and its state (see {@link HostedDocument}).
 */
public class UiScreen extends Screen {
    private final HostedDocument doc;
    private final Screen parent;

    /**
     * A screen of the page {@code model}'s class declares, closing to the game (or to the title
     * screen without a world).
     *
     * @param model the view model; closed with the screen
     * @throws IllegalArgumentException if the class does not declare exactly one concrete page
     * @throws UiLoadException          outside development when the document has errors
     */
    public UiScreen(ViewModel model) {
        this(model, null);
    }

    /**
     * A screen of the page {@code model}'s class declares, closing to {@code parent}.
     *
     * @param model  the view model; closed with the screen
     * @param parent the screen {@link #onClose} returns to, such as the mod list; {@code null} closes
     *               to the game
     * @throws IllegalArgumentException if the class does not declare exactly one concrete page
     * @throws UiLoadException          outside development when the document has errors
     */
    public UiScreen(ViewModel model, Screen parent) {
        this(DeclaredView.of(model.getClass()), model, parent);
    }

    /**
     * A screen of a page without a view model, closing to the game.
     *
     * @param page the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     * @throws UiLoadException outside development when the document has errors
     */
    public UiScreen(ResourceLocation page) {
        this(page, null, null);
    }

    /**
     * A screen of an explicit page, closing to the game (or to the title screen without a world).
     *
     * @param page the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     * @param model    the view model the page binds to, or {@code null}; closed with the screen
     * @throws UiLoadException outside development when the document has errors
     */
    public UiScreen(ResourceLocation page, ViewModel model) {
        this(page, model, null);
    }

    /**
     * A screen of an explicit page, closing to {@code parent}.
     *
     * @param page the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                 id, stylesheets come from its {@code <link>} elements
     * @param model    the view model the page binds to, or {@code null}; closed with the screen
     * @param parent   the screen {@link #onClose} returns to, such as the mod list; {@code null} closes
     *                 to the game
     * @throws UiLoadException outside development when the document has errors
     */
    public UiScreen(ResourceLocation page, ViewModel model, Screen parent) {
        super(Component.empty());
        this.doc = new HostedDocument(page, model, () -> { });
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
        FrameTimer.start(this);
        renderBackground(g);
        doc.frame(width, height, mouseX, mouseY);
        doc.paint(g, mouseX, mouseY);
        doc.drawOverlay(g);
        doc.drawTooltip(g, mouseX, mouseY);
        FrameTimer.stop(this);
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

    /** {@link #render} ends here: the view model closes and what is left of the document exits, over whatever shows next. */
    @Override
    public void removed() {
        doc.close();
        ExitingDocuments.add(doc);
    }
}
