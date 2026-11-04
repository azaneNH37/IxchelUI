package com.azane.ixchel.core.html;

import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.ElementType;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.FormFacet;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.TextField;
import com.azane.ixchel.core.event.InteractionFacet;
import com.azane.ixchel.core.event.Keys;
import com.azane.ixchel.core.layout.LayoutFacet;
import com.azane.ixchel.core.paint.PaintFacet;
import com.azane.ixchel.core.paint.DisplayList;
import com.azane.ixchel.core.paint.DrawCommand;
import com.azane.ixchel.core.view.ContentModel;
import com.azane.ixchel.core.view.ViewCode;

final class InputElement {
    private InputElement() {}
    private static final int SELECTION_COLOR = 0x803399FF;
    private static final int TRACK_COLOR_RANGE = 0xFF505050;
    static final ContentModel CONTENT = new HtmlContent.TooltipOnly() {
        @Override public void attributes(Element e, Diagnostics d) {
            if (FormControl.Kind.ofInputType(e.attribute("type")) == null) {
                d.report(ViewCode.UNSUPPORTED_INPUT_TYPE, e.pos(), "unsupported <input type=\"" + e.attribute("type")
                        + "\">; supported: text, number, checkbox, range");
            }
        }
    };

    static final LayoutFacet LAYOUT = new LayoutFacet() {
        @Override public boolean atomic() { return true; }
        @Override public boolean measured() { return true; }
    };

    static final PaintFacet PAINT = new PaintFacet() {
        @Override public void paint(Element e, float y, DisplayList out, Context context) {
            if (e.control() == null) return;
            switch (e.control().kind()) {
                case TEXT, NUMBER -> paintInput(e, e.computed(), e.box(), y, out, context);
                case CHECKBOX -> paintCheckbox(e, e.computed(), e.box(), y, out, context);
                case RANGE -> paintRange(e.control(), e.computed(), e.box(), y, out, context);
                default -> throw new IllegalStateException("not an input control");
            }
        }
    };

    static final InteractionFacet INTERACTION = new InteractionFacet() {
        @Override public boolean focusable() { return true; }
        @Override public boolean dragSourceAllowed() { return false; }
        @Override public boolean capturesKeys(Element e) { return e.textField() != null; }
        @Override public void beforePress(Element e, float x, int mods, Actions a) {
            if (e.textField() != null) placeCaret(e, x, (mods & Keys.MOD_SHIFT) != 0, a);
        }
        @Override public boolean press(Element e, float x, Actions a) {
            if (e.control().kind() != FormControl.Kind.RANGE) return false;
            e.control().beginEdit();
            move(e, x, a);
            return true;
        }
        @Override public void move(Element e, float x, Actions a) {
            if (FormEditing.rangeAt(e, x)) a.input(e);
        }
        @Override public void release(Element e, Actions a) {
            if (e.control().commitEdit()) a.change(e);
        }
        @Override public void focus(Element e, Actions a) {
            if (e.textField() != null) e.control().beginEdit();
        }
        @Override public void blur(Element e, Actions a) {
            if (e.textField() != null && e.control().commitEdit()) a.change(e);
        }
        @Override public void click(Element e, Actions a) {
            if (e.control().kind() == FormControl.Kind.CHECKBOX) toggle(e, a);
        }
        @Override public boolean key(Element e, int key, int mods, Actions a) {
            FormControl c = e.control();
            return switch (c.kind()) {
                case TEXT, NUMBER -> textKey(e, key, mods, a);
                case CHECKBOX -> {
                    if (key != FormEditing.KEY_SPACE) yield false;
                    toggle(e, a);
                    yield true;
                }
                case RANGE -> {
                    int result = FormEditing.rangeKey(c, key);
                    if (result > 0) a.commit(e);
                    yield result >= 0;
                }
                default -> false;
            };
        }
        @Override public boolean character(Element e, int codePoint, Actions a) {
            TextField f = e.textField();
            if (f == null) return false;
            if (TextEditing.insert(f, Character.toString(codePoint), maxLength(e), e.control().kind() == FormControl.Kind.NUMBER)) a.input(e);
            return true;
        }
    };

