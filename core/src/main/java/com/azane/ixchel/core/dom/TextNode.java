package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.diag.SourcePos;

/** Character data; styled by its parent element, never by selectors. */
public final class TextNode extends Node {
    private String text;

    public TextNode(String text, SourcePos pos) {
        super(pos);
        this.text = text;
    }

    public String text() {
        return text;
    }

    public void setText(String text) {
        if (!text.equals(this.text)) {
            this.text = text;
            if (parent != null) {
                parent.markTreeChanged();
            }
        }
    }
}
