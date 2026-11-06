package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.css.property.Affine;
import com.azane.ixchel.core.css.property.BackgroundSize;
import com.azane.ixchel.core.css.property.BorderImageSlice;
import com.azane.ixchel.core.css.property.BorderImageWidth;
import com.azane.ixchel.core.css.property.BoxShadow;
import com.azane.ixchel.core.css.property.ComputedStyle;
import com.azane.ixchel.core.css.property.CssProperties;
import com.azane.ixchel.core.css.property.CornerRadius;
import com.azane.ixchel.core.css.property.CssImage;
import com.azane.ixchel.core.css.property.Filter;
import com.azane.ixchel.core.css.property.Keywords;
import com.azane.ixchel.core.css.property.Length;
import com.azane.ixchel.core.css.property.ScrollbarColor;
import com.azane.ixchel.core.css.property.TextShadow;
import com.azane.ixchel.core.css.property.Transform;
import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostics;
import com.azane.ixchel.core.dom.Element;
import com.azane.ixchel.core.dom.LayerFacet;
import com.azane.ixchel.core.dom.FormControl;
import com.azane.ixchel.core.dom.LayoutBox;
import com.azane.ixchel.core.dom.Node;
import com.azane.ixchel.core.dom.StyledText;
import com.azane.ixchel.core.dom.TextNode;
import com.azane.ixchel.core.dom.TopLayer;
import com.azane.ixchel.core.layout.LayoutEngine;
import com.azane.ixchel.core.effect.Effect;
import com.azane.ixchel.core.effect.EffectCode;
import com.azane.ixchel.core.effect.Effects;
import com.azane.ixchel.core.layout.Scrollbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the laid-out tree into draw commands in document order (later commands paint on top): per
 * element the outer {@code box-shadow}s, the background color, the background layers bottom to top,
 * the inset shadows, the border (or the border image in its place), the content, then its
 * {@code ::before} box, its children, its {@code ::after} box and last its {@code outline}; with
 * a multiplying bottom image layer the color tints it instead of painting under it. An image layer
 * becomes one {@link DrawCommand.Image} per visible tile, already clipped to the border box, so the
 * host only draws texture regions; a gradient layer one {@link DrawCommand.Fill} per tile, clipped
 * to the tile. A border side is one {@link DrawCommand.Rect} ({@code solid}
 * only); the top and bottom sides cover the corners. With a rounded corner the background color is
 * a {@link DrawCommand.Fill} and the border one {@link DrawCommand.Border}.
 *
 * <p>{@code opacity} below 1, a {@code filter}, a {@code mix-blend-mode} other than {@code normal}, a
 * {@code clip-path} or a {@code mask-image} paint the element as a group: its commands, transform
 * included, go between a {@link DrawCommand.PushLayer} and a {@link DrawCommand.PopLayer}, and the
 * host filters what they drew and composites it inside the clip shape, times the mask, at that
 * opacity with that blend mode, so overlapping descendants and native elements are treated as one.
 * The mask layers follow a {@link DrawCommand.Mask} in the same layer. The layer's rectangle bounds
 * what its commands draw (see {@link DisplayList.Bounds}) grown by how far the filters spread it, cut
 * to the clip shape and (with a mask) the border box. A {@code backdrop-filter} is a
 * {@link DrawCommand.Backdrop} before the element's box. An element with opacity 0 paints nothing but
 * still takes pointer events, as in CSS. {@code background-clip: text} puts the background alone on
 * a layer masked by the text.
 *
 * <p>A {@code paint()} background layer is one {@link DrawCommand.Paint} over its clip box (the border
 * box unless {@code background-clip} says otherwise), whatever
 * the layer's size, position and repeat; a {@code -ix-paint()} filter becomes one of the layer's
 * {@link DrawCommand.LayerPaint}s. An effect name the document does not link, or of the other kind,
 * reaches here only through {@code var()} or a binding; it is reported once and the layer or filter
 * is left out.
 *
 * <p>A scroll container ({@code overflow} other than {@code visible}) clips its descendants to its
 * padding box and paints them {@link Element#scrollTop()} higher, then its {@link Scrollbar} over
 * them in {@code scrollbar-color}.
 *
 * <p>An {@code <input>} paints its value on one line, clipped to its content box and centered in it
 * vertically, scrolled sideways so the caret stays in view; empty, its {@code placeholder} at half
 * the alpha of {@code color}. Focused, the selection is a fixed translucent blue under the text and
 * the caret a 1px line of {@code color}, drawn while {@linkplain #setCaretVisible the blink} shows it.
 * A checked checkbox fills its content box with {@code accent-color}. A range draws a 2px track
 * across its content box, filled with {@code accent-color} up to the value, and a
 * {@value FormControl#THUMB_WIDTH}px thumb of the content box's height in {@code accent-color}. A
 * {@code <select>} draws its selected option's label like an input's value and a small down arrow of
 * {@code color} at the right of its content box; its options are not painted in place.
 *
 * <p>After the tree, each {@link TopLayer} entry in order, with no clip and full opacity: an open
 * dialog over a translucent black backdrop covering the viewport, the options of an open select.
 * Neither is painted where it sits in the tree.
 *
 * <p>{@code transform} wraps the element's commands in a {@link DrawCommand.PushTransform}; layout
 * and scroll offsets are not affected. Clips inside a transformed subtree are rectangles in its local
 * coordinates; under a rotation the host clips to their bounding box.
 */
public final class Painter implements PaintFacet.Context {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Bounds the commands one background may emit, so a tiny repeated tile cannot stall a frame. */
    private static final int MAX_TILES = 16384;
    private static final int BACKDROP_COLOR = 0x80000000;

    private final LayoutEngine layout;
    private final ImageMeasurer images;
    private final Effects effects;
    private final Diagnostics diag;
    private final Set<String> reported = new HashSet<>();
    private final DisplayList.Bounds bounds;
    private boolean caretVisible;

    /**
     * @param effects the document's effects, which {@code paint()} and {@code -ix-paint()} name
     * @param diag    receives paint-time errors (an image that cannot be measured), each reported once
     */
    public Painter(LayoutEngine layout, ImageMeasurer images, Effects effects, Diagnostics diag) {
        this.layout = layout;
        this.images = images;
        this.effects = effects;
        this.diag = diag;
        this.bounds = new DisplayList.Bounds(layout::textWidth);
    }

    /** Whether the focused input's caret is drawn: the visible half of its blink. */
    public boolean caretVisible() {
        return caretVisible;
    }

    public void setCaretVisible(boolean visible) {
        caretVisible = visible;
    }

    @Override
    public float textWidth(String text, float size) { return layout.textWidth(text, size); }

    @Override
    public float lineHeight(ComputedStyle cs) { return layout.lineHeight(cs); }

    @Override
    public float fontHeight(float size) { return layout.fontHeight(size); }

    /** Clears {@code out} and paints the children of the implicit root into it, then the top layer's entries. */
    public void paint(Element root, TopLayer top, DisplayList out) {
        out.clear();
        paintChildren(root, 0, out);
        List<Element> entries = top.entries();
        for (int i = 0; i < entries.size(); i++) {
            Element entry = entries.get(i);
            LayerFacet presentation = entry.type().facet(LayerFacet.KEY);
            if (presentation.modal()) {
                rect(out, 0, 0, root.box().width(), root.box().height(), BACKDROP_COLOR);
            }
            for (int j = 0; j < presentation.contentCount(entry); j++) {
                Element child = presentation.contentAt(entry, j);
                if (child != null) paintElement(child, 0, out);
            }
        }
        root.clearRepaint();
    }

    /** Clears {@code out} and paints {@code top}'s element at the pointer into it; empty when none is shown. */
    public void paintAtPointer(TopLayer top, DisplayList out) {
        out.clear();
        if (top.atPointer() != null) {
            paintElement(top.atPointer(), 0, out);
        }
    }

    /**
     * {@code dy}: how far scroll containers above move these children up. The {@code ::before} box
     * paints under the children, {@code ::after} over them.
     */
    private void paintChildren(Element e, float dy, DisplayList out) {
        if (e.before() != null) {
            paintElement(e.before(), dy, out);
        }
        // Indexed: the unmodifiable view allocates two objects per for-each, on every repaint.
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            Node c = children.get(i);
            if (c instanceof Element child) {
                if (!child.rendersInTopLayer()) {
                    paintElement(child, dy, out);
                }
            } else {
                paintText((TextNode) c, e.computed(), dy, out, false);
            }
        }
        if (e.after() != null) {
            paintElement(e.after(), dy, out);
        }
    }

    private void paintElement(Element e, float dy, DisplayList out) {
        ComputedStyle cs = e.computed();
        float opacity = Math.min(1, cs.get(CssProperties.OPACITY));
        if (cs.get(CssProperties.DISPLAY) == Keywords.Display.NONE || opacity <= 0) {
            return;
        }
        List<Filter> filters = cs.get(CssProperties.FILTER);
        Keywords.MixBlendMode blend = cs.get(CssProperties.MIX_BLEND_MODE);
        LayoutBox b = e.box();
        ClipShape clip = ClipShape.of(cs.get(CssProperties.CLIP_PATH), b.x(), b.y() - dy, b.width(), b.height());
        boolean masked = hasImage(cs.get(CssProperties.MASK_IMAGE));
        boolean group = opacity < 1 || !filters.isEmpty() || blend != Keywords.MixBlendMode.NORMAL || clip != null || masked;
        int start = out.size();
        if (group) {
            out.add(DrawCommand.PopLayer.INSTANCE); // replaced by the layer once its bounds are known
        }
        Affine m = Transform.of(cs, b.x(), b.y() - dy, b.width(), b.height());
        if (m != null) {
            out.add(new DrawCommand.PushTransform(m));
        }
        paintBox(e, cs, dy, group, out);
        paintOutline(cs, b, b.y() - dy, out);
        if (m != null) {
            out.add(DrawCommand.PopTransform.INSTANCE);
        }
        if (group) {
            float[] r = bounds.of(out, start + 1, out.size());
            if (r == null) {
                out.truncate(start);
                return;
            }
            float spread = 0;
            for (int i = 0; i < filters.size(); i++) {
                spread += filters.get(i).reach();
            }
            r[0] -= spread;
            r[1] -= spread;
            r[2] += spread;
            r[3] += spread;
            // What the clip and the mask (painted in the border box) leave, padded for antialiased edges.
            if (clip != null) {
                float[] c = clip.bounds();
                intersect(r, m, c[0] - 1, c[1] - 1, c[2] + 1, c[3] + 1);
            }
            if (masked) {
                intersect(r, m, b.x() - 1, b.y() - dy - 1, b.x() + b.width() + 1, b.y() - dy + b.height() + 1);
            }
            if (!(r[2] > r[0] && r[3] > r[1])) {
                out.truncate(start);
                return;
            }
            List<DrawCommand.LayerPaint> paints = List.of();
            RoundRect frame = null;
            for (int i = 0; i < filters.size(); i++) {
                if (filters.get(i).kind() == Filter.Kind.PAINT) {
                    paints = new ArrayList<>(2);
                    filters = layerPaints(e, cs, filters, paints);
                    frame = new RoundRect(b.x(), b.y() - dy, b.width(), b.height(), radii(cs, b.width(), b.height()));
                    break;
                }
            }
            if (masked) {
                out.add(DrawCommand.Mask.INSTANCE);
                if (m != null) {
                    out.add(new DrawCommand.PushTransform(m));
                }
                paintMask(e, cs, b, new RoundRect(b.x(), b.y() - dy, b.width(), b.height(), radii(cs, b.width(), b.height())), out);
                if (m != null) {
                    out.add(DrawCommand.PopTransform.INSTANCE);
                }
            }
            out.set(start, new DrawCommand.PushLayer(r[0], r[1], r[2] - r[0], r[3] - r[1], opacity, filters, blend, paints, frame, m,
                    clip, masked));
            out.add(DrawCommand.PopLayer.INSTANCE);
        }
    }

    /** Whether {@code images} has a layer other than {@code none}. */
    private static boolean hasImage(List<CssImage> images) {
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i) != CssImage.NONE) {
                return true;
            }
        }
        return false;
    }

    /** Cuts {@code r}, {@code {x0, y0, x1, y1}}, to the box of the local rectangle through {@code m} ({@code null} for none). */
    private static void intersect(float[] r, Affine m, float ax, float ay, float bx, float by) {
        if (m != null) {
            float px = m.x(ax, ay), py = m.y(ax, ay), qx = m.x(bx, ay), qy = m.y(bx, ay);
            float sx = m.x(ax, by), sy = m.y(ax, by), tx = m.x(bx, by), ty = m.y(bx, by);
            ax = Math.min(Math.min(px, qx), Math.min(sx, tx));
            ay = Math.min(Math.min(py, qy), Math.min(sy, ty));
            bx = Math.max(Math.max(px, qx), Math.max(sx, tx));
            by = Math.max(Math.max(py, qy), Math.max(sy, ty));
        }
        r[0] = Math.max(r[0], ax);
        r[1] = Math.max(r[1], ay);
        r[2] = Math.min(r[2], bx);
        r[3] = Math.min(r[3], by);
    }

    /** {@code filters} without the {@code -ix-paint()}s that name no usable effect; the others' effects go to {@code paints}. */
    private List<Filter> layerPaints(Element e, ComputedStyle cs, List<Filter> filters, List<DrawCommand.LayerPaint> paints) {
        List<Filter> kept = new ArrayList<>(filters.size());
        for (int i = 0; i < filters.size(); i++) {
            Filter f = filters.get(i);
            if (f.kind() != Filter.Kind.PAINT) {
                kept.add(f);
                continue;
            }
            Effect effect = effect(e, f.effect(), Effect.Kind.LAYER);
            if (effect != null) {
                kept.add(f);
                paints.add(new DrawCommand.LayerPaint(effect, effects.values(effect, cs)));
            }
        }
        return kept;
    }

    /** The linked effect of the file id {@code id}, of {@code kind}; otherwise reports why not and returns {@code null}. */
    private Effect effect(Element e, String id, Effect.Kind kind) {
        Effect effect = effects.byId(id);
        if (effect == null) {
            report(e, EffectCode.UNKNOWN_ALIAS, Effects.unknown(id));
            return null;
        }
        if (effect.kind() != kind) {
            report(e, EffectCode.WRONG_KIND, Effects.wrongKind(effect, null));
            return null;
        }
        return effect;
    }

    /** {@code grouped}: whether the element opened its own layer, which then holds its backdrop too. */
    private void paintBox(Element e, ComputedStyle cs, float dy, boolean grouped, DisplayList out) {
        LayoutBox b = e.box();
        float y = b.y() - dy;
        RoundRect border = new RoundRect(b.x(), y, b.width(), b.height(), radii(cs, b.width(), b.height()));
        List<Filter> backdrop = cs.get(CssProperties.BACKDROP_FILTER);
        if (!backdrop.isEmpty() && b.width() > 0 && b.height() > 0) {
            out.add(new DrawCommand.Backdrop(border, backdrop, grouped));
        }
        paintShadows(cs, b, border, false, out);
        if (cs.get(CssProperties.BACKGROUND_CLIP).get(0) == Keywords.BackgroundClip.TEXT) {
            paintTextClippedBackground(e, cs, b, border, dy, out);
        } else {
            paintBackground(e, cs, b, border, out);
        }
        paintShadows(cs, b, border, true, out);
        if (!paintBorderImage(e, cs, b, y, out)) {
            paintBorder(cs, b, border, y, out);
        }
        PaintFacet content = e.type().facet(PaintFacet.KEY);
        content.paint(e, y, out, this);
        if (!content.paintChildren()) return;
        Keywords.Overflow overflow = cs.get(CssProperties.OVERFLOW);
        if (overflow == Keywords.Overflow.VISIBLE) {
            paintChildren(e, dy, out);
            return;
        }
        // CSS clips overflow to the padding box.
        float clipX = b.x() + b.borderLeft();
        float clipY = y + b.borderTop();
        out.add(new DrawCommand.PushClip(clipX, clipY, b.width() - b.borderLeft() - b.borderRight(),
                b.height() - b.borderTop() - b.borderBottom()));
        paintChildren(e, dy + e.scrollTop(), out);
        paintScrollbar(e, cs, dy, out);
        out.add(DrawCommand.PopClip.INSTANCE);
    }

    /**
     * {@code background-clip: text}: the background on its own layer, masked by the element's text
     * and its descendants' drawn opaque, without their shadows.
     */
    private void paintTextClippedBackground(Element e, ComputedStyle cs, LayoutBox b, RoundRect border, float dy, DisplayList out) {
        int start = out.size();
        out.add(DrawCommand.PopLayer.INSTANCE); // replaced by the layer once its bounds are known
        paintBackground(e, cs, b, border, out);
        float[] r = bounds.of(out, start + 1, out.size());
        if (r == null) {
            out.truncate(start);
            return;
        }
        out.set(start, new DrawCommand.PushLayer(r[0], r[1], r[2] - r[0], r[3] - r[1], 1, List.of(), Keywords.MixBlendMode.NORMAL,
                List.of(), null, null, null, true));
        out.add(DrawCommand.Mask.INSTANCE);
        if (e.type().facet(PaintFacet.KEY).paintChildren()) {
            paintTextMask(e, dy, out);
        }
        out.add(DrawCommand.PopLayer.INSTANCE);
    }

    /** The text of {@code e}'s children and their descendants, opaque white, through their transforms and overflow clips. */
    private void paintTextMask(Element e, float dy, DisplayList out) {
        LayoutBox b = e.box();
        boolean clipped = e.computed().get(CssProperties.OVERFLOW) != Keywords.Overflow.VISIBLE;
        if (clipped) {
            out.add(new DrawCommand.PushClip(b.x() + b.borderLeft(), b.y() - dy + b.borderTop(),
                    b.width() - b.borderLeft() - b.borderRight(), b.height() - b.borderTop() - b.borderBottom()));
        }
        float childY = clipped ? dy + e.scrollTop() : dy;
        List<Node> children = e.children();
        for (int i = 0; i < children.size(); i++) {
            Node n = children.get(i);
            if (n instanceof Element c) {
                ComputedStyle cs = c.computed();
                if (c.rendersInTopLayer() || cs.get(CssProperties.DISPLAY) == Keywords.Display.NONE
                        || !c.type().facet(PaintFacet.KEY).paintChildren()) {
                    continue;
                }
                LayoutBox cb = c.box();
                Affine m = Transform.of(cs, cb.x(), cb.y() - childY, cb.width(), cb.height());
                if (m != null) {
                    out.add(new DrawCommand.PushTransform(m));
                }
                paintTextMask(c, childY, out);
                if (m != null) {
                    out.add(DrawCommand.PopTransform.INSTANCE);
                }
            } else {
                paintText((TextNode) n, e.computed(), childY, out, true);
            }
        }
        if (clipped) {
            out.add(DrawCommand.PopClip.INSTANCE);
        }
    }

    /** One line of a control's text, over the shadow of {@code cs}'s {@code text-shadow}. */
    public void text(DisplayList out, ComputedStyle cs, float x, float y, float size, int color, String text) {
        TextShadow shadow = cs.get(CssProperties.TEXT_SHADOW);
        int s = shadow.color();
        if (s >>> 24 != 0) {
            out.add(new DrawCommand.Text(x + shadow.x(), y + shadow.y(), size, s, text, 0, null, 0));
        }
        if (color >>> 24 != 0) {
            out.add(new DrawCommand.Text(x, y, size, color, text, 0, null, 0));
        }
    }

    /**
     * The outer or the inset shadows of {@code box-shadow}, bottom (last listed) first. An outer
     * shadow is the border box grown by its spread and moved by its offset, drawn outside the border
     * box; an inset one is the padding box shrunk by its spread and moved, darkening the padding box
     * outside it. The blur radius is twice the Gaussian's standard deviation, as in CSS.
     */
    private void paintShadows(ComputedStyle cs, LayoutBox b, RoundRect border, boolean inset, DisplayList out) {
        List<BoxShadow> shadows = cs.get(CssProperties.BOX_SHADOW);
        if (shadows.isEmpty()) {
            return;
        }
        RoundRect base = inset ? border.inset(b.borderTop(), b.borderRight(), b.borderBottom(), b.borderLeft()) : border;
        for (int i = shadows.size() - 1; i >= 0; i--) {
            BoxShadow s = shadows.get(i);
            int color = s.color();
            if (s.inset() != inset || color >>> 24 == 0) {
                continue;
            }
            RoundRect shape = spread(base, inset ? -s.spread() : s.spread());
            if (!inset && (shape.width() <= 0 || shape.height() <= 0)) {
                continue;
            }
            shape = new RoundRect(shape.x() + s.x(), shape.y() + s.y(), shape.width(), shape.height(), shape.radii());
            out.add(new DrawCommand.Shadow(shape, s.blur() / 2, color, inset, base, crisp(cs)));
        }
    }

    /**
     * {@code r} grown by {@code s} on every side, or shrunk when it is negative. Growing keeps a sharp
     * corner sharp and grows a small radius by less than {@code s} (CSS Backgrounds 3 §7.1.1).
     */
    private static RoundRect spread(RoundRect r, float s) {
        if (s <= 0) {
            return s == 0 ? r : r.inset(-s, -s, -s, -s);
        }
        RoundRect.Radii q = r.radii();
        float w = r.width() + 2 * s;
        float h = r.height() + 2 * s;
        RoundRect.Radii grown = q.isZero() ? q : new RoundRect.Radii(grow(q.topLeftX(), s), grow(q.topLeftY(), s),
                grow(q.topRightX(), s), grow(q.topRightY(), s), grow(q.bottomRightX(), s), grow(q.bottomRightY(), s),
                grow(q.bottomLeftX(), s), grow(q.bottomLeftY(), s), q.corners()).fit(w, h);
        return new RoundRect(r.x() - s, r.y() - s, w, h, grown);
    }

    private static float grow(float radius, float s) {
        if (radius <= 0) {
            return 0;
        }
        float ratio = radius / s;
        return radius + s * (ratio >= 1 ? 1 : 1 + (ratio - 1) * (ratio - 1) * (ratio - 1));
    }

    /**
     * {@code outline}: a ring {@code outline-width} wide around the border box grown by
     * {@code outline-offset}, its corners following the box's as a shadow's spread grows them; four
     * rectangles when they are square.
     */
    private void paintOutline(ComputedStyle cs, LayoutBox b, float y, DisplayList out) {
        if (cs.get(CssProperties.OUTLINE_STYLE) == Keywords.BorderStyle.NONE) {
            return;
        }
        float w = cs.get(CssProperties.OUTLINE_WIDTH).value();
        int color = cs.get(CssProperties.OUTLINE_COLOR);
        if (w <= 0 || color >>> 24 == 0) {
            return;
        }
        RoundRect border = new RoundRect(b.x(), y, b.width(), b.height(), radii(cs, b.width(), b.height()));
        RoundRect outer = spread(border, cs.get(CssProperties.OUTLINE_OFFSET).value() + w);
        float ow = outer.width();
        float oh = outer.height();
        if (ow <= 0 || oh <= 0) {
            return;
        }
        if (!outer.radii().isZero()) {
            out.add(new DrawCommand.Border(outer, w, w, w, w, color, color, color, color, crisp(cs)));
            return;
        }
        float side = Math.min(w, oh / 2);
        rect(out, outer.x(), outer.y(), ow, side, color);
        rect(out, outer.x(), outer.y() + oh - side, ow, side, color);
        float middle = oh - 2 * side;
        if (middle > 0) {
            float across = Math.min(w, ow / 2);
            rect(out, outer.x(), outer.y() + side, across, middle, color);
            rect(out, outer.x() + ow - across, outer.y() + side, across, middle, color);
        }
    }

    /**
     * Each side a rectangle inside the border box; top and bottom span the corners. With a rounded
     * corner, one {@link DrawCommand.Border} instead, the inner edge curving with the padding box.
     */
    private void paintBorder(ComputedStyle cs, LayoutBox b, RoundRect border, float y, DisplayList out) {
        float t = b.borderTop();
        float r = b.borderRight();
        float bt = b.borderBottom();
        float l = b.borderLeft();
        if (!border.radii().isZero()) {
            int tc = cs.get(CssProperties.BORDER_TOP_COLOR);
            int rc = cs.get(CssProperties.BORDER_RIGHT_COLOR);
            int bc = cs.get(CssProperties.BORDER_BOTTOM_COLOR);
            int lc = cs.get(CssProperties.BORDER_LEFT_COLOR);
            boolean visible = t > 0 && tc >>> 24 != 0 || r > 0 && rc >>> 24 != 0 || bt > 0 && bc >>> 24 != 0
                    || l > 0 && lc >>> 24 != 0;
            if (visible) {
                out.add(new DrawCommand.Border(border, t, r, bt, l, tc, rc, bc, lc, crisp(cs)));
            }
            return;
        }
        if (t > 0) {
            rect(out, b.x(), y, b.width(), t, cs.get(CssProperties.BORDER_TOP_COLOR));
        }
        if (bt > 0) {
            rect(out, b.x(), y + b.height() - bt, b.width(), bt, cs.get(CssProperties.BORDER_BOTTOM_COLOR));
        }
        float middle = b.height() - t - bt;
        if (middle <= 0) {
            return;
        }
        if (l > 0) {
            rect(out, b.x(), y + t, l, middle, cs.get(CssProperties.BORDER_LEFT_COLOR));
        }
        if (r > 0) {
            rect(out, b.x() + b.width() - r, y + t, r, middle, cs.get(CssProperties.BORDER_RIGHT_COLOR));
        }
    }

    /**
     * CSS Backgrounds 3 §6 over the border box, without {@code border-image-outset}: the image is cut
     * along the slice lines into corners, edges and a middle; corners are scaled into the corners of
     * the border image widths, edges scaled to the width of their side and tiled along it per
     * {@code border-image-repeat} ({@code repeat} centers the tiles, {@code round} fits a whole number),
     * the middle drawn only with {@code fill}. Whether it painted: an image the host cannot measure is
     * reported and the border is drawn as usual.
     */
    private boolean paintBorderImage(Element e, ComputedStyle cs, LayoutBox b, float y, DisplayList out) {
        if (!(cs.get(CssProperties.BORDER_IMAGE_SOURCE) instanceof CssImage.Url url)) {
            return false;
        }
        String src = url.src();
        ImageMeasurer.Size natural = images.naturalSize(src);
        if (natural == null) {
            report(e, PaintCode.BORDER_IMAGE_UNSIZED, "cannot load border image '" + src + "' to slice it");
            return false;
        }
        float iw = natural.width();
        float ih = natural.height();
        if (iw <= 0 || ih <= 0) {
            return true;
        }
        BorderImageSlice slice = cs.get(CssProperties.BORDER_IMAGE_SLICE);
        float st = slice(slice.top(), ih);
        float sr = slice(slice.right(), iw);
        float sb = slice(slice.bottom(), ih);
        float sl = slice(slice.left(), iw);
        BorderImageWidth width = cs.get(CssProperties.BORDER_IMAGE_WIDTH);
        float w = b.width();
        float h = b.height();
        float wt = imageWidth(width.top(), b.borderTop(), h, st);
        float wr = imageWidth(width.right(), b.borderRight(), w, sr);
        float wb = imageWidth(width.bottom(), b.borderBottom(), h, sb);
        float wl = imageWidth(width.left(), b.borderLeft(), w, sl);
        float f = Math.min(1, Math.min(wl + wr > 0 ? w / (wl + wr) : 1, wt + wb > 0 ? h / (wt + wb) : 1));
        wt *= f;
        wr *= f;
        wb *= f;
        wl *= f;
        List<Keywords.ImageTiling> repeat = cs.get(CssProperties.BORDER_IMAGE_REPEAT);
        Keywords.ImageTiling across = repeat.get(0);
        Keywords.ImageTiling down = repeat.get(1);
        Keywords.ImageTiling stretch = Keywords.ImageTiling.STRETCH;
        Slicer s = new Slicer(e, src, iw, ih, OPAQUE_WHITE, filter(cs), out);
        float x0 = b.x();
        float x1 = x0 + w;
        float y1 = y + h;
        float midW = w - wl - wr;
        float midH = h - wt - wb;
        float srcW = iw - sl - sr;
        float srcH = ih - st - sb;
        s.draw(x0, y, wl, wt, 0, 0, sl, st, wl, wt, stretch, stretch);
        s.draw(x1 - wr, y, wr, wt, iw - sr, 0, sr, st, wr, wt, stretch, stretch);
        s.draw(x1 - wr, y1 - wb, wr, wb, iw - sr, ih - sb, sr, sb, wr, wb, stretch, stretch);
        s.draw(x0, y1 - wb, wl, wb, 0, ih - sb, sl, sb, wl, wb, stretch, stretch);
        s.draw(x0 + wl, y, midW, wt, sl, 0, srcW, st, srcW * scale(wt, st), wt, across, stretch);
        s.draw(x0 + wl, y1 - wb, midW, wb, sl, ih - sb, srcW, sb, srcW * scale(wb, sb), wb, across, stretch);
        s.draw(x0, y + wt, wl, midH, 0, st, sl, srcH, wl, srcH * scale(wl, sl), stretch, down);
        s.draw(x1 - wr, y + wt, wr, midH, iw - sr, st, sr, srcH, wr, srcH * scale(wr, sr), stretch, down);
        if (slice.fill()) {
            // The middle scales like the top (else the bottom) edge across, like the left (else the right) one down.
            float across1 = st > 0 && wt > 0 ? wt / st : sb > 0 && wb > 0 ? wb / sb : 1;
            float down1 = sl > 0 && wl > 0 ? wl / sl : sr > 0 && wr > 0 ? wr / sr : 1;
            s.draw(x0 + wl, y + wt, midW, midH, sl, st, srcW, srcH, srcW * across1, srcH * down1, across, down);
        }
        return true;
    }

    /** A slice offset in image pixels, at most the image's side. */
    private static float slice(Length l, float side) {
        return Math.min(l.resolve(side), side);
    }

    /** A {@code border-image-width} side in GUI pixels; {@code area} is the border box's side along it. */
    private static float imageWidth(BorderImageWidth.Side s, float border, float area, float slice) {
        if (s.length() == null) {
            return s.number() * border;
        }
        return s.length().isAuto() ? slice : s.length().resolve(area);
    }

    /** How much an edge drawn {@code width} wide from a slice {@code slice} image pixels wide is scaled; 0 when empty. */
    private static float scale(float width, float slice) {
        return slice > 0 ? width / slice : 0;
    }

    /** Draws the parts of one border image. */
    private final class Slicer {
        private final Element e;
        private final String src;
        private final float iw;
        private final float ih;
        private final int tint;
        private final DrawCommand.Image.Filter filter;
        private final DisplayList out;

        Slicer(Element e, String src, float iw, float ih, int tint, DrawCommand.Image.Filter filter, DisplayList out) {
            this.e = e;
            this.src = src;
            this.iw = iw;
            this.ih = ih;
            this.tint = tint;
            this.filter = filter;
            this.out = out;
        }

        /**
         * The image region {@code (sx, sy, sw, sh)} in image pixels, as tiles {@code tw} by {@code th}
         * over the area {@code (x, y, w, h)}, clipped to it; a {@code stretch} axis has one tile.
         */
        void draw(float x, float y, float w, float h, float sx, float sy, float sw, float sh, float tw, float th,
                  Keywords.ImageTiling across, Keywords.ImageTiling down) {
            if (tint >>> 24 == 0 || w <= 0 || h <= 0 || sw <= 0 || sh <= 0) {
                return;
            }
            float[] ax = axis(x, w, tw, across);
            float[] ay = axis(y, h, th, down);
            if (ax == null || ay == null) {
                return;
            }
            if ((long) ax[2] * (long) ay[2] > MAX_TILES) {
                report(e, PaintCode.BORDER_IMAGE_TOO_MANY_TILES, "border image tiles would be drawn " + (long) ax[2] * (long) ay[2]
                        + " times (at most " + MAX_TILES + "); use a larger slice or border-image-width");
                return;
            }
            for (int row = 0; row < ay[2]; row++) {
                float ty = ay[0] + row * ay[1];
                float cy0 = Math.max(ty, y);
                float cy1 = Math.min(ty + ay[1], y + h);
                if (cy1 <= cy0) {
                    continue;
                }
                for (int col = 0; col < ax[2]; col++) {
                    float tx = ax[0] + col * ax[1];
                    float cx0 = Math.max(tx, x);
                    float cx1 = Math.min(tx + ax[1], x + w);
                    if (cx1 <= cx0) {
                        continue;
                    }
                    out.add(new DrawCommand.Image(cx0, cy0, cx1 - cx0, cy1 - cy0, src,
                            (sx + sw * (cx0 - tx) / ax[1]) / iw, (sy + sh * (cy0 - ty) / ay[1]) / ih,
                            (sx + sw * (cx1 - tx) / ax[1]) / iw, (sy + sh * (cy1 - ty) / ay[1]) / ih, tint, filter));
                }
            }
        }

        /** {@code {first tile's start, tile length, tile count}} along one axis; {@code null} when nothing shows. */
        private static float[] axis(float start, float length, float tile, Keywords.ImageTiling mode) {
            if (mode == Keywords.ImageTiling.STRETCH) {
                return new float[]{start, length, 1};
            }
            if (!(tile > 0)) {
                return null;
            }
            if (mode == Keywords.ImageTiling.ROUND) {
                float n = Math.max(1, Math.round(length / tile));
                return new float[]{start, length / n, n};
            }
            float free = (length - tile) / 2;
            float first = start + free - tile * (float) Math.ceil(free / tile);
            return new float[]{first, tile, (float) Math.ceil((start + length - first) / tile)};
        }
    }

    public void rect(DisplayList out, float x, float y, float w, float h, int color) {
        if (color >>> 24 != 0) {
            out.add(new DrawCommand.Rect(x, y, w, h, color));
        }
    }

    /** {@code dy}: the scroll offset of the ancestors, as {@link #paintBox} takes it. */
    private void paintScrollbar(Element e, ComputedStyle cs, float dy, DisplayList out) {
        Scrollbar bar = Scrollbar.of(e);
        if (bar == null) {
            return;
        }
        ScrollbarColor colors = cs.get(CssProperties.SCROLLBAR_COLOR);
        if (bar.track()) {
            rect(out, bar.x(), bar.y() - dy, bar.width(), bar.height(), colors.track());
        }
        rect(out, bar.x(), bar.thumbY() - dy, bar.width(), bar.thumbHeight(), colors.thumb());
    }

    /** The corner radii and shapes of the border box of {@code cs}'s element, the radii reduced to fit it. */
    private static RoundRect.Radii radii(ComputedStyle cs, float w, float h) {
        CornerRadius tl = cs.get(CssProperties.BORDER_TOP_LEFT_RADIUS);
        CornerRadius tr = cs.get(CssProperties.BORDER_TOP_RIGHT_RADIUS);
        CornerRadius br = cs.get(CssProperties.BORDER_BOTTOM_RIGHT_RADIUS);
        CornerRadius bl = cs.get(CssProperties.BORDER_BOTTOM_LEFT_RADIUS);
        if (tl.equals(CornerRadius.ZERO) && tr.equals(CornerRadius.ZERO) && br.equals(CornerRadius.ZERO)
                && bl.equals(CornerRadius.ZERO)) {
            return RoundRect.Radii.ZERO;
        }
        RoundRect.Corners corners = new RoundRect.Corners(cs.get(CssProperties.CORNER_TOP_LEFT_SHAPE),
                cs.get(CssProperties.CORNER_TOP_RIGHT_SHAPE), cs.get(CssProperties.CORNER_BOTTOM_RIGHT_SHAPE),
                cs.get(CssProperties.CORNER_BOTTOM_LEFT_SHAPE));
        return new RoundRect.Radii(radius(tl.x(), w), radius(tl.y(), h), radius(tr.x(), w), radius(tr.y(), h),
                radius(br.x(), w), radius(br.y(), h), radius(bl.x(), w), radius(bl.y(), h),
                corners).fit(w, h);
    }

    /** Whether {@code cs}'s element bakes its shape masks crisp ({@code shape-rendering}). */
    private static boolean crisp(ComputedStyle cs) {
        return cs.get(CssProperties.SHAPE_RENDERING) == Keywords.ShapeRendering.CRISP_EDGES;
    }

    private static float radius(Length l, float side) {
        return l.resolve(side);
    }

    /** How {@code cs}'s element samples scaled images ({@code image-rendering}). */
    public static DrawCommand.Image.Filter filter(ComputedStyle cs) {
        return switch (cs.get(CssProperties.IMAGE_RENDERING)) {
            case AUTO -> DrawCommand.Image.Filter.AUTO;
            case SMOOTH -> DrawCommand.Image.Filter.LINEAR;
            case PIXELATED, CRISP_EDGES -> DrawCommand.Image.Filter.NEAREST;
        };
    }

    /** {@code list}'s item for layer {@code i}, repeating the list when it is shorter, as CSS does. */
    private static <T> T layer(List<T> list, int i) {
        return list.get(i % list.size());
    }

    /**
     * The background color, then the layers bottom to top, each inside its {@code background-clip}
     * box (the color in the bottom layer's). With the bottom layer an image that multiplies, the color
     * tints it instead of painting under it.
     */
    private void paintBackground(Element e, ComputedStyle cs, LayoutBox b, RoundRect border, DisplayList out) {
        List<CssImage> images = cs.get(CssProperties.BACKGROUND_IMAGE);
        List<Keywords.BackgroundBlendMode> blends = cs.get(CssProperties.BACKGROUND_BLEND_MODE);
        List<Keywords.BackgroundClip> clips = cs.get(CssProperties.BACKGROUND_CLIP);
        int bottom = images.size() - 1;
        for (int i = 0; i < bottom; i++) {
            if (layer(blends, i) == Keywords.BackgroundBlendMode.MULTIPLY && images.get(i) != CssImage.NONE) {
                report(e, PaintCode.BLEND_NOT_BOTTOM, "background layer " + (i + 1) + " multiplies, but only the bottom layer may");
            }
        }
        int bg = cs.get(CssProperties.BACKGROUND_COLOR);
        boolean multiply = layer(blends, bottom) == Keywords.BackgroundBlendMode.MULTIPLY
                && images.get(bottom) instanceof CssImage.Url;
        if (!multiply) {
            fill(out, clipBox(layer(clips, bottom), b, border), bg, crisp(cs));
        }
        float originW = b.width() - b.borderLeft() - b.borderRight();
        float originH = b.height() - b.borderTop() - b.borderBottom();
        for (int i = bottom; i >= 0; i--) {
            CssImage image = images.get(i);
            RoundRect area = clipBox(layer(clips, i), b, border);
            if (image instanceof CssImage.Paint p) {
                Effect effect = effect(e, p.effect(), Effect.Kind.BOX);
                if (effect != null && area.width() > 0 && area.height() > 0) {
                    out.add(new DrawCommand.Paint(area, effect, effects.values(effect, cs)));
                }
                continue;
            }
            paintImage(e, cs, image, i, false, layer(cs.get(CssProperties.BACKGROUND_SIZE), i),
                    layer(cs.get(CssProperties.BACKGROUND_REPEAT), i), layer(cs.get(CssProperties.BACKGROUND_POSITION_X), i),
                    layer(cs.get(CssProperties.BACKGROUND_POSITION_Y), i), border.x() + b.borderLeft(), border.y() + b.borderTop(),
                    originW, originH, area, multiply && i == bottom ? 0xFF000000 | bg : OPAQUE_WHITE, out);
        }
    }

    /** {@code shape} in a solid color: a plain rectangle when it has no rounded corner. */
    private void fill(DisplayList out, RoundRect shape, int color, boolean crisp) {
        if (shape.radii().isZero()) {
            rect(out, shape.x(), shape.y(), shape.width(), shape.height(), color);
            return;
        }
        if (color >>> 24 != 0) {
            out.add(new DrawCommand.Fill(shape, shape.x(), shape.y(), shape.width(), shape.height(), color, null, crisp));
        }
    }

    /** The border box, padding box or content box as a shape; {@code text} paints in the border box, under a mask. */
    private static RoundRect clipBox(Keywords.BackgroundClip clip, LayoutBox b, RoundRect border) {
        return switch (clip) {
            case BORDER_BOX, TEXT -> border;
            case PADDING_BOX -> border.inset(b.borderTop(), b.borderRight(), b.borderBottom(), b.borderLeft());
            case CONTENT_BOX -> border.inset(b.borderTop() + b.paddingTop(), b.borderRight() + b.paddingRight(),
                    b.borderBottom() + b.paddingBottom(), b.borderLeft() + b.paddingLeft());
        };
    }

    /**
     * The {@code mask-image} layers bottom to top, painted over each other in the border box: as
     * background layers with {@code mask-size: auto}, {@code mask-position: 0 0} in the border box,
     * {@code mask-repeat: repeat} and {@code mask-clip: border-box}.
     */
    private void paintMask(Element e, ComputedStyle cs, LayoutBox b, RoundRect border, DisplayList out) {
        List<CssImage> images = cs.get(CssProperties.MASK_IMAGE);
        for (int i = images.size() - 1; i >= 0; i--) {
            paintImage(e, cs, images.get(i), i, true, BackgroundSize.AUTO, Keywords.BackgroundRepeat.REPEAT, Length.ZERO, Length.ZERO,
                    border.x(), border.y(), border.width(), border.height(), border, OPAQUE_WHITE, out);
        }
    }

    /**
     * CSS Backgrounds 3 for one image layer ({@code mask}: of {@code mask-image}): sized and
     * positioned in the origin box {@code (originX, originY, originW, originH)}, tiled out to the
     * edges of {@code area}, the clip box, and clipped to it. A gradient has no natural size, so an
     * {@code auto} side is the origin box's. {@code tint} multiplies an image's texels.
     */
    private void paintImage(Element e, ComputedStyle cs, CssImage image, int i, boolean mask, BackgroundSize size,
                            Keywords.BackgroundRepeat repeat, Length positionX, Length positionY, float originX, float originY,
                            float originW, float originH, RoundRect area, int tint, DisplayList out) {
        if (image == CssImage.NONE || originW <= 0 || originH <= 0) {
            return;
        }
        String src = image instanceof CssImage.Url u ? u.src() : null;
        float w = side(size.width(), originW);
        float h = side(size.height(), originH);
        if (src == null) {
            w = Float.isNaN(w) ? originW : w;
            h = Float.isNaN(h) ? originH : h;
        } else if (Float.isNaN(w) || Float.isNaN(h)) {
            ImageMeasurer.Size natural = images.naturalSize(src);
            if (natural == null) {
                if (mask) {
                    report(e, PaintCode.MASK_IMAGE_UNSIZED, "cannot load mask image '" + src + "' to size it");
                } else {
                    report(e, PaintCode.BACKGROUND_IMAGE_UNSIZED, "cannot load background image '" + src + "' to size it (background-size: auto)");
                }
                return;
            }
            if (natural.width() <= 0 || natural.height() <= 0) {
                return;
            }
            if (Float.isNaN(w) && Float.isNaN(h)) {
                w = natural.width();
                h = natural.height();
            } else if (Float.isNaN(w)) {
                w = h * natural.width() / natural.height();
            } else {
                h = w * natural.height() / natural.width();
            }
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean repeatX = repeat == Keywords.BackgroundRepeat.REPEAT || repeat == Keywords.BackgroundRepeat.REPEAT_X;
        boolean repeatY = repeat == Keywords.BackgroundRepeat.REPEAT || repeat == Keywords.BackgroundRepeat.REPEAT_Y;
        float left = area.x();
        float top = area.y();
        float right = left + area.width();
        float bottom = top + area.height();
        float x0 = originX + offset(positionX, originW - w);
        float y0 = originY + offset(positionY, originH - h);
        int columns = 1;
        int rows = 1;
        if (repeatX) {
            x0 -= w * (float) Math.ceil((x0 - left) / w);
            columns = (int) Math.ceil((right - x0) / w);
        }
        if (repeatY) {
            y0 -= h * (float) Math.ceil((y0 - top) / h);
            rows = (int) Math.ceil((bottom - y0) / h);
        }
        if ((long) columns * rows > MAX_TILES) {
            if (mask) {
                report(e, PaintCode.MASK_TOO_MANY_TILES, "mask layer " + (i + 1) + " would repeat " + (long) columns * rows
                        + " times (at most " + MAX_TILES + ")");
            } else {
                report(e, PaintCode.BACKGROUND_TOO_MANY_TILES, "background layer " + (i + 1) + " would repeat " + (long) columns * rows
                        + " times (at most " + MAX_TILES + "); use a larger background-size");
            }
            return;
        }
        DrawCommand.Image.Filter filter = filter(cs);
        boolean crisp = crisp(cs);
        for (int row = 0; row < rows; row++) {
            float ty = y0 + row * h;
            float cy0 = Math.max(ty, top);
            float cy1 = Math.min(ty + h, bottom);
            if (cy1 <= cy0) {
                continue;
            }
            for (int col = 0; col < columns; col++) {
                float tx = x0 + col * w;
                float cx0 = Math.max(tx, left);
                float cx1 = Math.min(tx + w, right);
                if (cx1 <= cx0) {
                    continue;
                }
                if (src != null) {
                    out.add(new DrawCommand.Image(cx0, cy0, cx1 - cx0, cy1 - cy0, src,
                            (cx0 - tx) / w, (cy0 - ty) / h, (cx1 - tx) / w, (cy1 - ty) / h, tint, filter));
                } else {
                    Gradient g = gradient(e, (CssImage.Gradient) image, tx, ty, w, h);
                    if (g != null) {
                        out.add(new DrawCommand.Fill(area, cx0, cy0, cx1 - cx0, cy1 - cy0, 0, g, crisp));
                    }
                }
            }
        }
    }

    /** Steps inserted between two stops whose premultiplied blend differs from a straight one. */
    private static final int PREMULTIPLIED_STEPS = 8;
    /** Bounds the stops a repeating gradient spreads over its tile, so a tiny period cannot stall a frame. */
    private static final int MAX_RAMP = 1024;

    /** {@code g} drawn in the tile {@code (x, y, w, h)}; {@code null} when it draws nothing. */
    private Gradient gradient(Element e, CssImage.Gradient g, float x, float y, float w, float h) {
        if (g instanceof CssImage.LinearGradient l) {
            return linear(e, l, x, y, w, h);
        }
        if (g instanceof CssImage.RadialGradient r) {
            return radial(e, r, x, y, w, h);
        }
        CssImage.ConicGradient c = (CssImage.ConicGradient) g;
        Ramp ramp = ramp(e, c.stops(), 1, false, 0, 1);
        return ramp == null ? null : new Gradient(Gradient.Kind.CONIC, x + c.centerX().resolve(w), y + c.centerY().resolve(h),
                (float) Math.toRadians(c.from()), 0, ramp.offsets, ramp.colors);
    }

    /**
     * CSS Images 3 §3.1: the gradient line runs through the tile's center at the angle, long enough
     * that the end stops' lines pass through the corners.
     */
    private Gradient linear(Element e, CssImage.LinearGradient g, float x, float y, float w, float h) {
        double angle = Float.isNaN(g.angle()) ? Math.atan2(g.cornerX() * h, -g.cornerY() * w) : Math.toRadians(g.angle());
        float dx = (float) Math.sin(angle);
        float dy = (float) -Math.cos(angle);
        float len = Math.abs(w * dx) + Math.abs(h * dy);
        if (len <= 0) {
            return null;
        }
        Ramp ramp = ramp(e, g.stops(), len, g.repeating(), 0, 1);
        if (ramp == null) {
            return null;
        }
        float cx = x + w / 2;
        float cy = y + h / 2;
        return Gradient.linear(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2, ramp.offsets, ramp.colors);
    }

    /**
     * CSS Images 3 §3.2: the ending shape around the center, sized to its keyword against the tile's
     * sides and corners; an ellipse sized to a corner keeps the aspect ratio its side keyword gives.
     * Stop lengths and percentages are along the horizontal ray, out to the ending shape.
     */
    private Gradient radial(Element e, CssImage.RadialGradient g, float x, float y, float w, float h) {
        float cx = x + g.centerX().resolve(w);
        float cy = y + g.centerY().resolve(h);
        float left = Math.abs(cx - x);
        float right = Math.abs(x + w - cx);
        float top = Math.abs(cy - y);
        float bottom = Math.abs(y + h - cy);
        float rx;
        float ry;
        switch (g.extent()) {
            case EXPLICIT -> {
                rx = g.radiusX().resolve(w);
                ry = g.circle() ? rx : g.radiusY().resolve(h);
            }
            case CLOSEST_SIDE, FARTHEST_SIDE -> {
                boolean closest = g.extent() == CssImage.Extent.CLOSEST_SIDE;
                rx = closest ? Math.min(left, right) : Math.max(left, right);
                ry = closest ? Math.min(top, bottom) : Math.max(top, bottom);
                if (g.circle()) {
                    rx = ry = closest ? Math.min(rx, ry) : Math.max(rx, ry);
                }
            }
            default -> {
                boolean closest = g.extent() == CssImage.Extent.CLOSEST_CORNER;
                float sx = closest ? Math.min(left, right) : Math.max(left, right);
                float sy = closest ? Math.min(top, bottom) : Math.max(top, bottom);
                if (g.circle()) {
                    rx = ry = (float) Math.hypot(sx, sy);
                } else {
                    rx = sx * (float) Math.sqrt(2);
                    ry = sy * (float) Math.sqrt(2);
                }
            }
        }
        if (!(rx > 0) || !(ry > 0)) {
            // A degenerate ending shape: the last color everywhere, or the average of a repeating one (CSS Images 3 §3.2.3).
            int color = g.repeating() ? average(g.stops()) : g.stops().get(g.stops().size() - 1).color();
            return Gradient.linear(cx, cy, cx, cy, new float[]{0}, new int[]{color});
        }
        float far = 0;
        for (int corner = 0; corner < 4; corner++) {
            float u = ((corner & 1) == 0 ? x : x + w) - cx;
            float v = ((corner & 2) == 0 ? y : y + h) - cy;
            far = Math.max(far, (float) Math.hypot(u / rx, v / ry));
        }
        Ramp ramp = ramp(e, g.stops(), rx, g.repeating(), 0, far);
        return ramp == null ? null : new Gradient(Gradient.Kind.RADIAL, cx, cy, rx, ry, ramp.offsets, ramp.colors);
    }

    /** A gradient's stops as ramp positions and colors. */
    private record Ramp(float[] offsets, int[] colors) {
    }

    /**
     * The ramp of {@code stops} along a line {@code len} pixels long: omitted positions are spread
     * evenly, out-of-order ones raised to the largest before them (CSS Images 3 §3.5). A
     * {@code repeating} ramp repeats its first to last stop over {@code [from, to]}; when they
     * coincide it is one color, the stops' average. {@code null} after reporting too many stops.
     */
    private Ramp ramp(Element e, List<CssImage.Stop> stops, float len, boolean repeating, float from, float to) {
        int n = stops.size();
        float[] at = new float[n];
        for (int i = 0; i < n; i++) {
            Length p = stops.get(i).position();
            at[i] = p == null ? Float.NaN : p.resolve(len) / len;
        }
        if (Float.isNaN(at[0])) {
            at[0] = 0;
        }
        if (Float.isNaN(at[n - 1])) {
            at[n - 1] = Math.max(1, maxDefined(at, n - 1));
        }
        float max = at[0];
        for (int i = 1; i < n; i++) {
            if (!Float.isNaN(at[i])) {
                at[i] = Math.max(at[i], max);
                max = at[i];
            }
        }
        for (int i = 1; i < n; i++) {
            if (Float.isNaN(at[i])) {
                int j = i;
                while (Float.isNaN(at[j])) {
                    j++;
                }
                float step = (at[j] - at[i - 1]) / (j - i + 1);
                for (int k = i; k < j; k++) {
                    at[k] = at[i - 1] + step * (k - i + 1);
                }
            }
        }
        float[] offsets = new float[(n - 1) * PREMULTIPLIED_STEPS + 1];
        int[] colors = new int[offsets.length];
        int k = 0;
        offsets[k] = at[0];
        colors[k++] = stops.get(0).color();
        for (int i = 1; i < n; i++) {
            int a = stops.get(i - 1).color();
            int b = stops.get(i).color();
            boolean straight = (a >>> 24) == (b >>> 24) || (a & 0xFFFFFF) == (b & 0xFFFFFF);
            int steps = straight ? 1 : PREMULTIPLIED_STEPS;
            for (int s = 1; s <= steps; s++) {
                float p = s / (float) steps;
                offsets[k] = at[i - 1] + (at[i] - at[i - 1]) * p;
                colors[k++] = s == steps ? b : premultipliedMix(a, b, p);
            }
        }
        if (!repeating) {
            return new Ramp(Arrays.copyOf(offsets, k), Arrays.copyOf(colors, k));
        }
        float period = at[n - 1] - at[0];
        if (!(period > 1e-6f)) {
            return new Ramp(new float[]{0}, new int[]{average(stops)});
        }
        long first = (long) Math.floor((from - at[0]) / period);
        long last = (long) Math.ceil((to - at[0]) / period);
        long count = Math.max(1, last - first) * k;
        if (count > MAX_RAMP) {
            report(e, PaintCode.GRADIENT_TOO_MANY_STOPS, "a repeating gradient would repeat " + count + " stops across its box (at most "
                    + MAX_RAMP + "); make the repeated span longer");
            return null;
        }
        float[] ro = new float[(int) count];
        int[] rc = new int[ro.length];
        int r = 0;
        for (long q = first; q < Math.max(first + 1, last); q++) {
            for (int i = 0; i < k; i++) {
                ro[r] = offsets[i] + q * period;
                rc[r++] = colors[i];
            }
        }
        return new Ramp(ro, rc);
    }

    /** The stops' colors averaged in premultiplied ARGB, each stop weighted alike. */
    private static int average(List<CssImage.Stop> stops) {
        float a = 0;
        float[] c = new float[3];
        for (CssImage.Stop s : stops) {
            float alpha = (s.color() >>> 24) / 255f;
            a += alpha;
            for (int i = 0; i < 3; i++) {
                c[i] += (s.color() >> (16 - 8 * i) & 0xFF) * alpha;
            }
        }
        if (a <= 0) {
            return 0;
        }
        int out = Math.round(a / stops.size() * 255) << 24;
        for (int i = 0; i < 3; i++) {
            out |= Math.round(Math.min(255, c[i] / a)) << (16 - 8 * i);
        }
        return out;
    }

    private static float maxDefined(float[] at, int end) {
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < end; i++) {
            if (!Float.isNaN(at[i])) {
                max = Math.max(max, at[i]);
            }
        }
        return max;
    }

    /** {@code from} to {@code to} at {@code p} in premultiplied ARGB, as CSS interpolates gradients. */
    private static int premultipliedMix(int from, int to, float p) {
        float fa = (from >>> 24) / 255f;
        float ta = (to >>> 24) / 255f;
        float alpha = fa + (ta - fa) * p;
        if (alpha <= 0) {
            return 0;
        }
        int out = Math.round(alpha * 255) << 24;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = ((from >> shift & 0xFF) * fa * (1 - p) + (to >> shift & 0xFF) * ta * p) / alpha;
            out |= Math.round(Math.max(0, Math.min(255, c))) << shift;
        }
        return out;
    }

    /** A {@code background-size} side in pixels; NaN for {@code auto}. */
    private static float side(Length l, float area) {
        return l.isAuto() ? Float.NaN : l.resolve(area);
    }

    /** A {@code background-position} side: a percentage aligns that point of the image with that point of the box. */
    private static float offset(Length l, float free) {
        return l.resolve(free);
    }

    private void report(Element e, DiagCode code, String message) {
        if (reported.add(e.pos() + ": " + message)) {
            diag.report(code, e.pos(), message);
        }
    }

    /**
     * Draws a text node's fragments as layout placed them, in its parent's color unless a styled span
     * has its own; bold and italic come from the parent, decorations from every element up to and
     * including the box holding the paragraph, and a span adds its own. For a {@code mask}, opaque
     * white whatever the color, without the shadow.
     */
    private void paintText(TextNode t, ComputedStyle parent, float dy, DisplayList out, boolean mask) {
        List<LayoutEngine.TextFragment> fragments = layout.fragments(t);
        if (fragments.isEmpty()) {
            return;
        }
        int flags = 0;
        if (parent.get(CssProperties.FONT_WEIGHT) == Keywords.FontWeight.BOLD) {
            flags |= StyledText.BOLD;
        }
        if (parent.get(CssProperties.FONT_STYLE) == Keywords.FontStyle.ITALIC) {
            flags |= StyledText.ITALIC;
        }
        for (Element a = t.parent(); a != null; a = a.parent()) {
            flags |= switch (a.computed().get(CssProperties.TEXT_DECORATION)) {
                case NONE -> 0;
                case UNDERLINE -> StyledText.UNDERLINE;
                case LINE_THROUGH -> StyledText.STRIKETHROUGH;
            };
            if (a.box().fragments() == null) {
                break; // the box holding the paragraph; inline elements have line fragments
            }
        }
        int base = mask ? OPAQUE_WHITE : parent.get(CssProperties.COLOR);
        TextShadow shadow = parent.get(CssProperties.TEXT_SHADOW);
        int shadowColor = mask ? 0 : shadow.color();
        StyledText styled = t.styled();
        for (int i = 0; i < fragments.size(); i++) {
            LayoutEngine.TextFragment f = fragments.get(i);
            int color = base;
            int own = flags;
            if (f.span() >= 0) {
                StyledText.Span s = styled.spans().get(f.span());
                if (s.color() != 0 && !mask) {
                    color = s.color();
                }
                own |= s.flags();
            }
            if (shadowColor >>> 24 != 0) {
                out.add(new DrawCommand.Text(f.x() + shadow.x(), f.y() - dy + shadow.y(), f.size(), shadowColor, f.text(), own, f.font(),
                        f.spacing()));
            }
            if (color >>> 24 != 0) {
                out.add(new DrawCommand.Text(f.x(), f.y() - dy, f.size(), color, f.text(), own, f.font(), f.spacing()));
            }
        }
    }
}
