package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssImage;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.property.PropertyDef;
import com.azane.ixchel.core.css.property.Transform;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;

import java.util.HashSet;
import java.util.Set;

/**
 * What layout makes of each element, from its computed style and place in the tree alone: how a box
 * lays out its children, what a child of a block container or of an inline box is, and the errors of
 * inline content. {@link LayoutEngine} asks it at every node it lays out; {@link #check} walks a
 * document the same way without laying anything out, for a lint, which has no fonts or viewport.
 */
public final class Formatting {
    /** How a box the layout syncs lays out its children. */
    enum Mode {
        /** Not at all: its type draws or measures it whole. */
        LEAF,
        /** As flex or grid items; also the document root's. */
        ITEMS,
        /** A block container with block-level children: those boxes, and the inline content between them. */
        FLOW,
        /** A block container holding inline content only: one paragraph. */
        PARAGRAPH
    }

    /** What a child of a {@link Mode#FLOW} container is. */
    enum FlowChild {
        /** A box of its own: block-level, absolutely positioned, {@code display: none} or drawn in the top layer. */
        BOX,
        /** Inline content of the run it sits in. */
        INLINE
    }

    /** What a node met in inline content is. */
    enum InlineChild {
        /** Not laid out here: {@code display: none} or drawn in the top layer. */
        SKIPPED,
        TEXT,
        /** An inline element, its content in the same lines. */
        INLINE_BOX,
        /** A box laid out on its own and placed on a line. */
        ATOM
    }

    private final Diagnostics diag;
    private final Set<String> reported = new HashSet<>();

    /** {@code diag} takes the errors of inline content, each once. */
    public Formatting(Diagnostics diag) {
        this.diag = diag;
    }

    /**
     * Reports the errors of inline content in the document under {@code root}, reaching every element
     * layout would lay out with no top layer entry open.
     */
    public void check(Element root) {
        box(root, true);
    }

    private void box(Element e, boolean isRoot) {
        Mode mode = mode(e, isRoot);
        if (mode == Mode.LEAF) {
            return;
        }
        for (int i = -1; i <= e.children().size(); i++) {
            Node c = i < 0 ? e.before() : i == e.children().size() ? e.after() : e.children().get(i);
            if (c == null) {
                continue;
            }
            if (mode == Mode.PARAGRAPH || mode == Mode.FLOW && flowChild(c) == FlowChild.INLINE) {
                inline(e, c);
            } else if (c instanceof Element d && !d.rendersInTopLayer()) {
                box(d, false);
            }
        }
    }

    private void inline(Element parent, Node c) {
        switch (inlineChild(parent, c)) {
            case INLINE_BOX -> {
                Element e = (Element) c;
                for (int i = -1; i <= e.children().size(); i++) {
                    Node d = i < 0 ? e.before() : i == e.children().size() ? e.after() : e.children().get(i);
                    if (d != null) {
                        inline(e, d);
                    }
                }
            }
            case ATOM -> box((Element) c, false);
            default -> {
            }
        }
    }

    static Mode mode(Element e, boolean isRoot) {
        if (!e.type().facet(LayoutFacet.KEY).layoutChildren()) {
            return Mode.LEAF;
        }
        if (isRoot || !isBlockContainer(e)) {
            return Mode.ITEMS;
        }
        return hasBlockLevelChild(e) ? Mode.FLOW : Mode.PARAGRAPH;
    }

    static FlowChild flowChild(Node c) {
        return c instanceof Element d && (d.rendersInTopLayer() || d.computed().get(CssProperties.DISPLAY) == Keywords.Display.NONE
                || isFlowChild(d)) ? FlowChild.BOX : FlowChild.INLINE;
    }

    /** What {@code c}, in the inline content of {@code parent}, is; reports it when inline content cannot hold it. */
    InlineChild inlineChild(Element parent, Node c) {
        if (c instanceof TextNode) {
            return InlineChild.TEXT;
        }
        Element e = (Element) c;
        ComputedStyle cs = e.computed();
        Keywords.Display d = cs.get(CssProperties.DISPLAY);
        if (e.rendersInTopLayer() || d == Keywords.Display.NONE) {
            return InlineChild.SKIPPED;
        }
        if (d == Keywords.Display.INLINE && !isReplaced(e)) {
            checkInlineBox(e, cs);
            return InlineChild.INLINE_BOX;
        }
        if (d == Keywords.Display.FLEX || d == Keywords.Display.GRID || d == Keywords.Display.BLOCK) {
            report(e, LayoutCode.BLOCK_IN_INLINE, "<" + e.tag() + "> has display: " + d.name().toLowerCase() + " inside <" + parent.tag() + ">, which is display: inline;"
                    + " an inline element holds only text and inline content, so use display: inline-block");
        } else if (cs.get(CssProperties.POSITION) == Keywords.Position.ABSOLUTE) {
            report(e, LayoutCode.ABSOLUTE_IN_INLINE, "<" + e.tag() + "> is absolutely positioned inside <" + parent.tag() + ">, which is display: inline and cannot contain it");
        }
        return InlineChild.ATOM;
    }

    /** A box a block container lays out as a child of its own rather than in a line: block-level or absolutely positioned. */
    static boolean isFlowChild(Element e) {
        ComputedStyle cs = e.computed();
        Keywords.Display d = cs.get(CssProperties.DISPLAY);
        return d == Keywords.Display.BLOCK || d == Keywords.Display.FLEX || d == Keywords.Display.GRID
                || d != Keywords.Display.NONE && cs.get(CssProperties.POSITION) == Keywords.Position.ABSOLUTE;
    }

    private static boolean hasBlockLevelChild(Element e) {
        if (e.before() != null && isFlowChild(e.before()) || e.after() != null && isFlowChild(e.after())) {
            return true;
        }
        for (Node c : e.children()) {
            if (c instanceof Element d && !d.rendersInTopLayer() && isFlowChild(d)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isReplaced(Element e) {
        return e.type().facet(LayoutFacet.KEY).atomic();
    }

    /** An element whose content is a paragraph, where flex or grid layout or an inline formatting context lays out the element itself. */
    private static boolean isBlockContainer(Element e) {
        Keywords.Display d = e.computed().get(CssProperties.DISPLAY);
        return d != Keywords.Display.FLEX && d != Keywords.Display.GRID && d != Keywords.Display.NONE && !isReplaced(e);
    }

    /** An inline element's box is cut across lines, which CSS paints per fragment; not supported (see {@code inline-block}). */
    private void checkInlineBox(Element e, ComputedStyle cs) {
        String found = null;
        if (cs.get(CssProperties.BACKGROUND_COLOR) >>> 24 != 0
                || cs.get(CssProperties.BACKGROUND_IMAGE).stream().anyMatch(i -> i != CssImage.NONE)) {
            found = "background";
        } else if (!cs.get(CssProperties.BOX_SHADOW).isEmpty()) {
            found = "box-shadow";
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
            if (l.unit() != Length.Unit.PX || l.value() != 0) {
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
}
