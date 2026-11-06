package com.azane.ixchel.core.layout;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Styles that lay out in a way the layout engine does not support, or with what the host lacks. */
public enum LayoutCode implements DiagCode {
    BLOCK_IN_INLINE("layout/block-in-inline", Severity.ERROR,
            "A block or flex box sits inside a display: inline element; use display: inline-block."),
    ABSOLUTE_IN_INLINE("layout/absolute-in-inline", Severity.ERROR,
            "An absolutely positioned box sits inside a display: inline element, which is not supported."),
    INLINE_BOX_PROPERTY("layout/inline-box-property", Severity.ERROR,
            "A display: inline element sets box properties inline elements do not support; use display: inline-block."),
    UNKNOWN_FONT("layout/unknown-font", Severity.WARNING,
            "font-family names a font the game does not have: no @font-face of that name, and no resource pack font of that id (or the font file fails to load), which only the running game checks; the next one, or the default font, is used.");

    private final String id;
    private final Severity severity;
    private final String summary;

    LayoutCode(String id, Severity severity, String summary) {
        this.id = id;
        this.severity = severity;
        this.summary = summary;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Severity severity() {
        return severity;
    }

    @Override
    public String summary() {
        return summary;
    }
}
