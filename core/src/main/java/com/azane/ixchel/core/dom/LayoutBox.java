package com.azane.ixchel.core.dom;

/** Border box in document coordinates (GUI pixels, origin at the viewport's top-left). */
public final class LayoutBox {
    private float x;
    private float y;
    private float width;
    private float height;

    public void set(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
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
}
