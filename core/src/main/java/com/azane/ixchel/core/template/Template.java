package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.SourcePos;
import com.azane.ixchel.core.dom.Node;

import java.util.List;

/** A parsed template: top-level nodes plus the raw CSS of its inline {@code <style>} elements. */
public record Template(List<Node> roots, List<StyleSource> styles) {
    public Template {
        roots = List.copyOf(roots);
        styles = List.copyOf(styles);
    }

    /** {@code start} is the position of the first character of {@code css}. */
    public record StyleSource(String css, SourcePos start) {
    }
}
