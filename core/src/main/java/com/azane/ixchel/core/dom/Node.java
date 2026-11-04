package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.diag.SourcePos;

/** A tree node: an {@link Element} or a run of {@link TextNode text}. */
public abstract sealed class Node permits Element, TextNode {
    private final SourcePos pos;
    private final LayoutBox box = new LayoutBox();
    Element parent;

    Node(SourcePos pos) {
        this.pos = pos;
    }

    public Element parent() {
        return parent;
    }

    public SourcePos pos() {
        return pos;
    }

    /** Written by the layout pass, read by paint and hit testing. */
    public LayoutBox box() {
        return box;
    }

    /**
     * A detached deep copy of what the view gave this node: tag, classes, attributes, inline
     * style, directives and children for an element, the text for a text node. Interaction state,
     * payloads, listeners and computed results are not copied.
     */
    public abstract Node copyTemplate();
}
