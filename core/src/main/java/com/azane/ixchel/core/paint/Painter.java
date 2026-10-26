package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.template.TagRegistry;
import com.azane.ixchel.core.text.TextShaper;

import java.util.List;

/** Turns the laid-out tree into draw commands in document order (later commands paint on top). */
public final class Painter {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private final LayoutEngine layout;
    private final TagRegistry tags;

    public Painter(LayoutEngine layout, TagRegistry tags) {
        this.layout = layout;
        this.tags = tags;
    }

    /** Clears {@code out} and paints the children of the implicit root into it. */
    public void paint(Element root, DisplayList out) {
        out.clear();
        paintChildren(root, out);
    }

    private void paintChildren(Element e, DisplayList out) {
        for (Node c : e.children()) {
            if (c instanceof Element child) {
                paintElement(child, out);
            } else {
                paintText((TextNode) c, e.computed(), out);
            }
        }
    }

    private void paintElement(Element e, DisplayList out) {
        ComputedStyle cs = e.computed();
        if (cs.get(CssProperties.DISPLAY) == Keywords.Display.NONE) {
            return;
        }
        LayoutBox b = e.box();
        int bg = cs.get(CssProperties.BACKGROUND_COLOR);
        if (bg >>> 24 != 0) {
            out.add(new DrawCommand.Rect(b.x(), b.y(), b.width(), b.height(), bg));
        }
        if (e.tag().equals("img")) {
            out.add(new DrawCommand.Image(b.x(), b.y(), b.width(), b.height(), e.attribute("src"), OPAQUE_WHITE));
        } else if (e.payload() != null && tags.payloadAttribute(e.tag()) != null) {
            out.add(new DrawCommand.Custom(e.tag(), e.payload(), b.x(), b.y(), b.width(), b.height()));
        }
        boolean clip = cs.get(CssProperties.OVERFLOW) == Keywords.Overflow.HIDDEN;
        if (clip) {
            out.add(new DrawCommand.PushClip(b.x(), b.y(), b.width(), b.height()));
        }
        paintChildren(e, out);
        if (clip) {
            out.add(DrawCommand.PopClip.INSTANCE);
        }
    }

    private void paintText(TextNode t, ComputedStyle parent, DisplayList out) {
        float size = parent.get(CssProperties.FONT_SIZE).value();
        int color = parent.get(CssProperties.COLOR);
        float lh = layout.lineHeight(size);
        List<TextShaper.Line> lines = layout.lines(t);
        LayoutBox b = t.box();
        for (int i = 0; i < lines.size(); i++) {
            out.add(new DrawCommand.Text(b.x(), b.y() + i * lh, size, color, lines.get(i).text()));
        }
    }
}