    private static void paintInput(Element e, ComputedStyle cs, LayoutBox b, float y, DisplayList out, PaintFacet.Context context) {
        float cx = b.x() + b.borderLeft() + b.paddingLeft();
        float cy = y + b.borderTop() + b.paddingTop();
        float cw = b.width() - b.borderLeft() - b.paddingLeft() - b.paddingRight() - b.borderRight();
        float ch = b.height() - b.borderTop() - b.paddingTop() - b.paddingBottom() - b.borderBottom();
        if (cw <= 0 || ch <= 0) {
            return;
        }
        TextField f = e.textField();
        String v = f.value();
        float size = cs.get(CssProperties.FONT_SIZE).value();
        float lh = context.lineHeight(cs);
        int color = cs.get(CssProperties.COLOR);
        boolean focused = e.hasState(Element.FOCUS);
        float caretX = context.textWidth(v.substring(0, f.caret()), size);
        // Room for the 1px caret after the last character; no empty space past the end while scrolled.
        float scroll = Math.min(f.scrollX(), Math.max(0, context.textWidth(v, size) + 1 - cw));
        if (caretX + 1 - scroll > cw) {
            scroll = caretX + 1 - cw;
        }
        scroll = Math.round(Math.max(0, Math.min(scroll, caretX)));
        f.setScrollX(scroll);
        float tx = cx - scroll;
        float ty = cy + Math.round((ch - lh) / 2);
        float glyphY = ty + Math.round((lh - context.fontHeight(size)) / 2);
        out.add(new DrawCommand.PushClip(cx, cy, cw, ch));
        String placeholder = e.attribute("placeholder");
        if (!v.isEmpty()) {
            if (focused && f.hasSelection()) {
                float x0 = Math.round(tx + context.textWidth(v.substring(0, f.selectionStart()), size));
                float x1 = Math.round(tx + context.textWidth(v.substring(0, f.selectionEnd()), size));
                context.rect(out, x0, ty, x1 - x0, lh, SELECTION_COLOR);
            }
            context.text(out, cs, tx, glyphY, size, color, v);
        } else if (placeholder != null && !placeholder.isEmpty()) {
            context.text(out, cs, tx, glyphY, size, Math.round((color >>> 24) * 0.5f) << 24 | color & 0xFFFFFF, placeholder);
        }
        if (focused && context.caretVisible()) {
            context.rect(out, Math.round(tx + caretX), ty, 1, lh, color);
        }
        out.add(DrawCommand.PopClip.INSTANCE);
    }

    private static void paintCheckbox(Element e, ComputedStyle cs, LayoutBox b, float y, DisplayList out, PaintFacet.Context context) {
        if (e.control().checked()) {
            context.rect(out, b.x() + b.borderLeft() + b.paddingLeft(), y + b.borderTop() + b.paddingTop(),
                    b.contentWidth(), b.contentHeight(), cs.get(CssProperties.ACCENT_COLOR));
        }
    }

    private static void paintRange(FormControl c, ComputedStyle cs, LayoutBox b, float y, DisplayList out, PaintFacet.Context context) {
        float cx = b.x() + b.borderLeft() + b.paddingLeft();
        float cy = y + b.borderTop() + b.paddingTop();
        float cw = b.contentWidth();
        float ch = b.contentHeight();
        if (cw <= FormControl.THUMB_WIDTH || ch <= 0) {
            return;
        }
        int accent = cs.get(CssProperties.ACCENT_COLOR);
        float trackY = cy + Math.round((ch - 2) / 2);
        float thumbX = cx + Math.round(c.fraction() * (cw - FormControl.THUMB_WIDTH));
        context.rect(out, cx, trackY, cw, 2, TRACK_COLOR_RANGE);
        context.rect(out, cx, trackY, thumbX - cx, 2, accent);
        context.rect(out, thumbX, cy, FormControl.THUMB_WIDTH, ch, accent);
    }

    private static void toggle(Element e, InteractionFacet.Actions a) {
        FormEditing.toggle(e.control());
        a.clickSound(e);
        a.commit(e);
    }

    private static boolean textKey(Element e, int key, int mods, InteractionFacet.Actions a) {
        FormControl c = e.control();
        if (FormEditing.isEnter(key)) {
            if (c.commitEdit()) a.change(e);
            return true;
        }
        boolean numeric = c.kind() == FormControl.Kind.NUMBER;
        if (numeric && (key == FormEditing.KEY_UP || key == FormEditing.KEY_DOWN)) {
            if (FormEditing.stepNumber(e, key == FormEditing.KEY_UP ? 1 : -1)) a.input(e);
            return true;
        }
        if (TextEditing.key(c.text(), key, mods, a.clipboard(), maxLength(e), numeric)) a.input(e);
        return true;
    }

    private static int maxLength(Element e) {
        String raw = e.attribute("maxlength");
        if (raw == null) return -1;
        try { return Math.max(0, Integer.parseInt(raw.trim())); }
        catch (NumberFormatException ex) { return -1; }
    }

    private static void placeCaret(Element e, float x, boolean extend, InteractionFacet.Actions a) {
        TextField field = e.textField();
        LayoutBox b = e.box();
        float size = e.computed().get(CssProperties.FONT_SIZE).value();
        float at = x - (b.x() + b.borderLeft() + b.paddingLeft()) + field.scrollX();
        String v = field.value();
        int best = 0;
        float distance = Math.abs(at);
        for (int i = 0; i < v.length();) {
            i = Character.offsetByCodePoints(v, i, 1);
            float d = Math.abs(a.textWidth(v.substring(0, i), size) - at);
            if (d >= distance) break;
            best = i;
            distance = d;
        }
        field.select(extend ? field.anchor() : best, best);
    }
    static final ElementType TYPE = ElementType.builder("input", "type", "placeholder", "maxlength", "min", "max", "step")
            .facet(ContentModel.KEY, CONTENT).facet(LayoutFacet.KEY, LAYOUT)
            .facet(PaintFacet.KEY, PAINT).facet(InteractionFacet.KEY, INTERACTION)
            .facet(FormFacet.KEY, new FormFacet() {
                @Override public FormControl.Kind kind(Element e) { return FormControl.Kind.ofInputType(e.attribute("type")); }
                @Override public boolean binds(Element e, String property) {
                    FormControl.Kind kind = kind(e);
                    return kind != null && (kind == FormControl.Kind.CHECKBOX ? property.equals("checked") : property.equals("value"));
                }
            }).build();
}
