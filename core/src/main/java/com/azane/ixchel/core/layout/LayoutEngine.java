package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.text.TextShaper;
import dev.vfyjxf.taffy.geometry.FloatSize;
import dev.vfyjxf.taffy.geometry.TaffyPoint;
import dev.vfyjxf.taffy.geometry.TaffyRect;
import dev.vfyjxf.taffy.geometry.TaffySize;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.AvailableSpace;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import dev.vfyjxf.taffy.style.LengthPercentage;
import dev.vfyjxf.taffy.style.LengthPercentageAuto;
import dev.vfyjxf.taffy.style.Overflow;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import dev.vfyjxf.taffy.style.TaffyStyle;
import dev.vfyjxf.taffy.tree.Layout;
import dev.vfyjxf.taffy.tree.NodeId;
import dev.vfyjxf.taffy.tree.TaffyTree;
import dev.vfyjxf.taffy.util.MeasureFunc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Mirrors the DOM into a taffy tree and writes absolute, pixel-rounded border boxes into
 * {@link Node#box()}. Only nodes whose layout-affecting computed values, text or children changed are
 * touched; when nothing changed and the viewport is the same, taffy is not run at all. A text change
 * that answers every measurement taffy has asked of that node the same way (a counter going from 5 to
 * 6 in a font with equal digit widths) cannot move anything, so it is re-shaped without running taffy.
 *
 * <p>An {@code <input>} has no intrinsic width (CSS or flex sizes it) and one line of its font size as
 * its intrinsic content height; its value does not take part in layout. A {@code <select>} is as wide
 * as its widest option's label plus {@value #SELECT_ARROW_SPACE} pixels for the arrow and one line
 * high; its options are not laid out inside it.
 *
 * <p>{@link TopLayer} entries are laid out after the document, each against the viewport: an open
 * {@code <dialog>} centered in it (percentages resolve against the viewport), the options of an open
 * {@code <select>} as a column of their max-content width but at least the select's, placed under
 * the select where it shows (its {@link Element#scrollOffset()} taken off), or above it when the
 * viewport has no room below, and moved left to stay inside. A closed dialog is not laid out.
 */
public final class LayoutEngine {
    /** Floats per recorded measurement: known width, known height, available width, then the answer. */
    private static final int QUERY = 5;
    /** Pixels a select reserves after its label for the arrow. */
    public static final float SELECT_ARROW_SPACE = 10;

    private static final class Slot {
        NodeId id;
        ComputedStyle style;
        List<NodeId> children = List.of();
        String text;
        float fontSize;
        boolean nowrap;
        /** A select's option labels, joined by newlines, as last measured. */
        String labels = "";
        TextShaper.Shaped shaped;
        float contentWidth;
        /**
         * Measurements taffy made of this text node since it was last marked dirty, {@link #QUERY}
         * floats each; the available width is NaN for min-content and infinite for max-content.
         */
        float[] queries = new float[0];
        int queryCount;
        int seen;

        void record(float knownWidth, float knownHeight, float available, float width, float height) {
            for (int i = 0; i < queryCount * QUERY; i += QUERY) {
                if (same(queries[i], knownWidth) && same(queries[i + 1], knownHeight) && same(queries[i + 2], available)) {
                    return;
                }
            }
            if ((queryCount + 1) * QUERY > queries.length) {
                queries = Arrays.copyOf(queries, Math.max(4, queryCount * 2) * QUERY);
            }
            int i = queryCount++ * QUERY;
            queries[i] = knownWidth;
            queries[i + 1] = knownHeight;
            queries[i + 2] = available;
            queries[i + 3] = width;
            queries[i + 4] = height;
        }
    }

    private final TaffyTree tree = new TaffyTree();
    private final TextShaper shaper;
    private final int[] layoutIds;
    private final Map<Node, Slot> slots = new IdentityHashMap<>();
    private int generation;
    private boolean changed;
    /** Number of taffy layout runs; observed by tests. */
    int computeCount;
    private float viewportWidth = Float.NaN;
    private float viewportHeight = Float.NaN;
    /** A top-layer entry's own taffy node: the viewport-sized box a dialog is centered in, or a select's option column. */
    private final Map<Element, NodeId> layers = new IdentityHashMap<>();
    private final Map<Element, List<NodeId>> layerChildren = new IdentityHashMap<>();

    public LayoutEngine(TextShaper shaper, PropertyRegistry registry) {
        this.shaper = shaper;
        this.layoutIds = registry.layoutAffectingIds();
        tree.enableRounding();
    }

    /**
     * {@code root} is the implicit document root; it is sized to the viewport. {@code top}'s entries
     * are laid out after it, in order (a select inside a dialog is placed after the dialog).
     */
    public void layout(Element root, float viewportWidth, float viewportHeight, TopLayer top) {
        generation++;
        changed = false;
        boolean resized = viewportWidth != this.viewportWidth || viewportHeight != this.viewportHeight;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        NodeId rootId = sync(root, true);
        List<Element> entries = top.entries();
        List<NodeId> layerIds = new ArrayList<>(entries.size());
        for (Element entry : entries) {
            layerIds.add(syncLayer(entry, resized));
        }
        for (Iterator<Map.Entry<Element, NodeId>> it = layers.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Element, NodeId> en = it.next();
            if (!entries.contains(en.getKey())) {
                tree.remove(en.getValue());
                layerChildren.remove(en.getKey());
                it.remove();
                changed = true;
            }
        }
        for (Iterator<Slot> it = slots.values().iterator(); it.hasNext(); ) {
            Slot s = it.next();
            if (s.seen != generation) {
                tree.remove(s.id);
                it.remove();
                changed = true;
            }
        }
        if (resized) {
            tree.setStyle(rootId, rootStyle(viewportWidth, viewportHeight));
            changed = true;
        }
        if (!changed) {
            return;
        }
        computeCount++;
        tree.computeLayout(rootId, TaffySize.of(AvailableSpace.definite(viewportWidth), AvailableSpace.definite(viewportHeight)));
        writeBoxes(root, 0, 0);
        for (int i = 0; i < entries.size(); i++) {
            placeLayer(entries.get(i), layerIds.get(i));
        }
    }

    /** Syncs an entry's content under its own node; a dialog's node is the viewport box it is centered in. */
    private NodeId syncLayer(Element entry, boolean resized) {
        boolean dialog = entry.tag().equals("dialog");
        NodeId id = layers.get(entry);
        if (id == null) {
            id = tree.newLeaf(new TaffyStyle());
            layers.put(entry, id);
            changed = true;
            resized = true;
        }
        if (dialog && resized) {
            TaffyStyle s = rootStyle(viewportWidth, viewportHeight);
            s.alignItems = AlignItems.CENTER;
            s.justifyContent = AlignContent.CENTER;
            tree.setStyle(id, s);
        }
        List<NodeId> ids = new ArrayList<>();
        if (dialog) {
            ids.add(sync(entry, false));
        } else {
            List<Node> children = entry.children();
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i) instanceof Element o) {
                    ids.add(sync(o, false));
                }
            }
        }
        if (!ids.equals(layerChildren.get(entry))) {
            layerChildren.put(entry, ids);
            tree.setChildren(id, ids.toArray(new NodeId[0]));
            changed = true;
        }
        return id;
    }

    private void placeLayer(Element entry, NodeId id) {
        if (entry.tag().equals("dialog")) {
            tree.computeLayout(id, TaffySize.of(AvailableSpace.definite(viewportWidth), AvailableSpace.definite(viewportHeight)));
            writeBoxes(entry, 0, 0);
            return;
        }
        LayoutBox b = entry.box();
        TaffyStyle s = new TaffyStyle();
        s.display = TaffyDisplay.FLEX;
        s.flexDirection = FlexDirection.COLUMN;
        s.minSize = TaffySize.of(TaffyDimension.length(b.width()), TaffyDimension.AUTO);
        tree.setStyle(id, s);
        tree.computeLayout(id, TaffySize.of(AvailableSpace.maxContent(), AvailableSpace.maxContent()));
        Layout l = tree.getLayout(id);
        float top = b.y() - entry.scrollOffset();
        float y = top + b.height();
        if (y + l.size().height > viewportHeight && top - l.size().height >= 0) {
            y = top - l.size().height;
        }
        float x = Math.max(0, Math.min(b.x(), viewportWidth - l.size().width));
        List<Node> children = entry.children();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof Element o) {
                writeBoxes(o, x, y);
            }
        }
    }

    /** Fonts or glyph widths changed: drop cached measurements and re-measure every text node. */
    public void invalidateText() {
        shaper.invalidate();
        for (Map.Entry<Node, Slot> en : slots.entrySet()) {
            if (en.getKey() instanceof TextNode) {
                en.getValue().text = null;
                en.getValue().queryCount = 0;
            }
        }
    }

    /** Lines of a text node as laid out; valid after {@link #layout}. */
    public List<TextShaper.Line> lines(TextNode node) {
        return slots.get(node).shaped.lines();
    }

    public float lineHeight(float fontSize) {
        return shaper.lineHeight(fontSize);
    }

    /** See {@link TextShaper#ellipsize}. */
    public String ellipsize(String line, float fontSize, float maxWidth) {
        return shaper.ellipsize(line, fontSize, maxWidth);
    }

    /** The advance of {@code text} on one line. */
    public float textWidth(String text, float fontSize) {
        return shaper.width(text, fontSize);
    }

    private NodeId sync(Node node, boolean isRoot) {
        if (node instanceof TextNode t) {
            return syncText(t);
        }
        Element e = (Element) node;
        Slot slot = slots.get(e);
        if (slot == null) {
            slot = new Slot();
            FormControl control = e.control();
            slot.id = control == null ? tree.newLeaf(new TaffyStyle())
                    : tree.newLeafWithMeasure(new TaffyStyle(), control.kind() == FormControl.Kind.SELECT
                    ? selectMeasure(slot) : inputMeasure(slot));
            slots.put(e, slot);
            changed = true;
        }
        slot.seen = generation;
        if (!isRoot && slot.style != e.computed()) {
            if (slot.style == null || !slot.style.sameValues(e.computed(), layoutIds)) {
                tree.setStyle(slot.id, toTaffy(e.computed()));
                changed = true;
            }
            slot.style = e.computed();
            if (e.control() != null) {
                float size = e.computed().get(CssProperties.FONT_SIZE).value();
                if (size != slot.fontSize) {
                    slot.fontSize = size;
                    tree.markDirty(slot.id);
                    changed = true;
                }
            }
        }
        if (e.control() != null && e.control().kind() == FormControl.Kind.SELECT) {
            // The options are laid out only in the top layer; their labels size the select.
            StringBuilder labels = new StringBuilder();
            for (Element o : e.control().options()) {
                labels.append(o.textContent().strip()).append('\n');
                clearTreeChanges(o);
            }
            if (!labels.toString().equals(slot.labels)) {
                slot.labels = labels.toString();
                tree.markDirty(slot.id);
                changed = true;
            }
            e.clearTreeChange();
            return slot.id;
        }
        List<Node> children = e.children();
        List<NodeId> ids = new ArrayList<>(children.size());
        for (Node c : children) {
            if (c instanceof Element d && d.tag().equals("dialog")) {
                // Laid out in the top layer while open, not at all while closed.
                clearTreeChanges(d);
                continue;
            }
            ids.add(sync(c, false));
        }
        if (!ids.equals(slot.children)) {
            slot.children = ids;
            tree.setChildren(slot.id, ids.toArray(new NodeId[0]));
            changed = true;
        }
        e.clearTreeChange();
        return slot.id;
    }

    /** Clears the tree-change marks of a subtree layout skips, so later changes inside it are seen again. */
    private static void clearTreeChanges(Element e) {
        e.clearTreeChange();
        for (Node c : e.children()) {
            if (c instanceof Element ce) {
                clearTreeChanges(ce);
            }
        }
    }

    private NodeId syncText(TextNode t) {
        ComputedStyle parent = t.parent().computed();
        float size = parent.get(CssProperties.FONT_SIZE).value();
        boolean nowrap = parent.get(CssProperties.WHITE_SPACE) == Keywords.WhiteSpace.NOWRAP;
        Slot slot = slots.get(t);
        if (slot == null) {
            slot = new Slot();
            slot.id = tree.newLeafWithMeasure(new TaffyStyle(), measure(slot));
            slots.put(t, slot);
            changed = true;
        }
        slot.seen = generation;
        String text = t.text();
        if (size != slot.fontSize || nowrap != slot.nowrap || !text.equals(slot.text)) {
            if (size == slot.fontSize && nowrap == slot.nowrap && measuresAlike(slot, text)) {
                slot.text = text;
                slot.shaped = shaper.shape(text, size, wrapWidth(slot, slot.contentWidth));
            } else {
                slot.text = text;
                slot.fontSize = size;
                slot.nowrap = nowrap;
                slot.queryCount = 0;
                tree.markDirty(slot.id);
                changed = true;
            }
        }
        return slot.id;
    }

    /** The width lines break at: none for {@code white-space: nowrap}. */
    private static float wrapWidth(Slot slot, float width) {
        return slot.nowrap ? Float.POSITIVE_INFINITY : width;
    }

    /**
     * Whether {@code text} gives every recorded measurement of the slot's current text the same answer.
     * With nothing recorded there is nothing to compare, so the answer is no.
     */
    private boolean measuresAlike(Slot slot, String text) {
        if (slot.queryCount == 0) {
            return false;
        }
        float[] q = slot.queries;
        for (int i = 0; i < slot.queryCount * QUERY; i += QUERY) {
            FloatSize s = measure(slot, text, q[i], q[i + 1], q[i + 2]);
            if (!same(s.width, q[i + 3]) || !same(s.height, q[i + 4])) {
                return false;
            }
        }
        return true;
    }

    /** An {@code <input>}: no width of its own, one line high. */
    private MeasureFunc inputMeasure(Slot slot) {
        return (known, available) -> new FloatSize(Float.isNaN(known.width) ? 0 : known.width,
                Float.isNaN(known.height) ? shaper.lineHeight(slot.fontSize) : known.height);
    }

    /** A {@code <select>}: its widest option label and the arrow, one line high. */
    private MeasureFunc selectMeasure(Slot slot) {
        return (known, available) -> {
            float width = known.width;
            if (Float.isNaN(width)) {
                width = 0;
                for (String label : slot.labels.split("\n", -1)) {
                    width = Math.max(width, shaper.width(label, slot.fontSize));
                }
                width += SELECT_ARROW_SPACE;
            }
            return new FloatSize(width, Float.isNaN(known.height) ? shaper.lineHeight(slot.fontSize) : known.height);
        };
    }

    private MeasureFunc measure(Slot slot) {
        return (known, available) -> {
            AvailableSpace a = available.width;
            float availableWidth = a.isMinContent() ? Float.NaN : a.isDefinite() ? a.getValue() : Float.POSITIVE_INFINITY;
            FloatSize s = measure(slot, slot.text, known.width, known.height, availableWidth);
            slot.record(known.width, known.height, availableWidth, s.width, s.height);
            return s;
        };
    }

    /** {@code availableWidth}: NaN for min-content, infinite for max-content. Font size and wrapping come from {@code slot}. */
    private FloatSize measure(Slot slot, String text, float knownWidth, float knownHeight, float availableWidth) {
        float maxWidth;
        if (!Float.isNaN(knownWidth)) {
            maxWidth = knownWidth;
        } else if (Float.isNaN(availableWidth)) {
            maxWidth = shaper.minContentWidth(text, slot.fontSize);
        } else {
            maxWidth = availableWidth;
        }
        TextShaper.Shaped s = shaper.shape(text, slot.fontSize, wrapWidth(slot, maxWidth));
        return new FloatSize(Float.isNaN(knownWidth) ? s.width() : knownWidth,
                Float.isNaN(knownHeight) ? s.height() : knownHeight);
    }

    /** Equality that also holds for NaN. */
    private static boolean same(float a, float b) {
        return Float.floatToIntBits(a) == Float.floatToIntBits(b);
    }

    /**
     * Writes the boxes of {@code node}'s subtree and returns the bottom edge, in document coordinates,
     * of what it adds to an ancestor scroll container's scrollable overflow: its border box, extended by
     * its descendants' unless it clips them. A scroll container's own range covers the margin boxes of
     * its in-flow children plus its bottom padding (as browsers do for flex containers), and every
     * descendant's contribution; {@code display: none} children add nothing.
     */
    private float writeBoxes(Node node, float parentX, float parentY) {
        Slot slot = slots.get(node);
        Layout l = tree.getLayout(slot.id);
        float x = parentX + l.location().x;
        float y = parentY + l.location().y;
        float bottom = y + l.size().height;
        node.box().set(x, y, l.size().width, l.size().height);
        node.box().setBorder(l.border().top, l.border().right, l.border().bottom, l.border().left);
        node.box().setPadding(l.padding().top, l.padding().right, l.padding().bottom, l.padding().left);
        if (node instanceof TextNode) {
            slot.contentWidth = tree.getUnroundedLayout(slot.id).contentBoxWidth();
            slot.shaped = shaper.shape(slot.text, slot.fontSize, wrapWidth(slot, slot.contentWidth));
            return bottom;
        }
        Element e = (Element) node;
        float childrenEnd = y;
        float inFlowEnd = y;
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            Node c = children.get(i);
            if (c instanceof Element skipped && (skipped.tag().equals("dialog") || e.tag().equals("select"))) {
                continue; // top-layer content: placed by placeLayer, and no part of this scroll range
            }
            float end = writeBoxes(c, x, y);
            ComputedStyle ccs = c instanceof Element ce ? ce.computed() : null;
            if (ccs != null && ccs.get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
                continue;
            }
            childrenEnd = Math.max(childrenEnd, end);
            if (ccs == null || ccs.get(CssProperties.POSITION) != Keywords.Position.ABSOLUTE) {
                float marginBottom = tree.getLayout(slots.get(c).id).margin().bottom;
                inFlowEnd = Math.max(inFlowEnd, c.box().y() + c.box().height() + marginBottom);
            }
        }
        ComputedStyle cs = e.computed();
        if (cs == null || cs.get(CssProperties.OVERFLOW) == Keywords.Overflow.VISIBLE) {
            e.setMaxScrollTop(0);
            return Math.max(bottom, childrenEnd);
        }
        // The scrollport is the padding box: content scrolls up to its bottom edge, not the border's.
        float portBottom = bottom - l.border().bottom;
        e.setMaxScrollTop(Math.max(childrenEnd, inFlowEnd + l.padding().bottom) - portBottom);
        return bottom;
    }

    private static TaffyStyle rootStyle(float w, float h) {
        TaffyStyle s = new TaffyStyle();
        s.display = TaffyDisplay.FLEX;
        s.flexDirection = FlexDirection.COLUMN;
        s.size = TaffySize.of(TaffyDimension.length(w), TaffyDimension.length(h));
        return s;
    }

    static TaffyStyle toTaffy(ComputedStyle cs) {
        TaffyStyle s = new TaffyStyle();
        s.display = cs.get(CssProperties.DISPLAY) == Keywords.Display.NONE ? TaffyDisplay.NONE : TaffyDisplay.FLEX;
        s.position = cs.get(CssProperties.POSITION) == Keywords.Position.ABSOLUTE ? TaffyPosition.ABSOLUTE : TaffyPosition.RELATIVE;
        s.inset = rect(lpa(cs.get(CssProperties.LEFT)), lpa(cs.get(CssProperties.RIGHT)),
                lpa(cs.get(CssProperties.TOP)), lpa(cs.get(CssProperties.BOTTOM)));
        s.size = TaffySize.of(dim(cs.get(CssProperties.WIDTH)), dim(cs.get(CssProperties.HEIGHT)));
        s.minSize = TaffySize.of(dim(cs.get(CssProperties.MIN_WIDTH)), dim(cs.get(CssProperties.MIN_HEIGHT)));
        s.maxSize = TaffySize.of(dim(cs.get(CssProperties.MAX_WIDTH)), dim(cs.get(CssProperties.MAX_HEIGHT)));
        s.margin = rect(lpa(cs.get(CssProperties.MARGIN_LEFT)), lpa(cs.get(CssProperties.MARGIN_RIGHT)),
                lpa(cs.get(CssProperties.MARGIN_TOP)), lpa(cs.get(CssProperties.MARGIN_BOTTOM)));
        s.padding = rect(lp(cs.get(CssProperties.PADDING_LEFT)), lp(cs.get(CssProperties.PADDING_RIGHT)),
                lp(cs.get(CssProperties.PADDING_TOP)), lp(cs.get(CssProperties.PADDING_BOTTOM)));
        s.border = rect(border(cs, CssProperties.BORDER_LEFT_WIDTH, CssProperties.BORDER_LEFT_STYLE),
                border(cs, CssProperties.BORDER_RIGHT_WIDTH, CssProperties.BORDER_RIGHT_STYLE),
                border(cs, CssProperties.BORDER_TOP_WIDTH, CssProperties.BORDER_TOP_STYLE),
                border(cs, CssProperties.BORDER_BOTTOM_WIDTH, CssProperties.BORDER_BOTTOM_STYLE));
        s.flexDirection = FlexDirection.valueOf(cs.get(CssProperties.FLEX_DIRECTION).name());
        s.flexWrap = switch (cs.get(CssProperties.FLEX_WRAP)) {
            case NOWRAP -> FlexWrap.NO_WRAP;
            case WRAP -> FlexWrap.WRAP;
            case WRAP_REVERSE -> FlexWrap.WRAP_REVERSE;
        };
        s.flexGrow = cs.get(CssProperties.FLEX_GROW);
        s.flexShrink = cs.get(CssProperties.FLEX_SHRINK);
        s.flexBasis = dim(cs.get(CssProperties.FLEX_BASIS));
        s.alignItems = AlignItems.valueOf(cs.get(CssProperties.ALIGN_ITEMS).name());
        s.alignSelf = AlignItems.valueOf(cs.get(CssProperties.ALIGN_SELF).name());
        s.justifyContent = AlignContent.valueOf(cs.get(CssProperties.JUSTIFY_CONTENT).name());
        s.gap = TaffySize.of(lp(cs.get(CssProperties.COLUMN_GAP)), lp(cs.get(CssProperties.ROW_GAP)));
        s.overflow = switch (cs.get(CssProperties.OVERFLOW)) {
            case VISIBLE -> new TaffyPoint<>(Overflow.VISIBLE, Overflow.VISIBLE);
            case HIDDEN -> new TaffyPoint<>(Overflow.HIDDEN, Overflow.HIDDEN);
            // No space is reserved: the scrollbar is drawn over the content (scrollbarWidth stays 0).
            case SCROLL, AUTO -> new TaffyPoint<>(Overflow.SCROLL, Overflow.SCROLL);
        };
        return s;
    }

    /** Assigns fields by name so the result does not depend on the factory's parameter order. */
    private static <T> TaffyRect<T> rect(T left, T right, T top, T bottom) {
        TaffyRect<T> r = TaffyRect.ltrb(left, top, right, bottom);
        r.left = left;
        r.right = right;
        r.top = top;
        r.bottom = bottom;
        return r;
    }

    /** The used width: 0 for {@code border-style: none}. */
    private static LengthPercentage border(ComputedStyle cs, PropertyDef<Length> width, PropertyDef<Keywords.BorderStyle> style) {
        return cs.get(style) == Keywords.BorderStyle.NONE ? LengthPercentage.length(0) : lp(cs.get(width));
    }

    private static TaffyDimension dim(Length l) {
        return switch (l.unit()) {
            case PX -> TaffyDimension.length(l.value());
            case PERCENT -> TaffyDimension.percent(l.value() / 100f);
            case AUTO -> TaffyDimension.AUTO;
        };
    }

    private static LengthPercentageAuto lpa(Length l) {
        return switch (l.unit()) {
            case PX -> LengthPercentageAuto.length(l.value());
            case PERCENT -> LengthPercentageAuto.percent(l.value() / 100f);
            case AUTO -> LengthPercentageAuto.AUTO;
        };
    }

    private static LengthPercentage lp(Length l) {
        return switch (l.unit()) {
            case PX -> LengthPercentage.length(l.value());
            case PERCENT -> LengthPercentage.percent(l.value() / 100f);
            case AUTO -> throw new IllegalArgumentException("auto is not a valid length-percentage");
        };
    }
}
