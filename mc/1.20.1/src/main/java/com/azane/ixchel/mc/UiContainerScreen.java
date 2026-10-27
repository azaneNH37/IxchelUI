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
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
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
 * resulting positions are written back to {@code Slot.x/y}. Highlight, tooltips, clicks, drags, quick
 * moves and the client prediction stay vanilla, so other mods that read slot positions or
 * {@code hoveredSlot} keep working. Who draws the slot items is chosen by {@link SlotItemRendering}.
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
 *       mounted ({@code if}) or not displayed is moved off screen and cannot be hovered or clicked;
 *       so is a slot an {@code overflow} ancestor clips away (its item area center with
 *       {@link SlotItemRendering#VANILLA}, all of its item area with {@link SlotItemRendering#IXCHEL}).
 *       Slots follow scroll offsets.</li>
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

    /** Who draws the items in the menu slots (#009). */
    public enum SlotItemRendering {
        /**
         * Vanilla's slot loop, untouched. Items are not clipped by {@code overflow}, so a slot is hidden
         * once the center of its item area leaves the clip.
         */
        VANILLA,
        /**
         * The screen draws slot items itself, each clipped by its {@code overflow} ancestors, so slots in
         * a scroll list are cut at its edge. Vanilla's loop is hidden by a zero-area scissor, which also
         * hides what other mods draw in that loop. Not drawn: the preview of a right-click split and of
         * a drag across slots (vanilla keeps their state private); the stacks themselves are correct.
         */
        IXCHEL
    }

    private static final int ITEM_SIZE = 16;
    /** Vanilla's slot item depth in {@code renderSlot}. */
    private static final float SLOT_ITEM_Z = 100;

    private final ScreenDocument doc;
    private final SlotItemRendering slotItems;
    /** Slot index → element placing it, rebuilt whenever the display list changes. */
    private final Map<Integer, Element> placed = new HashMap<>();
    private final Map<Element, Integer> slotOf = new IdentityHashMap<>();
    /** Written item-area position (x in the high half) → slot index, for {@link #isHovering}. */
    private final Map<Long, Integer> slotAtPosition = new HashMap<>();
    /** Visible slot → its clip in GUI pixels (x0, y0, x1, y1), for {@link SlotItemRendering#IXCHEL}. */
    private final Map<Slot, int[]> clipOf = new IdentityHashMap<>();
    private final Set<String> reported = new HashSet<>();
    /** {@code Slot.x/y} before this screen moved them, as {@link #position}. */
    private final long[] originalPositions;
    private final Renderable hideVanillaSlots = (g, mx, my, pt) -> g.enableScissor(0, 0, 0, 0);
    private long placedVersion = -1;
    private int placedWidth = -1;
    private int placedHeight = -1;

    /** Same as the full constructor with {@link SlotItemRendering#VANILLA}. */
    public UiContainerScreen(M menu, Inventory inventory, Component title, String hostModId, String templatePath,
                             List<String> cssPaths, ViewModel model) {
        this(menu, inventory, title, hostModId, templatePath, cssPaths, model, SlotItemRendering.VANILLA);
    }

    /**
     * @param hostModId    namespace of the template and stylesheets
     * @param templatePath template path under {@code assets/<hostModId>/}
     * @param cssPaths     stylesheet paths under {@code assets/<hostModId>/}, applied in order
     * @param model        the view model the template binds to, or {@code null}; closed with the screen
     * @param slotItems    who draws slot items
     * @throws UiLoadException outside development when the document has errors
     */
    public UiContainerScreen(M menu, Inventory inventory, Component title, String hostModId, String templatePath,
                             List<String> cssPaths, ViewModel model, SlotItemRendering slotItems) {
        super(menu, inventory, title);
        this.slotItems = slotItems;
        this.originalPositions = new long[menu.slots.size()];
        for (int i = 0; i < originalPositions.length; i++) {
            Slot slot = menu.slots.get(i);
            originalPositions[i] = position(slot.x, slot.y);
        }
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
        if (slotItems == SlotItemRendering.IXCHEL) {
            // Renderables run right before the slot loop; other mods may append theirs after init.
            List<Renderable> renderables = Renderables.of(this);
            renderables.remove(hideVanillaSlots);
            renderables.add(hideVanillaSlots);
        }
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

    /** The document draws its own labels; with {@link SlotItemRendering#IXCHEL} the slot items are drawn here. */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (slotItems != SlotItemRendering.IXCHEL) {
            return;
        }
        g.disableScissor();
        for (Slot slot : menu.slots) {
            int[] clip = clipOf.get(slot);
            if (clip == null || !slot.isActive()) {
                continue;
            }
            g.enableScissor(clip[0], clip[1], clip[2], clip[3]);
            drawSlotItem(g, slot);
            if (slot == hoveredSlot && slot.isHighlightable()) {
                renderSlotHighlight(g, slot.x, slot.y, 0);
            }
            g.disableScissor();
        }
    }

    /** Vanilla's {@code renderSlot} without the private split and drag previews. */
    private void drawSlotItem(GuiGraphics g, Slot slot) {
        ItemStack stack = slot.getItem();
        g.pose().pushPose();
        g.pose().translate(0, 0, SLOT_ITEM_Z);
        if (stack.isEmpty()) {
            Pair<ResourceLocation, ResourceLocation> icon = slot.getNoItemIcon();
            if (icon != null) {
                TextureAtlasSprite sprite = minecraft.getTextureAtlas(icon.getFirst()).apply(icon.getSecond());
                g.blit(slot.x, slot.y, 0, ITEM_SIZE, ITEM_SIZE, sprite);
            }
        } else {
            g.renderItem(stack, slot.x, slot.y, slot.x + slot.y * imageWidth);
            g.renderItemDecorations(font, stack, slot.x, slot.y);
        }
        g.pose().popPose();
    }

    private void layout() {
        DisplayList list = doc.frame(width, height);
        UiDocument d = doc.document();
        if (list == null || d == null) {
            return;
        }
        // A scroll repaints without a layout pass, so the display list version covers it too.
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
        clipOf.clear();
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
        float[] clip = new float[4];
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
            int x = Math.round(b.x() + (b.width() - ITEM_SIZE) / 2);
            int y = Math.round(b.y() - scrollAbove(e) + (b.height() - ITEM_SIZE) / 2);
            if (!displayed(e) || !visible(e, x, y, clip)) {
                SlotPositions.set(slot, SlotPositions.HIDDEN, SlotPositions.HIDDEN);
                continue;
            }
            int sx = x - leftPos;
            int sy = y - topPos;
            SlotPositions.set(slot, sx, sy);
            slotAtPosition.putIfAbsent(position(sx, sy), i);
            // Clamped: an unclipped slot has infinite bounds, which the scissor would scale and overflow.
            clipOf.put(slot, new int[] {Math.max(0, (int) Math.floor(clip[0])), Math.max(0, (int) Math.floor(clip[1])),
                    Math.min(width, (int) Math.ceil(clip[2])), Math.min(height, (int) Math.ceil(clip[3]))});
        }
        if (!missing.isEmpty()) {
            report(d, d.root(), "menu slots " + missing + " have no <" + ITEM_SLOT + ">; every slot of "
                    + menu.getClass().getSimpleName() + " must be placed");
        }
    }

    /** How far the scroll containers above {@code e} move it up. */
    private static float scrollAbove(Element e) {
        float dy = 0;
        for (Element a = e.parent(); a != null; a = a.parent()) {
            dy += a.scrollTop();
        }
        return dy;
    }

    /**
     * Intersects the clips of the {@code overflow} ancestors into {@code clip} (x0, y0, x1, y1) and
     * decides whether the item area at {@code (x, y)} shows through it under the rendering mode.
     */
    private boolean visible(Element e, int x, int y, float[] clip) {
        clip[0] = Float.NEGATIVE_INFINITY;
        clip[1] = Float.NEGATIVE_INFINITY;
        clip[2] = Float.POSITIVE_INFINITY;
        clip[3] = Float.POSITIVE_INFINITY;
        for (Element a = e.parent(); a != null; a = a.parent()) {
            if (a.computed() != null && a.computed().get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE) {
                LayoutBox b = a.box();
                float top = b.y() - scrollAbove(a);
                clip[0] = Math.max(clip[0], b.x());
                clip[1] = Math.max(clip[1], top);
                clip[2] = Math.min(clip[2], b.x() + b.width());
                clip[3] = Math.min(clip[3], top + b.height());
            }
        }
        if (slotItems == SlotItemRendering.IXCHEL) {
            return x < clip[2] && y < clip[3] && x + ITEM_SIZE > clip[0] && y + ITEM_SIZE > clip[1];
        }
        float cx = x + ITEM_SIZE / 2f;
        float cy = y + ITEM_SIZE / 2f;
        return cx >= clip[0] && cy >= clip[1] && cx < clip[2] && cy < clip[3];
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

    /** Puts the slots back where the menu had them: a menu can outlive the screen ({@code player.inventoryMenu}). */
    @Override
    public void removed() {
        for (int i = 0; i < originalPositions.length; i++) {
            SlotPositions.set(menu.slots.get(i), (int) (originalPositions[i] >> 32), (int) originalPositions[i]);
        }
        super.removed();
        doc.close();
    }

    /**
     * {@code Screen.renderables}: private in vanilla, public only through Forge's access transformer,
     * so read by reflection like {@link SlotPositions} (development name, then SRG). Loaded on the first
     * {@link SlotItemRendering#IXCHEL} frame.
     */
    private static final class Renderables {
        private static final MethodHandle GET = getter("renderables", "f_169369_");

        @SuppressWarnings("unchecked")
        static List<Renderable> of(Screen screen) {
            try {
                return (List<Renderable>) GET.invokeExact(screen);
            } catch (Throwable t) {
                throw new IllegalStateException("reading Screen.renderables failed", t);
            }
        }

        private static MethodHandle getter(String... names) {
            for (String name : names) {
                try {
                    Field f = Screen.class.getDeclaredField(name);
                    f.setAccessible(true);
                    return MethodHandles.lookup().unreflectGetter(f)
                            .asType(MethodType.methodType(List.class, Screen.class));
                } catch (NoSuchFieldException ignored) {
                    // try the next candidate name
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Screen." + name + " is not readable", e);
                }
            }
            throw new IllegalStateException("Screen field not found under " + Arrays.toString(names));
        }
    }
}
