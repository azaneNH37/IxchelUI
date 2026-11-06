package com.azane.ixchel.core.paint;

import com.azane.ixchel.core.diag.DiagCode;
import com.azane.ixchel.core.diag.Diagnostic.Severity;

/** Styles that cannot be painted. */
public enum PaintCode implements DiagCode {
    BACKGROUND_IMAGE_UNSIZED("paint/background-image-unsized", Severity.ERROR,
            "A background image with background-size: auto cannot be loaded to learn its natural size."),
    BACKGROUND_TOO_MANY_TILES("paint/background-too-many-tiles", Severity.ERROR,
            "A repeated background image would be drawn too many times; use a larger background-size."),
    BLEND_NOT_BOTTOM("paint/blend-not-bottom", Severity.ERROR,
            "A background layer other than the bottom one has background-blend-mode: multiply; only the bottom image layer can multiply."),
    BORDER_IMAGE_UNSIZED("paint/border-image-unsized", Severity.ERROR,
            "A border image cannot be loaded to learn its size, so it cannot be sliced; the border is drawn instead."),
    BORDER_IMAGE_TOO_MANY_TILES("paint/border-image-too-many-tiles", Severity.ERROR,
            "A repeated part of a border image would be drawn too many times; use a larger slice or border-image-width."),
    GRADIENT_TOO_MANY_STOPS("paint/gradient-too-many-stops", Severity.ERROR,
            "A repeating gradient would repeat its stops too many times across its box; make the repeated span longer."),
    MASK_IMAGE_UNSIZED("paint/mask-image-unsized", Severity.ERROR,
            "A mask-image url() cannot be loaded to learn its natural size; the layer masks everything away."),
    MASK_TOO_MANY_TILES("paint/mask-too-many-tiles", Severity.ERROR,
            "A mask image would repeat too many times across the border box; use a larger image."),
    ROUNDED_OVERFLOW_CLIP("paint/rounded-overflow-clip", Severity.WARNING,
            "An element with rounded corners clips its overflow; the clip is a rectangle, so children show past the corners.");

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
