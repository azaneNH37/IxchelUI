package com.azane.ixchel.mc;

import com.azane.ixchel.core.api.HostApi;
import com.azane.ixchel.core.binding.UiView;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.mc.container.ClippedSlotItems;
import com.azane.ixchel.mc.container.ContainerLayout;
import com.azane.ixchel.mc.container.SlotSnapshot;
import com.azane.ixchel.mc.document.ExitingDocuments;
import com.azane.ixchel.mc.document.HostedDocument;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Map;

/**
 * A container screen whose layout is a document. The host's menu defines the slots; the page
 * places them with {@code <mc-slot index="N">} elements, and the resulting positions are written
 * back to {@code Slot.x/y}. Highlight, tooltips, clicks, drags, quick moves and the client prediction
 * stay vanilla, so other mods that read slot positions or {@code hoveredSlot} keep working. Who draws
 * the slot items is chosen by {@link SlotItemRendering}.
 *
 * <pre>{@code
 * @UiView("ixchel_sample:ui/machine/panel.html")
 * class MachineModel extends ViewModel { ... }
 *
 * MenuScreens.register(MENU, (menu, inv, title) -> new UiContainerScreen<>(menu, inv, title, new MachineModel(menu)));
 * }</pre>
 *
 * <p>The page is the one the view model's class declares with {@link UiView}; a model that
 * serves several pages, or none, is mounted with the page id
 * ({@code new UiContainerScreen<>(menu, inv, title, page, model)}).
 *
 * <p>Layout rules:
 * <ul>
 *   <li>The 16×16 item area of a slot is centered in its element's border box; the box must be at
 *       least 16×16.</li>
 *   <li>Every menu slot must be placed by exactly one element. A slot whose element is missing, not
 *       mounted ({@code if}) or not displayed is moved off screen and cannot be hovered or clicked;
 *       so is a slot an {@code overflow} ancestor clips away (its item area center with
 *       {@link SlotItemRendering#VANILLA}, all of its item area with {@link SlotItemRendering#IXCHEL}).
 *       Slots follow scroll offsets.</li>
 *   <li>The screen's {@code leftPos/topPos/imageWidth/imageHeight}, which other mods read as the
 *       GUI area (JEI and EMI lay out beside it), are the bounding box of the page's top-level
 *       elements. Make the panels themselves top-level and center them with {@code margin: auto};
 *       a full-screen wrapper makes the whole screen the GUI area. A click on no element counts as
 *       outside, so it drops the carried stack like vanilla.</li>
 *   <li>A slot is hovered only when the document element under the mouse is (inside) its
 *       {@code <mc-slot>}; a panel drawn over a slot blocks it.</li>
 * </ul>
 * Mouse buttons go to the document and then to vanilla (see {@link #mouseClicked}); wheel and keys go
 * to the document first, and what it does not consume goes to vanilla. ParsedView problems are
 * reported like other document errors (development overlay, log).
 */
