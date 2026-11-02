package com.azane.ixchel.core.html;

import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.template.ContentModel;
import com.azane.ixchel.core.template.TemplateCode;
import java.util.List;

final class HtmlContent {
    private HtmlContent() {}
    static class Replaced implements ContentModel {
        @Override public boolean ownsText() { return false; }
        @Override public boolean preserveWhitespace() { return false; }
    }
    static class TooltipOnly extends Replaced {
        @Override public boolean children(Element e, List<Node> children, Diagnostics d) {
            for (Node n : children) {
                if (!blank(n) && !tooltip(n)) {
                    d.report(TemplateCode.INVALID_CHILDREN, n.pos(), e.type() + " may only contain an <ix-tooltip>");
                    return false;
                }
            }
            return true;
        }
    }
    static final ContentModel NATIVE = new Replaced();
    static final ContentModel LINES = new Replaced() {
        @Override public boolean children(Element e, List<Node> children, Diagnostics d) {
            for (Node n : children) if (!blank(n)) {
                d.report(TemplateCode.INVALID_CHILDREN, e.pos(), e.type() + " must not have children: its lines are filled in");
                return false;
            }
            return true;
        }
    };
    static ContentModel pointer(String description) {
        return new ContentModel() {
            @Override public boolean pointerBoundary() { return true; }
            @Override public void placement(Element e, boolean topLevel, int depth, Diagnostics d) {
                if (topLevel || depth > 0) d.report(TemplateCode.MISPLACED_ELEMENT, e.pos(), topLevel
                        ? e.type() + " is " + description + " of its parent element; it cannot be at the top level"
                        : e.type() + " cannot be inside an <ix-tooltip> or <ix-drag-preview>");
            }
        };
    }
    private static boolean blank(Node n) { return n instanceof TextNode t && t.text().isBlank(); }
    private static boolean tooltip(Node n) {
        return n instanceof Element e && e.type().facet(LayerFacet.KEY).pointer() == LayerFacet.Pointer.TOOLTIP;
    }
}
