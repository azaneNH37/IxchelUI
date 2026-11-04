package com.azane.ixchel.core.html;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.FormFacet;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.event.InteractionFacet;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.layout.LayoutFacet;
import com.azane.ixchel.core.paint.PaintFacet;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.view.ContentModel;
import com.azane.ixchel.core.view.ViewCode;
import com.azane.ixchel.core.text.TextShaper;
import java.util.List;

final class SelectElement {
    private SelectElement() {}
    private static final float ARROW_SPACE = 10;
    static final ContentModel CONTENT = new ContentModel() {
        @Override public boolean ownsText() { return false; }
        @Override public boolean children(Element e, List<Node> children, Diagnostics d) {
            if (onlyOptions(children, true)) return true;
            d.report(ViewCode.INVALID_CHILDREN, e.pos(), "<select> may only contain <option> elements, directly or in <for> and <if>, and an <ix-tooltip>");
            return false;
        }
    };

    static final ContentModel OPTION_CONTENT = new ContentModel() {
        @Override public boolean children(Element e, List<Node> children, Diagnostics d) {
            for (Node n : children) if (!(n instanceof TextNode)) {
                d.report(ViewCode.INVALID_CHILDREN, e.pos(), "<option> may only contain text");
                return false;
            }
            return true;
        }
    };

    private static boolean onlyOptions(List<Node> children, boolean direct) {
        for (Node n : children) {
            if (blank(n)) continue;
            if (!(n instanceof Element e) || !(e.type().facet(FormFacet.KEY).option() || direct && tooltip(e)
                    || (e.tag().equals("for") || e.tag().equals("if")) && onlyOptions(e.children(), false))) return false;
        }
        return true;
    }

    private static boolean blank(Node n) { return n instanceof TextNode t && t.text().isBlank(); }

    private static boolean tooltip(Node n) {
        return n instanceof Element e && e.type().facet(LayerFacet.KEY).pointer() == LayerFacet.Pointer.TOOLTIP;
    }

    static final LayoutFacet LAYOUT = new LayoutFacet() {
        @Override public boolean atomic() { return true; }
        @Override public boolean measured() { return true; }
        @Override public boolean layoutChildren() { return false; }
        @Override public String measureKey(Element e) {
            StringBuilder labels = new StringBuilder();
            for (Node n : e.children()) {
                if (n instanceof Element o && o.type().facet(FormFacet.KEY).option()) {
                    labels.append(o.textContent().strip()).append('\n');
                }
            }
            return labels.toString();
        }
        @Override public float width(String labels, float size, TextShaper text) {
            float width = 0;
            for (String label : labels.split("\n", -1)) width = Math.max(width, text.width(label, size, false));
            return width + ARROW_SPACE;
        }
    };

    static final PaintFacet PAINT = new PaintFacet() {
        @Override public boolean paintChildren() { return false; }
        @Override public void paint(Element e, float y, DisplayList out, Context context) {
            paintSelect(e.control(), e.computed(), e.box(), y, out, context);
        }
    };

    static final InteractionFacet INTERACTION = new InteractionFacet() {
        @Override public boolean focusable() { return true; }
        @Override public boolean hitChildren() { return false; }
        @Override public boolean dragSourceAllowed() { return false; }
        @Override public void click(Element e, Actions a) { open(e, a); }
        @Override public boolean key(Element e, int key, int mods, Actions a) {
            boolean open = a.isOpen(e);
            if (key == FormEditing.KEY_UP || key == FormEditing.KEY_DOWN) {
                if (!open && key == FormEditing.KEY_DOWN && (mods & Keys.MOD_ALT) != 0) open(e, a);
                else if (FormEditing.stepSelect(e.control(), key == FormEditing.KEY_UP ? -1 : 1)) a.commit(e);
                return true;
            }
            if (FormEditing.isEnter(key) || key == FormEditing.KEY_SPACE) {
                if (open) a.close(e); else open(e, a);
                return true;
            }
            return false;
        }
    };

    static final InteractionFacet OPTION_INTERACTION = new InteractionFacet() {
        @Override public void click(Element e, Actions a) {
            Element select = e.parent();
            if (select == null || !a.isOpen(select)) return;
            String before = select.control().value();
            select.control().setValue(FormControl.optionValue(e));
            a.close(select);
            if (!select.control().value().equals(before)) a.commit(select);
        }
    };

    private static void paintSelect(FormControl c, ComputedStyle cs, LayoutBox b, float y, DisplayList out, PaintFacet.Context context) {
        float cx = b.x() + b.borderLeft() + b.paddingLeft();
        float cy = y + b.borderTop() + b.paddingTop();
        float cw = b.contentWidth();
        float ch = b.contentHeight();
        if (cw <= 0 || ch <= 0) {
            return;
        }
        float size = cs.get(CssProperties.FONT_SIZE).value();
        float lh = context.lineHeight(cs);
        int color = cs.get(CssProperties.COLOR);
        float ty = cy + Math.round((ch - lh) / 2) + Math.round((lh - context.fontHeight(size)) / 2);
        float labelWidth = Math.max(0, cw - ARROW_SPACE);
        out.add(new DrawCommand.PushClip(cx, cy, labelWidth, ch));
        context.text(out, cs, cx, ty, size, color, c.label());
        out.add(DrawCommand.PopClip.INSTANCE);
        // A 5px-wide down arrow: rows 5, 3, 1 wide, centered in the reserved space.
        float ax = cx + cw - ARROW_SPACE + 3;
        float ay = cy + Math.round((ch - 3) / 2);
        for (int row = 0; row < 3; row++) {
            context.rect(out, ax + row, ay + row, 5 - 2 * row, 1, color);
        }
    }

    private static void open(Element e, InteractionFacet.Actions a) { a.open(e); a.clickSound(e); }
    static final ElementType TYPE = ElementType.builder("select")
            .facet(ContentModel.KEY, CONTENT).facet(LayoutFacet.KEY, LAYOUT)
            .facet(PaintFacet.KEY, PAINT).facet(InteractionFacet.KEY, INTERACTION)
            .facet(LayerFacet.KEY, new LayerFacet(LayerFacet.Placement.BELOW_ANCHOR, LayerFacet.Pointer.NONE,
                    false, false, false, true, true, true, true))
            .facet(FormFacet.KEY, new FormFacet() {
                @Override public FormControl.Kind kind(Element e) { return FormControl.Kind.SELECT; }
                @Override public boolean optionOwner() { return true; }
                @Override public boolean binds(Element e, String property) { return property.equals("value"); }
            }).build();
    static final ElementType OPTION = ElementType.builder("option", "value")
            .facet(ContentModel.KEY, OPTION_CONTENT).facet(InteractionFacet.KEY, OPTION_INTERACTION)
            .facet(FormFacet.KEY, new FormFacet() {
                @Override public boolean option() { return true; }
                @Override public boolean checked(Element e) {
                    Element parent = e.parent();
                    return parent != null && parent.type().facet(FormFacet.KEY).optionOwner()
                            && parent.control().selectedOption() == e;
                }
            }).build();
}
