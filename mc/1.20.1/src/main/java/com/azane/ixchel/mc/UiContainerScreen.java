package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.diag.UiLoadException;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.runtime.UiDocument;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A container screen whose layout is a document. The host's menu defines the slots; the template
 * places them with {@code <item-slot slot="N">} elements ({@link McElements#CONTAINER_TAGS}), and the
 * resulting positions are written back to {@code Slot.x/y}. Everything about items stays vanilla:
 * drawing, highlight, tooltips, clicks, drags, quick moves and the client prediction, so other mods
 * that read slot positions or {@code hoveredSlot} keep working.
 *
 * <pre>{@code
 * MenuScreens.register(MENU, (menu, inv, title) -> new UiContainerScreen<>(menu, inv, title,
 *         MODID, "ui/backpack.html", List.of("ui/backpack.css"), new BackpackView(menu)));
 * }</pre>
 *
 * <p>Layout rules:
 * <ul>
 *   <li>The 16×16 item area of a slot is centered in its element's border box; the box must be at
 *       least 16×16.</li>
 *   <li>Every menu slot must be placed by exactly one element. A slot whose element is missing, not
 *       mounted ({@code if}), not displayed, or whose item area center is clipped away by an
 *       {@code overflow} ancestor is moved off screen and cannot be hovered or clicked.</li>
 *   <li>The screen's {@code leftPos/topPos/imageWidth/imageHeight}, which other mods read as the
 *       GUI area (JEI and EMI lay out beside it), are the bounding box of the template's top-level
 *       elements. Make the panels themselves top-level and center them with {@code margin: auto};
 *       a full-screen wrapper makes the whole screen the GUI area. A click on no element counts as
 *       outside, so it drops the carried stack like vanilla.</li>
 *   <li>A slot is hovered only when the document element under the mouse is (inside) its
 *       {@code <item-slot>}; a panel drawn over a slot blocks it.</li>
 * </ul>
 * Mouse buttons go to the document and then to vanilla (see {@link #mouseClicked}); wheel and keys go
 * to the document first, and what it does not consume goes to vanilla. Template problems are
 * reported like other document errors (development overlay, log).
 */
public class UiContainerScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    /** Tag of the slot element. */
    public static final String ITEM_SLOT = "item-slot";
    /** Attribute holding the menu slot index. */
    public static final String SLOT_ATTRIBUTE = "slot";

    private static final int ITEM_SIZE = 16;

    private final ScreenDocument doc;
    /** Slot index → element placing it, rebuilt whenever the display list changes. */
    private final Map<Integer, Element> placed = new HashMap<>();
    private final Map<Element, Integer> slotOf = new IdentityHashMap<>();
    /** Written item-area position (x in the high half) → slot index, for {@link #isHovering}. */
    private final Map<Long, Integer> slotAtPosition = new HashMap<>();
    private final Set<String> reported = new HashSet<>();
    private long placedVersion = -1;
    private int placedWidth = -1;
    private int placedHeight = -1;

    /**
     * @param hostModId    namespace of the template and stylesheets
     * @param templatePath template path under {@code assets/<hostModId>/}
     * @param cssPaths     stylesheet paths under {@code assets/<hostModId>/}, applied in order
     * @param model        the view model the template binds to, or {@code null}; closed with the screen
     * @throws UiLoadException outside development when the document has errors
     */
    public UiContainerScreen(M menu, Inventory inventory, Component title, String hostModId, String templatePath,
                             List<String> cssPaths, ViewModel model) {
        super(menu, inventory, title);
        this.doc = new ScreenDocument(hostModId, templatePath, cssPaths, McElements.CONTAINER_TAGS, model,
                () -> placedVersion = -1);
    }

    @Override
    protected void init() {
        super.init();
        doc.watch();
        // Lay out now: other mods read the GUI area right after init.
        layout();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        layout();
        renderBackground(g);
        // renderBg pushes a pose raised above the document; everything vanilla draws after it (slot
        // items, highlight, carried item, tooltips) lands on top of the document's rects and items.
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null && menu.getCarried().isEmpty()) {
            doc.drawTooltip(g, font, mouseX, mouseY);
        }
        doc.drawOverlay(g);
        g.pose().popPose();
    }

    /**
     * Paints the document, then raises the pose by its depth until the end of {@link #render}: the
     * painter's depth grows with every group of {@code <item>}s, and vanilla would otherwise draw slot
     * items under rects that come later in the document.
     */
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        doc.paint(g);
        g.pose().pushPose();
        g.pose().translate(0, 0, doc.topZ());
    }

    /** The document draws its own labels. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    private void layout() {
        DisplayList list = doc.frame(width, height);
        UiDocument d = doc.document();
        if (list == null || d == null) {
            return;
        }
        if (list.version() == placedVersion && width == placedWidth && height == placedHeight) {
            return;
        }
        placedVersion = list.version();
        placedWidth = width;
        placedHeight = height;
        placeGuiArea(d.root());
        placeSlots(d);
    }

    private void placeGuiArea(Element root) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Node n : root.children()) {
            if (n instanceof Element e && displayed(e)) {
                LayoutBox b = e.box();
                minX = Math.min(minX, b.x());
                minY = Math.min(minY, b.y());
                maxX = Math.max(maxX, b.x() + b.width());
                maxY = Math.max(maxY, b.y() + b.height());
            }
        }
        if (minX > maxX) {
            leftPos = 0;
            topPos = 0;
            imageWidth = 0;
            imageHeight = 0;
            return;
        }
        leftPos = (int) Math.floor(minX);
        topPos = (int) Math.floor(minY);
        imageWidth = (int) Math.ceil(maxX) - leftPos;
        imageHeight = (int) Math.ceil(maxY) - topPos;
    }

    private void placeSlots(UiDocument d) {
        placed.clear();
        slotOf.clear();
        slotAtPosition.clear();
        List<Element> elements = new ArrayList<>();
        collect(d.root(), elements);
        int count = menu.slots.size();
        for (Element e : elements) {
            Integer index = index(d, e, count);
            if (index == null) {
                continue;
            }
            Element earlier = placed.putIfAbsent(index, e);
            if (earlier != null) {
                report(d, e, "slot " + index + " is already placed by the <" + ITEM_SLOT + "> at "
                        + earlier.pos() + "; this one is ignored");
                continue;
            }
            slotOf.put(e, index);
        }
        Set<Integer> missing = new TreeSet<>();
        for (int i = 0; i < count; i++) {
            Slot slot = menu.slots.get(i);
            Element e = placed.get(i);
            if (e == null) {
                missing.add(i);
                SlotPositions.set(slot, SlotPositions.HIDDEN, SlotPositions.HIDDEN);
                continue;
            }
            LayoutBox b = e.box();
            if (b.width() < ITEM_SIZE || b.height() < ITEM_SIZE) {
                report(d, e, "<" + ITEM_SLOT + "> for slot " + i + " is " + b.width() + "x" + b.height()
                        + "; it must be at least 16x16");
            }
            float x = b.x() + (b.width() - ITEM_SIZE) / 2;
            float y = b.y() + (b.height() - ITEM_SIZE) / 2;
            if (!displayed(e) || clipped(e, x + ITEM_SIZE / 2f, y + ITEM_SIZE / 2f)) {
                SlotPositions.set(slot, SlotPositions.HIDDEN, SlotPositions.HIDDEN);
            } else {
                int sx = Math.round(x) - leftPos;
                int sy = Math.round(y) - topPos;
                SlotPositions.set(slot, sx, sy);
                slotAtPosition.putIfAbsent(position(sx, sy), i);
            }
        }
        if (!missing.isEmpty()) {
            report(d, d.root(), "menu slots " + missing + " have no <" + ITEM_SLOT + ">; every slot of "
                    + menu.getClass().getSimpleName() + " must be placed");
        }
    }

    private static void collect(Element e, List<Element> out) {
        if (e.tag().equals(ITEM_SLOT)) {
            out.add(e);
        }
        for (Node n : e.children()) {
            if (n instanceof Element c) {
                collect(c, out);
            }
        }
    }

    private Integer index(UiDocument d, Element e, int count) {
        String raw = e.attribute(SLOT_ATTRIBUTE);
        if (raw == null) {
            report(d, e, "<" + ITEM_SLOT + "> needs a slot index: slot=\"N\" or bind:slot");
            return null;
        }
        int index;
        try {
            index = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            report(d, e, "slot index '" + raw + "' is not an integer");
            return null;
        }
        if (index < 0 || index >= count) {
            report(d, e, "slot index " + index + " is out of range; the menu has " + count + " slots");
            return null;
        }
        return index;
    }

    private static boolean displayed(Element e) {
        for (Element a = e; a != null; a = a.parent()) {
            if (a.computed() != null && a.computed().get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
                return false;
            }
        }
        return true;
    }

    private static boolean clipped(Element e, float x, float y) {
        for (Element a = e.parent(); a != null; a = a.parent()) {
            if (a.computed() != null && a.computed().get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE) {
                LayoutBox b = a.box();
                if (x < b.x() || y < b.y() || x >= b.x() + b.width() || y >= b.y() + b.height()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void report(UiDocument d, Element at, String message) {
        if (reported.add(at.pos() + message)) {
            d.diagnostics().error(at.pos(), message);
        }
    }

    /** A slot is hovered only through its own element: panels over it, or clipping, block it. */
    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        if (!super.isHovering(x, y, width, height, mouseX, mouseY)) {
            return false;
        }
        UiDocument d = doc.document();
        if (d == null || width != ITEM_SIZE || height != ITEM_SIZE) {
            return true;
        }
        Integer slot = slotAtPosition.get(position(x, y));
        if (slot == null) {
            return true;
        }
        for (Element e = d.events().hitTest((float) mouseX, (float) mouseY); e != null; e = e.parent()) {
            Integer index = slotOf.get(e);
            if (index != null) {
                return index.equals(slot);
            }
        }
        return false;
    }

    private static long position(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
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
        boolean ui = d != null && d.events().mousePressed((float) x, (float) y, button);
        return super.mouseClicked(x, y, button) || ui;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        UiDocument d = doc.document();
        boolean ui = d != null && d.events().mouseReleased((float) x, (float) y, button);
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
    public void removed() {
        super.removed();
        doc.close();
    }
}
