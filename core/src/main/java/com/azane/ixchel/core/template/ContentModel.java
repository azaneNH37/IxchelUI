package com.azane.ixchel.core.template;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.Facet;
import com.azane.ixchel.core.dom.Node;
import java.util.List;

/** Element-specific validation, independent of template syntax and component expansion. */
public interface ContentModel {
    Facet<ContentModel> KEY = new Facet<>(new ContentModel() {});
    default void attributes(Element e, Diagnostics diagnostics) {}
    default void placement(Element e, boolean topLevel, int pointerDepth, Diagnostics diagnostics) {}
    default boolean children(Element e, List<Node> children, Diagnostics diagnostics) { return true; }
    default boolean preserveWhitespace() { return true; }
    default boolean ownsText() { return true; }
    default boolean pointerBoundary() { return false; }
}
