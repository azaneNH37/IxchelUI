package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Node;

import java.util.List;

/**
 * A parsed template: top-level nodes plus its stylesheets, {@code <link rel="stylesheet">} references
 * and inline {@code <style>} bodies, in document order.
 */
public record Template(List<Node> roots, List<StyleSource> styles) {
    public Template {
        roots = List.copyOf(roots);
        styles = List.copyOf(styles);
    }

    /** One top-level stylesheet of the template, in the order it appears. */
    public sealed interface StyleSource permits Inline, Link {
    }

    /** The raw CSS of a {@code <style>} element; {@code start} is the position of its first character. */
    public record Inline(String css, SourcePos start) implements StyleSource {
    }

    /** A {@code <link rel="stylesheet" href="…"/>}; {@code pos} is the position of the tag. */
    public record Link(String href, SourcePos pos) implements StyleSource {
    }
}
