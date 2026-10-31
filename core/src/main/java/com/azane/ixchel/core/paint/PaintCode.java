package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Styles that cannot be painted. */
public enum PaintCode implements DiagCode {
    BACKGROUND_IMAGE_UNSIZED("paint/background-image-unsized", Severity.ERROR,
            "A background image with background-size: auto cannot be loaded to learn its natural size."),
    BACKGROUND_TOO_MANY_TILES("paint/background-too-many-tiles", Severity.ERROR,
            "A repeated background image would be drawn too many times; use a larger background-size.");

    private final String id;
    private final Severity severity;
    private final String summary;

    PaintCode(String id, Severity severity, String summary) {
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
