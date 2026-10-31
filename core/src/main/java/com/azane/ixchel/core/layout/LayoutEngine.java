package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.PropertyRegistry;
import com.azane.ixchel.core.css.property.Transform;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.template.TagRegistry;
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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors the DOM into a taffy tree and writes absolute, pixel-rounded border boxes into
 * {@link Node#box()}. Only nodes whose layout-affecting computed values, text or children changed are
 * touched; when nothing changed and the viewport is the same, taffy is not run at all. A text change
 * that answers every measurement taffy has asked of its paragraph the same way (a counter going from 5
 * to 6 in a font with equal digit widths) cannot move anything, so it is re-placed without running taffy.
 *
 * <p>Text is laid out in paragraphs (see {@link TextShaper}). A flex container's text child that is not
 * all whitespace is a paragraph of its own, in the container's font; whitespace-only text between flex
 * items is not laid out. An element whose {@code display} is {@code block}, or {@code inline} or
 * {@code inline-block} where its parent's flex layout blockifies it, is a block container: one taffy
 * leaf whose content is a paragraph of its inline descendants' text, in each one's font, and atomic
 * boxes. An atomic box is an {@code inline-block}, or an {@code <img>}, native element or form control
 * whatever its {@code display}; it is laid out on its own at its max-content size (percentages resolve
 * against nothing) and sits on the baseline: the baseline of its last line when it holds text and has
 * {@code overflow: visible}, its bottom margin edge otherwise. Inside a block container, a {@code flex}
 * or {@code block} child or an absolutely positioned one is an error and laid out as an atomic box, and
 * an {@code inline} element with box properties (background, border, padding, margin, sizes, position,
 * transform) is an error. An inline element's box is the bounding box of its line rectangles
 * ({@link LayoutBox#fragments()}). Lines are aligned by the container's {@code text-align} (whole
 * pixels; a line wider than the box starts at its start edge, as in CSS) and, with
 * {@code text-overflow: ellipsis} and an {@code overflow} other than visible, cut with an ellipsis at
 * the container's padding box.
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

    /**
     * A text node's characters on one line, at document coordinates; {@code y} is the top of a line of
     * the run's font. {@code span} indexes the node's {@linkplain TextNode#styled() styled spans}, -1 for plain text.
     */
    public record TextFragment(float x, float y, float size, String text, int span) {
    }

    private static final class Slot {
        NodeId id;
        ComputedStyle style;
        List<NodeId> children = List.of();
        /** The paragraph of a text leaf or block container; {@code null} for every other node. */
        Para para;
        /** As an atomic box: its size and baseline as last laid out on its own; {@code null} to lay it out again. */
        TextShaper.Box atom;
        float fontSize;
        /** A select's option labels, joined by newlines, as last measured. */
        String labels = "";
        /**
         * Measurements taffy made of this paragraph since it was last marked dirty, {@link #QUERY}
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

    /** Where an item comes from: run {@code span} of a text node (-1: its plain text), or an atomic element. */
    private record Source(Node node, int span) {
    }

    /** An inline element holds items {@code [start, end)}. */
    private record InlineRange(Element element, int start, int end) {
    }

    /** A paragraph's content, its container's text settings, and how it was last laid out. */
    private static final class Para {
        final List<TextShaper.Item> items = new ArrayList<>();
        final List<Source> sources = new ArrayList<>();
        final List<InlineRange> inlines = new ArrayList<>();
        final float strut;
        final boolean nowrap;
        final float align;
        final boolean ellipsis;
        /** Whether the next run's leading space collapses into what precedes it. */
        boolean afterSpace = true;
        /** The content width it was laid out at, and the result. */
        float width;
        TextShaper.Paragraph shaped;

        Para(ComputedStyle container) {
            strut = container.get(CssProperties.FONT_SIZE).value();
            nowrap = container.get(CssProperties.WHITE_SPACE) == Keywords.WhiteSpace.NOWRAP;
            align = switch (container.get(CssProperties.TEXT_ALIGN)) {
                case START, LEFT -> 0;
                case CENTER -> 0.5f;
                case END, RIGHT -> 1;
            };
            ellipsis = container.get(CssProperties.TEXT_OVERFLOW) == Keywords.TextOverflow.ELLIPSIS
                    && container.get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE;
        }

        /** Adds {@code text} with whitespace collapsed, across run boundaries too. */
        void addRun(String text, float size, boolean bold, boolean nowrapRun, Source source) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
                    if (!afterSpace) {
                        sb.append(' ');
                        afterSpace = true;
                    }
                } else {
                    sb.append(c);
                    afterSpace = false;
                }
            }
            if (!sb.isEmpty()) {
                items.add(new TextShaper.Run(sb.toString(), size, bold, nowrapRun));
                sources.add(source);
            }
        }

        void addBox(TextShaper.Box box, Element atom) {
            items.add(box);
            sources.add(new Source(atom, -1));
            afterSpace = false;
        }

        boolean sameLayout(Para o) {
            return strut == o.strut && nowrap == o.nowrap && items.equals(o.items);
        }

        boolean samePlacement(Para o) {
            return sameLayout(o) && align == o.align && ellipsis == o.ellipsis
                    && sources.equals(o.sources) && inlines.equals(o.inlines);
        }

        float wrapWidth(float width) {
            return nowrap ? Float.POSITIVE_INFINITY : width;
        }
    }

    private final TaffyTree tree = new TaffyTree();
    private final TextShaper shaper;
    private final int[] layoutIds;
    private final TagRegistry tags;
    private final Diagnostics diag;
    private final Set<String> reported = new HashSet<>();
    private final Map<Node, Slot> slots = new IdentityHashMap<>();
    private final Map<TextNode, List<TextFragment>> fragments = new IdentityHashMap<>();
    private int generation;
    private boolean changed;
    /** Number of taffy layout runs of the document; observed by tests. */
    int computeCount;
    private float viewportWidth = Float.NaN;
    private float viewportHeight = Float.NaN;
    /** A top-layer entry's own taffy node: the viewport-sized box a dialog is centered in, or a select's option column. */
    private final Map<Element, NodeId> layers = new IdentityHashMap<>();
    private final Map<Element, List<NodeId>> layerChildren = new IdentityHashMap<>();

    /** {@code tags} tells native (replaced) elements apart; {@code diag} takes the inline content errors. */
    public LayoutEngine(TextShaper shaper, PropertyRegistry registry, TagRegistry tags, Diagnostics diag) {
        this.shaper = shaper;
        this.layoutIds = registry.layoutAffectingIds();
        this.tags = tags;
        this.diag = diag;
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
        for (Iterator<Map.Entry<Node, Slot>> it = slots.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Node, Slot> en = it.next();
            if (en.getValue().seen != generation) {
                tree.remove(en.getValue().id);
                if (en.getKey() instanceof TextNode t) {
                    fragments.remove(t);
                }
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
        fragments.clear();
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

    /** Fonts or glyph widths changed: drop cached measurements and lay every paragraph out again. */
    public void invalidateText() {
        shaper.invalidate();
        for (Slot s : slots.values()) {
            s.para = null;
            s.atom = null;
            s.queryCount = 0;
        }
    }

    /** A text node's characters as placed, in line order; empty for text that is not laid out. Valid after {@link #layout}. */
    public List<TextFragment> fragments(TextNode node) {
        return fragments.getOrDefault(node, List.of());
    }

    public float lineHeight(float fontSize) {
        return shaper.lineHeight(fontSize);
    }

    /** The advance of {@code text} on one line, in a regular-weight font. */
    public float textWidth(String text, float fontSize) {
        return shaper.width(text, fontSize, false);
    }

    /** {@code null} for a text node that is not laid out: whitespace-only text between flex items. */
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
        boolean block = !isRoot && isBlockContainer(e);
        if (block != (slot.para != null)) {
            tree.setMeasureFunc(slot.id, block ? paragraphMeasure(slot) : null);
            if (block) {
                slot.children = List.of();
                tree.setChildren(slot.id, new NodeId[0]);
            }
            slot.para = null;
            slot.queryCount = 0;
            changed = true;
        }
        if (block) {
            Para p = new Para(e.computed());
            collect(p, e);
            syncParagraph(e, slot, p);
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
            NodeId id = sync(c, false);
            if (id != null) {
                ids.add(id);
            }
        }
        if (!ids.equals(slot.children)) {
            slot.children = ids;
            tree.setChildren(slot.id, ids.toArray(new NodeId[0]));
            changed = true;
        }
        e.clearTreeChange();
        return slot.id;
    }

    /** {@code <img>} and native elements: their content is their own, not a paragraph. */
    private boolean isReplaced(Element e) {
        return e.tag().equals("img") || tags.payloadAttribute(e.tag()) != null;
    }

    /** An element whose content is a paragraph, where flex layout or an inline formatting context lays out the element itself. */
    private boolean isBlockContainer(Element e) {
        Keywords.Display d = e.computed().get(CssProperties.DISPLAY);
        return d != Keywords.Display.FLEX && d != Keywords.Display.NONE && e.control() == null && !isReplaced(e);
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

    /** A flex container's text child: its own paragraph, or nothing when it is all whitespace. */
    private NodeId syncText(TextNode t) {
        if (t.text().isBlank()) {
            return null;
        }
        Slot slot = slots.get(t);
        if (slot == null) {
            slot = new Slot();
            slot.id = tree.newLeafWithMeasure(new TaffyStyle(), paragraphMeasure(slot));
            slots.put(t, slot);
            changed = true;
        }
        slot.seen = generation;
        ComputedStyle parent = t.parent().computed();
        Para p = new Para(parent);
        addText(p, t, parent);
        syncParagraph(t, slot, p);
        return slot.id;
    }

    /** Adds the inline content of {@code parent} to {@code p}; atomic boxes are laid out on their own here. */
    private void collect(Para p, Element parent) {
        List<Node> children = parent.children();
        for (int i = 0; i < children.size(); i++) {
            Node c = children.get(i);
            if (c instanceof TextNode t) {
                addText(p, t, parent.computed());
                continue;
            }
            Element e = (Element) c;
            ComputedStyle cs = e.computed();
            Keywords.Display d = cs.get(CssProperties.DISPLAY);
            if (e.tag().equals("dialog") || d == Keywords.Display.NONE) {
                clearTreeChanges(e);
                continue;
            }
            if (d == Keywords.Display.INLINE && !isReplaced(e) && e.control() == null) {
                checkInlineBox(e, cs);
                int start = p.items.size();
                collect(p, e);
                p.inlines.add(new InlineRange(e, start, p.items.size()));
                e.clearTreeChange();
                continue;
            }
            if (d == Keywords.Display.FLEX || d == Keywords.Display.BLOCK) {
                report(e, LayoutCode.BLOCK_IN_INLINE, "<" + e.tag() + "> has display: " + d.name().toLowerCase() + " inside the inline content of a block;"
                        + " a block holds only text and inline content, so use display: inline-block");
            } else if (cs.get(CssProperties.POSITION) == Keywords.Position.ABSOLUTE) {
                report(e, LayoutCode.ABSOLUTE_IN_INLINE, "<" + e.tag() + "> is absolutely positioned inside the inline content of a block, which is not supported");
            }
            p.addBox(atom(e), e);
        }
    }

    private static void addText(Para p, TextNode t, ComputedStyle cs) {
        float size = cs.get(CssProperties.FONT_SIZE).value();
        boolean bold = cs.get(CssProperties.FONT_WEIGHT) == Keywords.FontWeight.BOLD;
        boolean nowrap = cs.get(CssProperties.WHITE_SPACE) == Keywords.WhiteSpace.NOWRAP;
        StyledText styled = t.styled();
        if (styled == null) {
            p.addRun(t.text(), size, bold, nowrap, new Source(t, -1));
            return;
        }
        List<StyledText.Span> spans = styled.spans();
        for (int k = 0; k < spans.size(); k++) {
            StyledText.Span s = spans.get(k);
            p.addRun(s.text(), size, bold || (s.flags() & StyledText.BOLD) != 0, nowrap, new Source(t, k));
        }
    }

    /** An inline element's box is cut across lines, which CSS paints per fragment; not supported (see {@code inline-block}). */
    private void checkInlineBox(Element e, ComputedStyle cs) {
        String found = null;
        if (cs.get(CssProperties.BACKGROUND_COLOR) >>> 24 != 0 || !cs.get(CssProperties.BACKGROUND_IMAGE).isEmpty()) {
            found = "background";
        } else if (cs.get(CssProperties.BORDER_TOP_STYLE) != Keywords.BorderStyle.NONE
                || cs.get(CssProperties.BORDER_RIGHT_STYLE) != Keywords.BorderStyle.NONE
                || cs.get(CssProperties.BORDER_BOTTOM_STYLE) != Keywords.BorderStyle.NONE
                || cs.get(CssProperties.BORDER_LEFT_STYLE) != Keywords.BorderStyle.NONE) {
            found = "border";
        } else if (nonZero(cs, CssProperties.PADDING_TOP, CssProperties.PADDING_RIGHT, CssProperties.PADDING_BOTTOM, CssProperties.PADDING_LEFT)) {
            found = "padding";
        } else if (nonZero(cs, CssProperties.MARGIN_TOP, CssProperties.MARGIN_RIGHT, CssProperties.MARGIN_BOTTOM, CssProperties.MARGIN_LEFT)) {
            found = "margin";
        } else if (cs.get(CssProperties.WIDTH).unit() != Length.Unit.AUTO || cs.get(CssProperties.HEIGHT).unit() != Length.Unit.AUTO
                || cs.get(CssProperties.MIN_WIDTH).unit() != Length.Unit.AUTO || cs.get(CssProperties.MIN_HEIGHT).unit() != Length.Unit.AUTO
                || cs.get(CssProperties.MAX_WIDTH).unit() != Length.Unit.AUTO || cs.get(CssProperties.MAX_HEIGHT).unit() != Length.Unit.AUTO) {
            found = "a size";
        } else if (cs.get(CssProperties.POSITION) != Keywords.Position.RELATIVE) {
            found = "position";
        } else if (cs.get(CssProperties.TRANSFORM) != Transform.NONE) {
            found = "transform";
        }
        if (found != null) {
            report(e, LayoutCode.INLINE_BOX_PROPERTY, "<" + e.tag() + "> is display: inline and has " + found
                    + ", which inline elements do not support; use display: inline-block");
        }
    }

    @SafeVarargs
    private static boolean nonZero(ComputedStyle cs, PropertyDef<Length>... sides) {
        for (PropertyDef<Length> side : sides) {
            Length l = cs.get(side);
            if (l.unit() == Length.Unit.AUTO || l.value() != 0) {
                return true;
            }
        }
        return false;
    }

    private void report(Element e, LayoutCode code, String message) {
        if (reported.add(e.pos() + ": " + message)) {
            diag.report(code, e.pos(), message);
        }
    }

    /** Lays an atomic box out on its own when anything in it changed, and gives its size and baseline. */
    private TextShaper.Box atom(Element e) {
        NodeId id = sync(e, false);
        Slot slot = slots.get(e);
        if (slot.atom == null || tree.isDirty(id)) {
            tree.computeLayout(id, TaffySize.of(AvailableSpace.maxContent(), AvailableSpace.maxContent()));
            // Writes the boxes, and shapes its paragraph for the baseline; placing it moves them.
            writeBoxes(e, 0, 0);
            slot.atom = atomBox(e, slot);
            changed = true;
        }
        return slot.atom;
    }

    private TextShaper.Box atomBox(Element e, Slot slot) {
        ComputedStyle cs = e.computed();
        Layout l = tree.getLayout(slot.id);
        float mt = px(cs.get(CssProperties.MARGIN_TOP));
        float marginHeight = mt + l.size().height + px(cs.get(CssProperties.MARGIN_BOTTOM));
        float width = px(cs.get(CssProperties.MARGIN_LEFT)) + l.size().width + px(cs.get(CssProperties.MARGIN_RIGHT));
        List<TextShaper.Line> lines = slot.para == null ? List.of() : slot.para.shaped.lines();
        if (lines.isEmpty() || cs.get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE) {
            return new TextShaper.Box(width, marginHeight, 0);
        }
        float ascent = mt + l.border().top + l.padding().top;
        for (int i = 0; i < lines.size() - 1; i++) {
            ascent += lines.get(i).height();
        }
        ascent += lines.get(lines.size() - 1).baseline();
        return new TextShaper.Box(width, ascent, marginHeight - ascent);
    }

    /** Margins that are not pixels (percentages, auto) count as 0 around an atomic box. */
    private static float px(Length l) {
        return l.unit() == Length.Unit.PX ? l.value() : 0;
    }

    /**
     * Takes {@code p} as the paragraph of {@code owner}. When it measures the same as the old one (or is
     * the same but for alignment), it is placed now in the owner's current box; otherwise taffy runs.
     */
    private void syncParagraph(Node owner, Slot slot, Para p) {
        Para old = slot.para;
        slot.para = p;
        if (old != null && old.shaped != null && old.sameLayout(p)) {
            p.width = old.width;
            p.shaped = old.shaped;
            if (!old.samePlacement(p)) {
                place(owner, p);
            }
            return;
        }
        if (old != null && old.shaped != null && measuresAlike(slot, p)) {
            p.width = old.width;
            p.shaped = shaper.layout(p.items, p.strut, p.wrapWidth(p.width));
            place(owner, p);
            return;
        }
        slot.queryCount = 0;
        tree.markDirty(slot.id);
        changed = true;
    }

    /**
     * Whether {@code p} gives every recorded measurement of the slot's current paragraph the same answer.
     * With nothing recorded there is nothing to compare, so the answer is no.
     */
    private boolean measuresAlike(Slot slot, Para p) {
        if (slot.queryCount == 0) {
            return false;
        }
        float[] q = slot.queries;
        for (int i = 0; i < slot.queryCount * QUERY; i += QUERY) {
            FloatSize s = measure(p, q[i], q[i + 1], q[i + 2]);
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
                    width = Math.max(width, shaper.width(label, slot.fontSize, false));
                }
                width += SELECT_ARROW_SPACE;
            }
            return new FloatSize(width, Float.isNaN(known.height) ? shaper.lineHeight(slot.fontSize) : known.height);
        };
    }

    private MeasureFunc paragraphMeasure(Slot slot) {
        return (known, available) -> {
            AvailableSpace a = available.width;
            float availableWidth = a.isMinContent() ? Float.NaN : a.isDefinite() ? a.getValue() : Float.POSITIVE_INFINITY;
            FloatSize s = measure(slot.para, known.width, known.height, availableWidth);
            slot.record(known.width, known.height, availableWidth, s.width, s.height);
            return s;
        };
    }

    /** The content size of {@code p}; {@code availableWidth}: NaN for min-content, infinite for max-content. */
    private FloatSize measure(Para p, float knownWidth, float knownHeight, float availableWidth) {
        float maxWidth;
        if (!Float.isNaN(knownWidth)) {
            maxWidth = knownWidth;
        } else if (Float.isNaN(availableWidth)) {
            maxWidth = shaper.minContentWidth(p.items);
        } else {
            maxWidth = availableWidth;
        }
        TextShaper.Paragraph s = shaper.layout(p.items, p.strut, p.wrapWidth(maxWidth));
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
     * its in-flow children (or its lines) plus its bottom padding (as browsers do for flex containers),
     * and every descendant's contribution; {@code display: none} children add nothing.
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
        Para p = slot.para;
        if (p != null) {
            p.width = tree.getUnroundedLayout(slot.id).contentBoxWidth();
            p.shaped = shaper.layout(p.items, p.strut, p.wrapWidth(p.width));
        }
        if (node instanceof TextNode) {
            place(node, p);
            return bottom;
        }
        Element e = (Element) node;
        float childrenEnd = y;
        float inFlowEnd = y;
        if (p != null) {
            childrenEnd = place(e, p);
            inFlowEnd = y + l.border().top + l.padding().top + p.shaped.height();
        } else {
            List<Node> children = e.children();
            for (int i = 0; i < children.size(); i++) {
                Node c = children.get(i);
                if (c instanceof Element skipped && (skipped.tag().equals("dialog") || e.tag().equals("select"))) {
                    continue; // top-layer content: placed by placeLayer, and no part of this scroll range
                }
                Slot cs = slots.get(c);
                if (cs == null) {
                    continue; // whitespace between flex items
                }
                float end = writeBoxes(c, x, y);
                ComputedStyle ccs = c instanceof Element ce ? ce.computed() : null;
                if (ccs != null && ccs.get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
                    continue;
                }
                childrenEnd = Math.max(childrenEnd, end);
                if (ccs == null || ccs.get(CssProperties.POSITION) != Keywords.Position.ABSOLUTE) {
                    float marginBottom = tree.getLayout(cs.id).margin().bottom;
                    inFlowEnd = Math.max(inFlowEnd, c.box().y() + c.box().height() + marginBottom);
                }
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

    /**
     * Places the shaped paragraph of {@code owner} in its content box: text fragments, atomic boxes and
     * inline elements' line rectangles. Returns the bottom of its lines and atomic boxes' contributions.
     */
    private float place(Node owner, Para p) {
        LayoutBox b = owner.box();
        Element container = owner instanceof Element e ? e : owner.parent();
        float cx = b.x() + b.borderLeft() + b.paddingLeft();
        float cy = b.y() + b.borderTop() + b.paddingTop();
        float boxWidth = b.width() - b.borderLeft() - b.paddingLeft() - b.paddingRight() - b.borderRight();
        float limit = Float.POSITIVE_INFINITY;
        if (p.ellipsis) {
            LayoutBox cb = container.box();
            limit = cb.x() + cb.width() - cb.borderRight();
        }
        for (Source s : p.sources) {
            if (s.node() instanceof TextNode t) {
                fragments.put(t, new ArrayList<>(2));
            }
        }
        // Per placed piece: item, line, start x, end x.
        int pieceCount = 0;
        float[] pieces = new float[16];
        float end = cy;
        float y = cy;
        List<TextShaper.Line> lines = p.shaped.lines();
        for (int li = 0; li < lines.size(); li++) {
            TextShaper.Line line = lines.get(li);
            List<TextShaper.Piece> shown = line.pieces();
            float x0;
            if (cx + line.width() > limit) {
                shown = shaper.ellipsize(line, p.items, limit - cx);
                x0 = cx;
            } else {
                x0 = cx + Math.max(0, Math.round((boxWidth - line.width()) * p.align));
            }
            for (TextShaper.Piece piece : shown) {
                float px = Math.round(x0 + piece.x());
                if ((pieceCount + 1) * 4 > pieces.length) {
                    pieces = Arrays.copyOf(pieces, pieces.length * 2);
                }
                pieces[pieceCount * 4] = piece.item();
                pieces[pieceCount * 4 + 1] = li;
                pieces[pieceCount * 4 + 2] = px;
                pieces[pieceCount * 4 + 3] = px + piece.width();
                pieceCount++;
                Source src = p.sources.get(piece.item());
                if (p.items.get(piece.item()) instanceof TextShaper.Run r) {
                    float top = y + line.baseline() - shaper.baseline(r.size());
                    fragments.get((TextNode) src.node()).add(new TextFragment(px, top, r.size(), piece.text(), src.span()));
                } else {
                    TextShaper.Box box = (TextShaper.Box) p.items.get(piece.item());
                    end = Math.max(end, placeAtom((Element) src.node(), px, y + line.baseline() - box.ascent()));
                }
            }
            if (shown != line.pieces()) {
                // Boxes the ellipsis cut off go past the padding box, where the clip hides them.
                for (TextShaper.Piece piece : line.pieces()) {
                    if (piece.text() == null && !shown.contains(piece)) {
                        TextShaper.Box box = (TextShaper.Box) p.items.get(piece.item());
                        placeAtom((Element) p.sources.get(piece.item()).node(), limit, y + line.baseline() - box.ascent());
                    }
                }
            }
            y += line.height();
        }
        end = Math.max(end, y);
        for (InlineRange r : p.inlines) {
            float[] rects = new float[0];
            int n = 0;
            for (int li = 0; li < lines.size(); li++) {
                float x0 = Float.POSITIVE_INFINITY;
                float x1 = Float.NEGATIVE_INFINITY;
                for (int k = 0; k < pieceCount; k++) {
                    int item = (int) pieces[k * 4];
                    if (pieces[k * 4 + 1] == li && item >= r.start() && item < r.end()) {
                        x0 = Math.min(x0, pieces[k * 4 + 2]);
                        x1 = Math.max(x1, pieces[k * 4 + 3]);
                    }
                }
                if (x0 <= x1) {
                    float top = cy;
                    for (int k = 0; k < li; k++) {
                        top += lines.get(k).height();
                    }
                    rects = Arrays.copyOf(rects, (n + 1) * 4);
                    rects[n * 4] = x0;
                    rects[n * 4 + 1] = top;
                    rects[n * 4 + 2] = x1 - x0;
                    rects[n * 4 + 3] = lines.get(li).height();
                    n++;
                }
            }
            r.element().box().setFragments(rects, cx, cy);
            r.element().setMaxScrollTop(0);
        }
        return end;
    }

    /** Puts an atomic box's margin box at {@code (x, top)}; returns its scroll contribution's bottom. */
    private float placeAtom(Element e, float x, float top) {
        ComputedStyle cs = e.computed();
        Layout l = tree.getLayout(slots.get(e).id);
        float bx = x + px(cs.get(CssProperties.MARGIN_LEFT));
        float by = Math.round(top + px(cs.get(CssProperties.MARGIN_TOP)));
        return writeBoxes(e, bx - l.location().x, by - l.location().y);
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
        // Every other display is a flex container to taffy: a block container has no taffy children.
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
