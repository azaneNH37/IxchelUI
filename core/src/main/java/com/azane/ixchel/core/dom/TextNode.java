package com.azane.ixchel.core.dom;

import com.azane.ixchel.core.diag.SourcePos;

import java.util.Objects;

/**
 * Character data; styled by its parent element, never by selectors. A bound value may give it
 * {@linkplain #styled() styled spans}, which add to the parent's styles.
 */
public final class TextNode extends Node {
    private String text;
    private StyledText styled;

    public TextNode(String text, SourcePos pos) {
        super(pos);
        this.text = text;
    }

    /** The plain text, also of styled content. */
    public String text() {
        return text;
    }

    /** The styled spans, or {@code null} when the text is plain. */
    public StyledText styled() {
        return styled;
    }

    public void setText(String text) {
        set(text, null);
    }

    public void setStyled(StyledText styled) {
        set(styled.plain(), styled);
    }

    private void set(String text, StyledText styled) {
        if (!text.equals(this.text) || !Objects.equals(styled, this.styled)) {
            this.text = text;
            this.styled = styled;
            if (parent != null) {
                parent.markTreeChanged();
            }
        }
    }

    @Override
    public TextNode copyTemplate() {
        return new TextNode(text, pos());
    }
}
