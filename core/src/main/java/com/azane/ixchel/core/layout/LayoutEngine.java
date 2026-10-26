package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
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
 */
public final class LayoutEngine {
    /** Floats per recorded measurement: known width, known height, available width, then the answer. */
    private static final int QUERY = 5;

    private static final class Slot {
        NodeId id;
        ComputedStyle style;
        List<NodeId> children = List.of();
        String text;
        float fontSize;
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

    public LayoutEngine(TextShaper shaper, PropertyRegistry registry) {
        this.shaper = shaper;
        this.layoutIds = registry.layoutAffectingIds();
        tree.enableRounding();
    }

    /** {@code root} is the implicit document root; it is sized to the viewport. */
    public void layout(Element root, float viewportWidth, float viewportHeight) {
        generation++;
        changed = false;
        NodeId rootId = sync(root, true);
        for (Iterator<Slot> it = slots.values().iterator(); it.hasNext(); ) {
            Slot s = it.next();
            if (s.seen != generation) {
                tree.remove(s.id);
                it.remove();
                changed = true;
            }
        }
        if (viewportWidth != this.viewportWidth || viewportHeight != this.viewportHeight) {
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            tree.setStyle(rootId, rootStyle(viewportWidth, viewportHeight));
            changed = true;
        }
        if (!changed) {
            return;
        }
        computeCount++;
        tree.computeLayout(rootId, TaffySize.of(AvailableSpace.definite(viewportWidth), AvailableSpace.definite(viewportHeight)));
        writeBoxes(root, 0, 0);
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

    private NodeId sync(Node node, boolean isRoot) {
        if (node instanceof TextNode t) {
            return syncText(t);
        }
        Element e = (Element) node;
        Slot slot = slots.get(e);
        if (slot == null) {
            slot = new Slot();
            slot.id = tree.newLeaf(new TaffyStyle());
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
        }
        List<Node> children = e.children();
        List<NodeId> ids = new ArrayList<>(children.size());
        for (Node c : children) {
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

    private NodeId syncText(TextNode t) {
        float size = t.parent().computed().get(CssProperties.FONT_SIZE).value();
        Slot slot = slots.get(t);
        if (slot == null) {
            slot = new Slot();
            slot.id = tree.newLeafWithMeasure(new TaffyStyle(), measure(slot));
            slots.put(t, slot);
            changed = true;
        }
        slot.seen = generation;
        String text = t.text();
        if (size != slot.fontSize || !text.equals(slot.text)) {
            if (size == slot.fontSize && measuresAlike(slot, text)) {
                slot.text = text;
                slot.shaped = shaper.shape(text, size, slot.contentWidth);
            } else {
                slot.text = text;
                slot.fontSize = size;
                slot.queryCount = 0;
                tree.markDirty(slot.id);
                changed = true;
            }
        }
        return slot.id;
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
            FloatSize s = measure(text, slot.fontSize, q[i], q[i + 1], q[i + 2]);
            if (!same(s.width, q[i + 3]) || !same(s.height, q[i + 4])) {
                return false;
            }
        }
        return true;
    }

    private MeasureFunc measure(Slot slot) {
        return (known, available) -> {
            AvailableSpace a = available.width;
            float availableWidth = a.isMinContent() ? Float.NaN : a.isDefinite() ? a.getValue() : Float.POSITIVE_INFINITY;
            FloatSize s = measure(slot.text, slot.fontSize, known.width, known.height, availableWidth);
            slot.record(known.width, known.height, availableWidth, s.width, s.height);
            return s;
        };
    }

    /** {@code availableWidth}: NaN for min-content, infinite for max-content. */
    private FloatSize measure(String text, float fontSize, float knownWidth, float knownHeight, float availableWidth) {
        float maxWidth;
        if (!Float.isNaN(knownWidth)) {
            maxWidth = knownWidth;
        } else if (Float.isNaN(availableWidth)) {
            maxWidth = shaper.minContentWidth(text, fontSize);
        } else {
            maxWidth = availableWidth;
        }
        TextShaper.Shaped s = shaper.shape(text, fontSize, maxWidth);
        return new FloatSize(Float.isNaN(knownWidth) ? s.width() : knownWidth,
                Float.isNaN(knownHeight) ? s.height() : knownHeight);
    }

    /** Equality that also holds for NaN. */
    private static boolean same(float a, float b) {
        return Float.floatToIntBits(a) == Float.floatToIntBits(b);
    }

    private void writeBoxes(Node node, float parentX, float parentY) {
        Slot slot = slots.get(node);
        Layout l = tree.getLayout(slot.id);
        float x = parentX + l.location().x;
        float y = parentY + l.location().y;
        node.box().set(x, y, l.size().width, l.size().height);
        if (node instanceof TextNode) {
            slot.contentWidth = tree.getUnroundedLayout(slot.id).contentBoxWidth();
            slot.shaped = shaper.shape(slot.text, slot.fontSize, slot.contentWidth);
        } else {
            for (Node c : ((Element) node).children()) {
                writeBoxes(c, x, y);
            }
        }
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
        Overflow o = cs.get(CssProperties.OVERFLOW) == Keywords.Overflow.HIDDEN ? Overflow.HIDDEN : Overflow.VISIBLE;
        s.overflow = new TaffyPoint<>(o, o);
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
