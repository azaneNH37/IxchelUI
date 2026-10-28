package com.azane.ixchel.core.dom;

/**
 * The editing state of an {@code <input>} ({@link Element#textField()}): its value, the caret and
 * the selection anchor as UTF-16 indices into the value (the selection runs between them), and how
 * far the text is scrolled left to keep the caret in view. Every change is painted by the next frame
 * without restyle or relayout; editing itself is the event dispatcher's default action.
 */
public final class TextField {
    private final Element owner;
    private String value = "";
    private int caret;
    private int anchor;
    private float scrollX;

    TextField(Element owner) {
        this.owner = owner;
    }

    public String value() {
        return value;
    }

    public int caret() {
        return caret;
    }

    public int anchor() {
        return anchor;
    }

    public int selectionStart() {
        return Math.min(caret, anchor);
    }

    public int selectionEnd() {
        return Math.max(caret, anchor);
    }

    public boolean hasSelection() {
        return caret != anchor;
    }

    public String selectedText() {
        return value.substring(selectionStart(), selectionEnd());
    }

    /** Replaces the value and puts the caret at its end, with nothing selected. */
    public void setValue(String value) {
        if (value.equals(this.value)) {
            return;
        }
        this.value = value;
        caret = anchor = value.length();
        owner.requestRepaint();
    }

    /** Selects from {@code anchor} to {@code caret}, both clamped to the value; equal indices place the caret. */
    public void select(int anchor, int caret) {
        int a = clamp(anchor);
        int c = clamp(caret);
        if (a != this.anchor || c != this.caret) {
            this.anchor = a;
            this.caret = c;
            owner.requestRepaint();
        }
    }

    /** Replaces the selection (or inserts at the caret) with {@code text}; the caret ends after it. */
    public void replaceSelection(String text) {
        int start = selectionStart();
        int end = selectionEnd();
        value = value.substring(0, start) + text + value.substring(end);
        caret = anchor = start + text.length();
        owner.requestRepaint();
    }

    /** How far the text is drawn left of the content box, in GUI pixels; kept by the paint pass. */
    public float scrollX() {
        return scrollX;
    }

    public void setScrollX(float scrollX) {
        this.scrollX = scrollX;
    }

    private int clamp(int i) {
        return Math.max(0, Math.min(i, value.length()));
    }
}
