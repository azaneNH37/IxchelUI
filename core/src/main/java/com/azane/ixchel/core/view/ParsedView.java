package com.azane.ixchel.core.view;

import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Node;

import java.util.List;

/**
 * A parsed view: the kind its root declares, {@code null} when it has no valid root, a page's
 * top-level nodes, the stylesheet it links, {@code null} when none, and a component's description,
 * {@code null} for a page.
 */
public record ParsedView(Kind kind, List<Node> roots, Link stylesheet, ComponentDef component) {
    public ParsedView {
        roots = List.copyOf(roots);
    }

    /** What a view file's root declares it to be. */
    public enum Kind {
        /** {@code <page>}: shown by a screen or HUD. */
        PAGE,
        /** {@code <component name params events>}: used as a tag by the views that link it. */
        COMPONENT
    }

    /** A {@code <link href="…"/>}; {@code pos} is the position of the tag. */
    public record Link(String href, SourcePos pos) {
    }
}
