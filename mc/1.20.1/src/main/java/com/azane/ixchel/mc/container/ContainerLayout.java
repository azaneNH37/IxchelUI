package com.azane.ixchel.mc.container;

import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.runtime.UiDocument;
import com.azane.ixchel.core.view.UiElement;
import com.azane.ixchel.core.view.TagRegistry;
import com.azane.ixchel.mc.UiRegistries;
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
 * Projects a document onto a vanilla container screen: the GUI area other mods read, and every menu
 * slot written back to {@code Slot.x/y} from its {@code <mc-slot>} element (see
 * {@code ref/SDG-ixchel-container-slot-integration} for the rules). Client thread only.
 */
@UiElement(tag = ContainerLayout.SLOT_TAG, attributes = ContainerLayout.INDEX_ATTRIBUTE, containerOnly = true)
public final class ContainerLayout {
    /**
     * Tag of the slot element, {@code <mc-slot index="N">}, declared on this class and registered in
     * {@link UiRegistries#MC}: the projection of menu slot {@code N}. The slot's 16×16 item area is
     * centered in the element's border box and written back to {@code Slot.x/y}; vanilla keeps drawing
     * the item and handling clicks, drags and quick moves there. Use {@code bind:index} for slots from
     * an {@code each} row.
     */
    public static final String SLOT_TAG = "mc-slot";
    /** Attribute holding the menu slot index. */
    public static final String INDEX_ATTRIBUTE = "index";

    static final int ITEM_SIZE = 16;

    private final AbstractContainerMenu menu;
    private final boolean clipWholeItem;
    /** Slot index → element placing it, rebuilt whenever the display list changes. */
    private final Map<Integer, Element> placed = new HashMap<>();
    private final Map<Element, Integer> slotOf = new IdentityHashMap<>();
    /** Written item-area position (x in the high half) → slot index, for {@link #hovering}. */
    private final Map<Long, Integer> slotAtPosition = new HashMap<>();
    /** Visible slot → its clip in GUI pixels (x0, y0, x1, y1). */
    private final Map<Slot, int[]> clipOf = new IdentityHashMap<>();
    private final Set<String> reported = new HashSet<>();
    /** {@code Slot.x/y} before this layout moved them, as {@link #position}. */
    private final long[] originalPositions;
    private long placedVersion = -1;
    private int placedWidth = -1;
    private int placedHeight = -1;
    private int left;
    private int top;
    private int areaWidth;
    private int areaHeight;

    /**
     * @param clipWholeItem whether a slot counts as visible while any of its item area shows through
     *                      its {@code overflow} ancestors (the screen clips each item itself), rather
     *                      than only while the center does (vanilla draws items unclipped)
     */
    public ContainerLayout(AbstractContainerMenu menu, boolean clipWholeItem) {
        this.menu = menu;
        this.clipWholeItem = clipWholeItem;
        this.originalPositions = new long[menu.slots.size()];
        for (int i = 0; i < originalPositions.length; i++) {
            Slot slot = menu.slots.get(i);
            originalPositions[i] = position(slot.x, slot.y);
        }
    }

    /** Forgets the last layout, as after the document was replaced. */
    public void invalidate() {
        placedVersion = -1;
    }

    /**
     * Places the GUI area and the slots for the display list {@code list} of {@code d} on a screen of
     * the given size; does nothing when neither changed since the last call.
     *
     * @return whether the GUI area may have changed
     */
    public boolean update(UiDocument d, DisplayList list, int width, int height) {
        // A scroll repaints without a layout pass, so the display list version covers it too.
        if (list.version() == placedVersion && width == placedWidth && height == placedHeight) {
            return false;
        }
        placedVersion = list.version();
        placedWidth = width;
        placedHeight = height;
        placeGuiArea(d.root());
        placeSlots(d);
        return true;
    }

    /** The GUI area: the bounding box of the page's displayed top-level elements. */
    public int left() {
        return left;
    }

    public int top() {
        return top;
    }

    public int width() {
        return areaWidth;
    }