@HostApi
public class UiContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    /** Who draws the items in the menu slots (#009). */
    public enum SlotItemRendering {
        /**
         * Vanilla's slot loop, untouched. Items are not clipped by {@code overflow}, so a slot is hidden
         * once the center of its item area leaves the clip.
         */
        VANILLA,
        /** The screen draws slot items itself, each clipped by its {@code overflow} ancestors; see {@link ClippedSlotItems}. */
        IXCHEL
    }

    private final HostedDocument doc;
    private final ContainerLayout layout;
    private final ClippedSlotItems clippedItems;

    /**
     * Same as {@link #UiContainerScreen(AbstractContainerMenu, Inventory, Component, ViewModel, SlotItemRendering)}
     * with {@link SlotItemRendering#VANILLA}.
     */
    public UiContainerScreen(M menu, Inventory inventory, Component title, ViewModel model) {
        this(menu, inventory, title, model, SlotItemRendering.VANILLA);
    }

    /**
     * A container screen of the page {@code model}'s class declares with {@link UiView}.
     *
     * @param model     the view model; closed with the screen
     * @param slotItems who draws slot items
     * @throws IllegalArgumentException if the class does not declare exactly one concrete page
     * @throws UiLoadException          outside development when the document has errors
     */
    public UiContainerScreen(M menu, Inventory inventory, Component title, ViewModel model,
                             SlotItemRendering slotItems) {
        this(menu, inventory, title, DeclaredView.of(model.getClass()), model, slotItems);
    }

    /**
     * Same as {@link #UiContainerScreen(AbstractContainerMenu, Inventory, Component, ResourceLocation, ViewModel, SlotItemRendering)}
     * with {@link SlotItemRendering#VANILLA}.
     */
    public UiContainerScreen(M menu, Inventory inventory, Component title, ResourceLocation page, ViewModel model) {
        this(menu, inventory, title, page, model, SlotItemRendering.VANILLA);
    }

    /**
     * A container screen of an explicit page.
     *
     * @param page  the page under {@code assets/<namespace>/}; the namespace is the host mod
     *                  id, stylesheets come from its {@code <link>} elements
     * @param model     the view model the page binds to, or {@code null}; closed with the screen
     * @param slotItems who draws slot items
     * @throws UiLoadException outside development when the document has errors
     */
    public UiContainerScreen(M menu, Inventory inventory, Component title, ResourceLocation page, ViewModel model,
                             SlotItemRendering slotItems) {
        super(menu, inventory, title);
        this.layout = new ContainerLayout(menu, slotItems == SlotItemRendering.IXCHEL);
        this.clippedItems = slotItems == SlotItemRendering.IXCHEL ? new ClippedSlotItems() : null;
        this.doc = new HostedDocument(page, model, layout::invalidate);
    }

    @Override
    protected void init() {
        super.init();
        doc.watch();
        // Lay out now: other mods read the GUI area right after init.
        layout(Float.NaN, Float.NaN);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        FrameTimer.start(this);
        layout(mouseX, mouseY);
        if (clippedItems != null) {
            clippedItems.hideVanillaLoop(this);
        }
        renderBackground(g);
        // renderBg pushes a pose raised above the document; everything vanilla draws after it (slot
        // items, highlight, carried item, tooltips) lands on top of the document's rects and items.
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null && menu.getCarried().isEmpty()) {
            doc.drawTooltip(g, mouseX, mouseY);
        }
        doc.drawOverlay(g);
        g.pose().popPose();
        FrameTimer.stop(this);
    }

    /**
     * Paints the document, then raises the pose by its depth until the end of {@link #render}: the
     * painter's depth grows with every group of {@code <mc-item>}s, and vanilla would otherwise draw slot
     * items under rects that come later in the document.
     */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        doc.paint(g, mouseX, mouseY);
        g.pose().pushPose();
        g.pose().translate(0, 0, doc.topZ());
    }

    /** The document draws its own labels; with {@link SlotItemRendering#IXCHEL} the slot items are drawn here. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (clippedItems != null) {
            clippedItems.draw(g, font, menu, layout, hoveredSlot, imageWidth);
        }
    }

    /** Frames the document for the pointer, NaN for none, and moves the GUI area to where it laid the slots out. */
    private void layout(float pointerX, float pointerY) {
        DisplayList list = doc.frame(width, height, pointerX, pointerY);
        UiDocument d = doc.document();
        if (list != null && d != null && layout.update(d, list, width, height)) {
            leftPos = layout.left();
            topPos = layout.top();
            imageWidth = layout.width();
            imageHeight = layout.height();
        }
    }

    /** A slot is hovered only through its own element: panels over it, or clipping, block it. */
    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        if (!super.isHovering(x, y, width, height, mouseX, mouseY)) {
            return false;
        }
        UiDocument d = doc.document();
        return d == null || layout.hovering(d, x, y, width, height, mouseX, mouseY);
    }

    /** Outside means on no document element, so clicks on transparent panel areas stay inside. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        UiDocument d = doc.document();
        if (d == null) {
            return super.hasClickedOutside(mouseX, mouseY, left, top, button);
        }
        return d.events().hitTest((float) mouseX, (float) mouseY) == d.root();
    }

    @Override
    public void mouseMoved(double x, double y) {
        UiDocument d = doc.document();
        if (d != null) {
            d.events().mouseMoved((float) x, (float) y);
        }
        super.mouseMoved(x, y);
    }

    /**
     * Buttons go to the document and then always to vanilla: vanilla does nothing for a press on no
     * slot inside the GUI area, and its drag, quick craft and double click state needs every press and
     * release, including a release over a panel.
     */
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        UiDocument d = doc.document();
        boolean ui = d != null && d.events().mousePressed((float) x, (float) y, button, Modifiers.held());
        return super.mouseClicked(x, y, button) || ui;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        UiDocument d = doc.document();
        boolean ui = d != null && d.events().mouseReleased((float) x, (float) y, button, Modifiers.held());
        return super.mouseReleased(x, y, button) || ui;
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

    /** The current document, for querying or driving it; {@code null} only when the first development load failed. */
    public UiDocument document() {
        return doc.document();
    }

    @Override
    public void removed() {
        SlotSnapshot items = layout.snapshot();
        layout.restore();
        super.removed();
        doc.close();
        items.attach();
        ExitingDocuments.add(doc);
    }
}
