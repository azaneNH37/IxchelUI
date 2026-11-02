package com.azane.ixchel.core.dom;

/**
 * Border box in document coordinates (GUI pixels, origin at the viewport's top-left), and the used
 * border and padding widths inside it: the padding box is the border box minus the borders, the
 * content box that minus the padding as well.
 *
 * <p>An {@code inline} element in an inline formatting context has one rectangle per line it spans
 * instead (its {@linkplain #fragments() fragments}, line-high); its border box is their bounding box
 * and it has no borders or padding.
 */
public final class LayoutBox {
    private static final float[] NO_FRAGMENTS = new float[0];

    private float x;
    private float y;
    private float width;
    private float height;
    private float borderTop;
    private float borderRight;
    private float borderBottom;
    private float borderLeft;
    private float paddingTop;
    private float paddingRight;
    private float paddingBottom;
    private float paddingLeft;
    /** {@code x, y, width, height} per line of an inline box; {@code null} for any other box. */
    private float[] fragments;

    public void set(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        fragments = null;
    }

    /**
     * Makes this an inline box of the given line rectangles ({@code x, y, width, height} each); with
     * none, an empty box at {@code (x, y)}.
     */
    public void setFragments(float[] rects, float x, float y) {
        float x0 = rects.length == 0 ? x : Float.POSITIVE_INFINITY;
        float y0 = rects.length == 0 ? y : Float.POSITIVE_INFINITY;
        float x1 = rects.length == 0 ? x : Float.NEGATIVE_INFINITY;
        float y1 = rects.length == 0 ? y : Float.NEGATIVE_INFINITY;
        for (int i = 0; i < rects.length; i += 4) {
            x0 = Math.min(x0, rects[i]);
            y0 = Math.min(y0, rects[i + 1]);
            x1 = Math.max(x1, rects[i] + rects[i + 2]);
            y1 = Math.max(y1, rects[i + 1] + rects[i + 3]);
        }
        set(x0, y0, x1 - x0, y1 - y0);
        setBorder(0, 0, 0, 0);
        setPadding(0, 0, 0, 0);
        fragments = rects.length == 0 ? NO_FRAGMENTS : rects;
    }

    /** The line rectangles of an inline box, {@code x, y, width, height} each; {@code null} for any other box. */
    public float[] fragments() {
        return fragments;
    }

    /** Whether {@code (x, y)} is in the border box, or for an inline box in one of its line rectangles. */
    public boolean contains(float px, float py) {
        if (fragments == null) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
        for (int i = 0; i < fragments.length; i += 4) {
            if (px >= fragments[i] && py >= fragments[i + 1] && px < fragments[i] + fragments[i + 2]
                    && py < fragments[i + 1] + fragments[i + 3]) {
                return true;
            }
        }
        return false;
    }

    public void setBorder(float top, float right, float bottom, float left) {
        borderTop = top;
        borderRight = right;
        borderBottom = bottom;
        borderLeft = left;
    }

    public void setPadding(float top, float right, float bottom, float left) {
        paddingTop = top;
        paddingRight = right;
        paddingBottom = bottom;
        paddingLeft = left;
    }

    public float contentWidth() {
        return width - borderLeft - paddingLeft - paddingRight - borderRight;
    }

    public float contentHeight() {
        return height - borderTop - paddingTop - paddingBottom - borderBottom;
    }

    public float paddingTop() {
        return paddingTop;
    }

    public float paddingRight() {
        return paddingRight;
    }

    public float paddingBottom() {
        return paddingBottom;
    }

    public float paddingLeft() {
        return paddingLeft;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float borderTop() {
        return borderTop;
    }

    public float borderRight() {
        return borderRight;
    }

    public float borderBottom() {
        return borderBottom;
    }

    public float borderLeft() {
        return borderLeft;
    }
}