    public int height() {
        return areaHeight;
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
            left = top = areaWidth = areaHeight = 0;
            return;
        }
        left = (int) Math.floor(minX);
        top = (int) Math.floor(minY);
        areaWidth = (int) Math.ceil(maxX) - left;
        areaHeight = (int) Math.ceil(maxY) - top;
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
                report(d, e, ContainerCode.DUPLICATE_SLOT, "slot " + index + " is already placed by the <" + SLOT_TAG
                        + "> at " + earlier.pos() + "; this one is ignored");
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
            if (!displayed(e)) {
                SlotPositions.set(slot, SlotPositions.HIDDEN, SlotPositions.HIDDEN);
                continue;
            }
            LayoutBox b = e.box();
            if (b.width() < ITEM_SIZE || b.height() < ITEM_SIZE) {
                report(d, e, ContainerCode.SLOT_TOO_SMALL, "<" + SLOT_TAG + "> for slot " + i + " is " + b.width()
                        + "x" + b.height() + "; it must be at least 16x16");
            }
            int x = Math.round(b.x() + (b.width() - ITEM_SIZE) / 2);
            int y = Math.round(b.y() - e.scrollOffset() + (b.height() - ITEM_SIZE) / 2);
            if (!visible(e, x, y, clip)) {
                SlotPositions.set(slot, SlotPositions.HIDDEN, SlotPositions.HIDDEN);
                continue;
            }
            int sx = x - left;
            int sy = y - top;
            SlotPositions.set(slot, sx, sy);
            slotAtPosition.putIfAbsent(position(sx, sy), i);
            // Clamped: an unclipped slot has infinite bounds, which the scissor would scale and overflow.
            clipOf.put(slot, new int[] {Math.max(0, (int) Math.floor(clip[0])), Math.max(0, (int) Math.floor(clip[1])),
                    Math.min(placedWidth, (int) Math.ceil(clip[2])), Math.min(placedHeight, (int) Math.ceil(clip[3]))});
        }
        if (!missing.isEmpty()) {
            report(d, d.root(), ContainerCode.UNPLACED_SLOT, "menu slots " + missing + " have no <" + SLOT_TAG
                    + ">; every slot of " + menu.getClass().getSimpleName() + " must be placed");
        }
    }

    /**
     * Intersects the clips of the {@code overflow} ancestors into {@code clip} (x0, y0, x1, y1) and
     * decides whether the item area at {@code (x, y)} shows through it.
     */
    private boolean visible(Element e, int x, int y, float[] clip) {
        clip[0] = Float.NEGATIVE_INFINITY;
        clip[1] = Float.NEGATIVE_INFINITY;
        clip[2] = Float.POSITIVE_INFINITY;
        clip[3] = Float.POSITIVE_INFINITY;
        for (Element a = e.parent(); a != null; a = a.parent()) {
            if (a.computed() != null && a.computed().get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE) {
                // Overflow clips to the padding box.
                LayoutBox b = a.box();
                float top = b.y() - a.scrollOffset() + b.borderTop();
                clip[0] = Math.max(clip[0], b.x() + b.borderLeft());
                clip[1] = Math.max(clip[1], top);
                clip[2] = Math.min(clip[2], b.x() + b.width() - b.borderRight());
                clip[3] = Math.min(clip[3], top + b.height() - b.borderTop() - b.borderBottom());
            }
        }
        if (clipWholeItem) {
            return x < clip[2] && y < clip[3] && x + ITEM_SIZE > clip[0] && y + ITEM_SIZE > clip[1];
        }
        float cx = x + ITEM_SIZE / 2f;
        float cy = y + ITEM_SIZE / 2f;
        return cx >= clip[0] && cy >= clip[1] && cx < clip[2] && cy < clip[3];
    }

    private static void collect(Element e, List<Element> out) {
        if (e.tag().equals(SLOT_TAG)) {
            out.add(e);
        }
        for (Node n : e.children()) {
            if (n instanceof Element c) {
                collect(c, out);
            }
        }
    }

    private Integer index(UiDocument d, Element e, int count) {
        String raw = e.attribute(INDEX_ATTRIBUTE);
        if (raw == null) {
            report(d, e, ContainerCode.MISSING_INDEX, "<" + SLOT_TAG + "> needs a slot index: index=\"N\" or bind:index");
            return null;
        }
        int index;
        try {
            index = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            report(d, e, ContainerCode.MALFORMED_INDEX, "slot index '" + raw + "' is not an integer");
            return null;
        }
        if (index < 0 || index >= count) {
            report(d, e, ContainerCode.INDEX_OUT_OF_RANGE, "slot index " + index + " is out of range; the menu has "
                    + count + " slots");
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

    private void report(UiDocument d, Element at, ContainerCode code, String message) {
        if (reported.add(at.pos() + message)) {
            d.diagnostics().report(code, at.pos(), message);
        }
    }

    /**
     * Whether the slot vanilla found under the pointer at item area {@code (x, y)} (screen-relative)
     * is really hovered: only through its own element, so panels over it, or clipping, block it.
     */
    public boolean hovering(UiDocument d, int x, int y, int width, int height, double mouseX, double mouseY) {
        if (width != ITEM_SIZE || height != ITEM_SIZE) {
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

    /** The clip of a visible slot in GUI pixels (x0, y0, x1, y1), or {@code null} when it is hidden. */
    int[] clip(Slot slot) {
        return clipOf.get(slot);
    }

    /** What the visible slots show now, by their elements; call before {@link #restore}. */
    public SlotSnapshot snapshot() {
        Map<Element, SlotSnapshot.Item> items = new IdentityHashMap<>();
        for (Map.Entry<Integer, Element> placement : placed.entrySet()) {
            Slot slot = menu.slots.get(placement.getKey());
            if (clipOf.get(slot) != null && slot.isActive()) {
                items.put(placement.getValue(), new SlotSnapshot.Item(slot.getItem().copy(), slot.getNoItemIcon(),
                        slot.x + slot.y * areaWidth));
            }
        }
        return new SlotSnapshot(items);
    }

    /** Puts the slots back where the menu had them: a menu can outlive the screen ({@code player.inventoryMenu}). */
    public void restore() {
        for (int i = 0; i < originalPositions.length; i++) {
            SlotPositions.set(menu.slots.get(i), (int) (originalPositions[i] >> 32), (int) originalPositions[i]);
        }
    }

    private static long position(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }
}
