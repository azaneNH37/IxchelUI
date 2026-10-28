package com.azane.ixchel.core.dom;

/**
 * Border box in document coordinates (GUI pixels, origin at the viewport's top-left), and the used
 * border and padding widths inside it: the padding box is the border box minus the borders, the
 * content box that minus the padding as well.
 */
public final class LayoutBox {
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

    public void set(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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
