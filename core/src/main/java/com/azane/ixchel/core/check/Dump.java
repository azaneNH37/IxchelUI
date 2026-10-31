package com.azane.ixchel.core.check;

import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.runtime.UiDocument;

import java.util.Map;

/**
 * A document's tree as text, for reading what a failed check saw: one line per node, indented by
 * depth; an element with its tag, attributes, classes and laid-out border box (x, y, width, height,
 * then the scroll offset when it has one), a text node quoted. The top layer's entries follow.
 *
 * <pre>
 * &lt;div class="row" x=10.0 y=20.0 w=100.0 h=18.0&gt;
 *   "Iron Ore"
 * </pre>
 */
public final class Dump {
    private Dump() {
    }

    public static String of(UiDocument d) {
        StringBuilder out = new StringBuilder();
        node(out, d.root(), 0);
        if (!d.topLayer().entries().isEmpty()) {
            out.append("top layer:\n");
            for (Element e : d.topLayer().entries()) {
                out.append("  ");
                element(out, e);
            }
        }
        return out.toString();
    }

    private static void node(StringBuilder out, Node n, int depth) {
        out.append("  ".repeat(depth));
        if (n instanceof TextNode t) {
            out.append('"').append(t.text().replace("\n", "\\n")).append("\"\n");
            return;
        }
        Element e = (Element) n;
        element(out, e);
        for (Node c : e.children()) {
            node(out, c, depth + 1);
        }
    }

    private static void element(StringBuilder out, Element e) {
        out.append('<').append(e.tag());
        for (Map.Entry<String, String> a : e.attributes().entrySet()) {
            out.append(' ').append(a.getKey()).append("=\"").append(a.getValue()).append('"');
        }
        if (!e.classes().isEmpty()) {
            out.append(" class=\"").append(String.join(" ", e.classes())).append('"');
        }
        LayoutBox b = e.box();
        out.append(" x=").append(b.x()).append(" y=").append(b.y()).append(" w=").append(b.width()).append(" h=")
                .append(b.height());
        if (e.scrollTop() != 0) {
            out.append(" scrollTop=").append(e.scrollTop());
        }
        out.append(">\n");
    }
}
